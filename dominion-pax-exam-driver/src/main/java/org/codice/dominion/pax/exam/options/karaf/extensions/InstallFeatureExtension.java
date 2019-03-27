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
import org.apache.karaf.features.internal.model.Feature;
import org.apache.karaf.features.internal.service.RepositoryImpl;
import org.codice.dominion.options.karaf.KarafOptions.InstallFeature;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.pax.exam.options.PaxExamUtilities;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.options.UrlReference;

/** Extension point for the {@link InstallFeature} option annotation. */
public class InstallFeatureExtension implements Extension<InstallFeature> {
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
    return new Option[] {
      KarafDistributionOption.features(
          repoUrl,
          InstallFeatureExtension.getFeatureNamesFromRepoIfNotSpecified(repoUrl, annotation.name()))
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
