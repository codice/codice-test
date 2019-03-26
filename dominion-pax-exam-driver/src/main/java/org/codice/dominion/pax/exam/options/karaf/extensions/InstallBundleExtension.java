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

import org.codice.dominion.options.karaf.KarafOptions.InstallBundle;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.pax.exam.options.PaxExamUtilities;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link InstallBundle} option annotation. */
public class InstallBundleExtension implements Extension<InstallBundle> {
  @Override
  public Option[] options(
      InstallBundle annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader) {
    final MavenUrl mavenUrl = annotation.bundle();
    final String url = annotation.bundleUrl();
    final boolean mavenUrlIsDefined =
        org.codice.dominion.options.Utilities.isDefined(mavenUrl.groupId());
    final boolean urlIsDefined = org.codice.dominion.options.Utilities.isDefined(url);

    if (!mavenUrlIsDefined && !urlIsDefined) {
      return new Option[] {
        CoreOptions.mavenBundle(PaxExamUtilities.getProjectReference(annotation, resourceLoader))
      };
    } else if (mavenUrlIsDefined) {
      if (urlIsDefined) {
        throw new IllegalArgumentException(
            "specify only one of InstallBundle.bundle() or InstallBundle.bundleUrl() in "
                + annotation
                + " for "
                + resourceLoader.getLocationClass().getName());
      }
      return new Option[] {
        CoreOptions.mavenBundle(PaxExamUtilities.toReference(mavenUrl, annotation, resourceLoader))
      };
    }
    return new Option[] {CoreOptions.provision(url)};
  }
}
