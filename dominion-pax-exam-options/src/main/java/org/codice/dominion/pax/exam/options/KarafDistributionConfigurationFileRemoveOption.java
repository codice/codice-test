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
package org.codice.dominion.pax.exam.options;

/**
 * This option allows to remove configurations in each configuration file based on the <code>
 * {karaf.home}</code> location. The key identifies a property to remove completely from the
 * configuration file.
 */
public class KarafDistributionConfigurationFileRemoveOption
    extends KarafDistributionConfigurationFilePostOption {
  private final String key;

  public KarafDistributionConfigurationFileRemoveOption(String configurationFilePath, String key) {
    super(configurationFilePath);
    this.key = key;
  }

  public String getKey() {
    return key;
  }
}
