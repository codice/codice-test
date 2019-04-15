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
package org.codice.dominion.pax.exam.options.extensions;

import org.apache.commons.io.FilenameUtils;
import org.codice.dominion.options.Options.OverrideAndPropagateMavenSettings;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.url.mvn.ServiceConstants;

/** Extension point for the {@link OverrideAndPropagateMavenSettings} option annotation. */
public class OverrideAndPropagateMavenSettingsExtension
    implements Extension<OverrideAndPropagateMavenSettings> {
  @Override
  public Option[] options(
      OverrideAndPropagateMavenSettings annotation,
      PaxExamInterpolator interpolator,
      ResourceLoader resourceLoader) {
    final String value = annotation.value();

    if (value.isEmpty()) {
      return new Option[0];
    }
    return new Option[] {
      KarafDistributionOption.editConfigurationFilePut(
          "etc/" + ServiceConstants.PID + ".cfg",
          ServiceConstants.PID + '.' + ServiceConstants.PROPERTY_SETTINGS_FILE,
          FilenameUtils.separatorsToSystem(value))
    };
  }
}
