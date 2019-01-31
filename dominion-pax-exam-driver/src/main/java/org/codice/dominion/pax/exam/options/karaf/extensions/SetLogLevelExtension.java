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

import org.apache.commons.lang3.StringUtils;
import org.codice.dominion.options.karaf.KarafOptions.SetLogLevel;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;

/** Extension point for the {@link SetLogLevel} option annotation. */
public class SetLogLevelExtension implements Extension<SetLogLevel> {
  @Override
  public Option[] options(
      SetLogLevel annotation, Class<?> testClass, ResourceLoader resourceLoader) {
    final String level = annotation.level();

    if (StringUtils.isEmpty(level)) {
      return new Option[0];
    }
    return new Option[] {KarafDistributionOption.logLevel(Enum.valueOf(LogLevel.class, level))};
  }
}
