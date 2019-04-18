/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.dominion.pax.exam.options.karaf.extensions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.features.internal.model.Feature;
import org.apache.karaf.features.internal.service.RepositoryImpl;
import org.codice.dominion.options.karaf.KarafOptions.InstallFeature;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.KarafSshCommandOption;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.pax.exam.options.PaxExamUtilities;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileExtendOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.configs.FeaturesCfg;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.options.UrlReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Extension point for the {@link InstallFeature} option annotation. */
public class InstallFeatureExtension implements Extension<InstallFeature> {
  private static final Logger LOGGER = LoggerFactory.getLogger(InstallFeatureExtension.class);

  @Override
  public Option[] options(
      InstallFeature annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader)
      throws URISyntaxException {
    final MavenUrl mavenUrl = annotation.repository();
    final String url = annotation.repositoryUrl();
    final boolean mavenUrlIsDefined =
        org.codice.dominion.options.Utilities.isDefined(mavenUrl.groupId());
    final boolean urlIsDefined = org.codice.dominion.options.Utilities.isDefined(url);
    final UrlReference repoUrl;

    if (!mavenUrlIsDefined && !urlIsDefined) {
      repoUrl =
          PaxExamUtilities.getProjectReference(annotation, resourceLoader)
              .type("xml")
              .classifier("features");
    } else if (mavenUrlIsDefined) {
      if (urlIsDefined) {
        throw new IllegalArgumentException(
            "specify only one of InstallFeature.repository() or InstallFeature.repositoryUrl() in "
                + annotation
                + " for "
                + resourceLoader.getLocationClass().getName());
      }
      repoUrl = PaxExamUtilities.toReference(mavenUrl, annotation, resourceLoader);
    } else {
      repoUrl = new RawUrlReference(url);
    }
    final String[] names =
        InstallFeatureExtension.getFeatureNamesFromRepoIfNotSpecified(repoUrl, annotation.name());

    if (annotation.boot()) {
      return new Option[] {KarafDistributionOption.features(repoUrl, names)};
    }
    long timeout = annotation.timeout();

    if (timeout != -1L) {
      timeout = annotation.units().toMillis(timeout);
    } else { // default to 3 minutes per features
      timeout = TimeUnit.MINUTES.toMillis(3L) * names.length;
    }
    return new Option[] {
      new KarafDistributionConfigurationFileExtendOption(
          FeaturesCfg.REPOSITORIES, repoUrl.getURL()),
      new KarafSshCommandOption(
          "feature:install --no-auto-refresh " + StringUtils.join(names, " "), timeout) {
        @Override
        public String getCommand() {
          if (names.length == 0) {
            LOGGER.info("Installing '{}' feature", names[0]);
          } else {
            LOGGER.info("Installing features: {}", StringUtils.join(names, ", "));
          }
          return super.getCommand();
        }
      }
    };
  }

  private static String[] getFeatureNamesFromRepoIfNotSpecified(UrlReference url, String[] names)
      throws URISyntaxException {
    if (names.length > 0) {
      return names;
    } // load the feature file and install all features listed
    return new RepositoryImpl(new URI(url.getURL()), true)
        .getFeaturesInternal()
        .getFeature()
        .stream()
        .map(Feature::getName)
        .toArray(String[]::new);
  }
}
