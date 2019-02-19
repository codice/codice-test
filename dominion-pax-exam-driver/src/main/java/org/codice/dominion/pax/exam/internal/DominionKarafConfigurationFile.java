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
import java.io.IOException;
import javax.annotation.Nullable;

/** Interface for Karaf configuration file support. */
public interface DominionKarafConfigurationFile {
  /**
   * Checks if the file exist on disk.
   *
   * @return <code>true</code> if the file exist on disk; <code>false</code> otherwise
   */
  boolean exists();

  /**
   * Replaces the file on disk with the one specified.
   *
   * @param source the source file to copy to this config's location
   */
  void replace(File source);

  /**
   * Stores this representation of a config file back to disk in the appropriate format.
   *
   * @throws IOException if an I/O error occurs
   */
  void store() throws IOException;

  /**
   * Loads the corresponding config file from disk in this representation.
   *
   * @throws IOException if an I/O error occurs
   */
  void load() throws IOException;

  /**
   * Puts a new value for the specified key.
   *
   * @param key the key to have its value set
   * @param value the new value for the corresponding key
   */
  void put(String key, Object value);

  /**
   * Extends the specified key's value with the specified one.
   *
   * @param key the key to have its value extended
   * @param value the new value to add to the corresponding key
   */
  void extend(String key, Object value);

  /**
   * Retracts a given value from the specified key.
   *
   * @param key the key to have one of its value retracted
   * @param value the value to remove from the corresponding key
   * @return if a change resulted for this configuration file
   */
  boolean retract(String key, Object value);

  /**
   * Gets the current value associated with the specified key.
   *
   * @param key the key for which to retrieved the current value
   * @return the corresponding value or <code>null</code> if none defined
   */
  @Nullable
  Object get(String key);
}
