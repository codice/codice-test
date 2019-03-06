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

import org.ops4j.pax.exam.Option;

/**
 * Abstract class for file options that are processed by Dominion after all other standard PaxExam
 * options (including its own file options) have been processed and before the container is started.
 */
// cannot extend KarafDistributionConfigurationFileOption as it will be handle as an extend option
// by PaxExam
public abstract class KarafDistributionConfigurationFilePostOption implements Option {
  private final String configurationFilePath;

  public KarafDistributionConfigurationFilePostOption(String configurationFilePath) {
    this.configurationFilePath = configurationFilePath;
  }

  public String getConfigurationFilePath() {
    return configurationFilePath;
  }
}
