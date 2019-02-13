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

import org.apache.commons.lang3.StringUtils;
import org.codice.dominion.options.Options.SetRootLogLevel;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;

/** Extension point for the {@link SetRootLogLevel} option annotation. */
public class SetRootLogLevelExtension implements Extension<SetRootLogLevel> {
  private static final String ROOT_LOGGER_KEY = "log4j2.rootLogger.level";
  private static final String LOGGER_FILE = "etc/org.ops4j.pax.logging.cfg";

  @Override
  public Option[] options(
      SetRootLogLevel annotation, Class<?> testClass, ResourceLoader resourceLoader) {
    final String level = annotation.value();

    if (StringUtils.isEmpty(level)) {
      return new Option[0];
    }
    return new Option[] {
      KarafDistributionOption.editConfigurationFilePut(
          SetRootLogLevelExtension.LOGGER_FILE, SetRootLogLevelExtension.ROOT_LOGGER_KEY, level)
    };
  }
}
