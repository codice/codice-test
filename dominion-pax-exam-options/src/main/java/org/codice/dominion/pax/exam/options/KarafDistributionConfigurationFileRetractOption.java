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

import org.ops4j.pax.exam.karaf.options.ConfigurationPointer;

/**
 * This option allows to retract configurations in each configuration file based on the <code>
 * {karaf.home}</code> location. The value identifies an entry to retract from the current value
 * (e.g. if it was a=a,b,c and a=b, it then becomes a=a,c) instead of replacing or extending it. If
 * there is no current value or if it is not currently defined as part of the configuration then
 * nothing happen.
 *
 * <p>If you would like to extend or replace functionality please use the {@link
 * org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileExtendOption} or {@link
 * org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileOption} instead.
 */
// cannot extend KarafDistributionConfigurationFileOption as it will be handle as an extend option
// by PaxExam
public class KarafDistributionConfigurationFileRetractOption
    extends KarafDistributionConfigurationFilePostOption {
  private final Object value;

  public KarafDistributionConfigurationFileRetractOption(
      ConfigurationPointer pointer, Object value) {
    super(pointer);
    this.value = value;
  }

  public KarafDistributionConfigurationFileRetractOption(
      String configurationFilePath, String key, Object value) {
    super(configurationFilePath, key);
    this.value = value;
  }

  public Object getValue() {
    return value;
  }
}
