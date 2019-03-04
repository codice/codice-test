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

import org.codice.dominion.options.Options.MavenUrl;
import org.codice.dominion.options.karaf.KarafOptions.Feature;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.pax.exam.options.Utilities;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;

/** Extension point for the {@link Feature} option annotation. */
public class FeatureExtension implements Extension<Feature> {
  @Override
  public Option[] options(
      Feature annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader) {
    final MavenUrl mavenUrl = annotation.repository();
    final String url = annotation.repositoryUrl();
    final boolean groupIsDefined =
        org.codice.dominion.options.Utilities.isDefined(mavenUrl.groupId());
    final boolean urlIsDefined = org.codice.dominion.options.Utilities.isDefined(url);
    final String[] names = annotation.names();

    if (!groupIsDefined && !urlIsDefined) {
      return new Option[] {
        KarafDistributionOption.features(
            Utilities.getProjectReference(annotation, resourceLoader)
                .type("xml")
                .classifier("features"),
            names)
      };
    } else if (groupIsDefined) {
      if (urlIsDefined) {
        throw new IllegalArgumentException(
            "specify only one of Feature.repository() or Feature.repositoryUrl() in "
                + annotation
                + " for "
                + resourceLoader.getLocationClass().getName());
      }
      return new Option[] {
        KarafDistributionOption.features(
            Utilities.toReference(annotation, mavenUrl, resourceLoader), names)
      };
    }
    return new Option[] {KarafDistributionOption.features(url, names)};
  }
}
