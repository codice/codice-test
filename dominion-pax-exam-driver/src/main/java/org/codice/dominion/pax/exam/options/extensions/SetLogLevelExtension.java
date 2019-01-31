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

import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.codice.dominion.options.Options.SetLogLevel;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;

/** Extension point for the {@link SetLogLevel} option annotation. */
public class SetLogLevelExtension implements Extension<SetLogLevel> {
  private static final String LOGGER_PREFIX = "log4j2.logger.";
  private static final String LOGGER_FILE = "etc/org.ops4j.pax.logging.cfg";

  @Override
  public Option[] options(
      SetLogLevel annotation, Class<?> testClass, ResourceLoader resourceLoader) {
    return SetLogLevelExtension.options(annotation.name(), annotation.level())
        .toArray(Option[]::new);
  }

  static Stream<Option> options(String name, String level) {
    if (StringUtils.isEmpty(level)) {
      return Stream.empty();
    }
    final String loggerKey = name.replace('.', '_').toLowerCase();

    return Stream.of(
        KarafDistributionOption.editConfigurationFilePut(
            SetLogLevelExtension.LOGGER_FILE,
            String.format("%s%s.name", SetLogLevelExtension.LOGGER_PREFIX, loggerKey),
            name),
        KarafDistributionOption.editConfigurationFilePut(
            SetLogLevelExtension.LOGGER_FILE,
            String.format("%s%s.level", SetLogLevelExtension.LOGGER_PREFIX, loggerKey),
            level));
  }
}
