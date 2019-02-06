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
package org.codice.dominion.pax.exam.internal;

import java.io.File;

/** Factory for creating classes capable of handling Karaf configuration files. */
public class DominionKarafConfigurationFileFactory {
  public static DominionKarafConfigurationFile create(final File karafHome, final String location) {
    if ((location != null) && location.endsWith(".config")) {
      return new DominionKarafConfigFile(karafHome, location);
    } else {
      return new DominionKarafCfgFile(karafHome, location);
    }
  }

  private DominionKarafConfigurationFileFactory() {}
}
