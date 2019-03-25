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

import org.codice.dominion.options.karaf.KarafOptions.InstallFeature;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.pax.exam.options.PaxExamUtilities;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;

/** Extension point for the {@link InstallFeature} option annotation. */
public class FeatureExtension implements Extension<InstallFeature> {
  @Override
  public Option[] options(
      InstallFeature annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader) {
    final MavenUrl mavenUrl = annotation.repository();
    final String url = annotation.repositoryUrl();
    final boolean mavenUrlIsDefined =
        org.codice.dominion.options.Utilities.isDefined(mavenUrl.groupId());
    final boolean urlIsDefined = org.codice.dominion.options.Utilities.isDefined(url);
    final String[] names = annotation.names();

    if (!mavenUrlIsDefined && !urlIsDefined) {
      return new Option[] {
        KarafDistributionOption.features(
            PaxExamUtilities.getProjectReference(annotation, resourceLoader)
                .type("xml")
                .classifier("features"),
            names)
      };
    } else if (mavenUrlIsDefined) {
      if (urlIsDefined) {
        throw new IllegalArgumentException(
            "specify only one of Feature.repository() or Feature.repositoryUrl() in "
                + annotation
                + " for "
                + resourceLoader.getLocationClass().getName());
      }
      return new Option[] {
        KarafDistributionOption.features(
            PaxExamUtilities.toReference(mavenUrl, annotation, resourceLoader), names)
      };
    }
    return new Option[] {KarafDistributionOption.features(url, names)};
  }
}
