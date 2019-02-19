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

import org.codice.dominion.options.karaf.KarafOptions;

/** PaxExam option for removing a user. */
public class KarafUserPropertiesFileUserRetractOption
    extends KarafDistributionConfigurationFileRemoveOption {

  /**
   * Creates a new option.
   *
   * @param userId the user id to be removed
   */
  public KarafUserPropertiesFileUserRetractOption(String userId) {
    super(KarafOptions.USER_PROPERTIES, userId);
  }

  /**
   * Gets the unique user id to remove.
   *
   * @return the unique user id
   */
  public String getUserId() {
    return getKey();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{userId=" + getUserId() + "}";
  }
}
