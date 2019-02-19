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
import org.apache.commons.io.FileUtils;

/** Base class for Karaf configuration file support. */
public abstract class DominionKarafConfigurationFile {
  protected final File file;

  public DominionKarafConfigurationFile(final File karafHome, final String location) {
    if (location.startsWith("/")) {
      this.file = new File(karafHome + location);
    } else {
      this.file = new File(karafHome + "/" + location);
    }
  }

  /**
   * Checks if the file exist on disk.
   *
   * @return <code>true</code> if the file exist on disk; <code>false</code> otherwise
   */
  public boolean exists() {
    return file.exists();
  }

  /**
   * Replaces the file on disk with the one specified.
   *
   * @param source the source file to copy to this config's location
   */
  public void replace(File source) {
    try {
      FileUtils.copyFile(source, file);
    } catch (IOException e) {
      throw new IllegalStateException("Error occurred while replacing file " + file, e);
    }
  }

  /**
   * Stores this representation of a config file back to disk in the appropriate format.
   *
   * @throws IOException if an I/O error occurs
   */
  public abstract void store() throws IOException;

  /**
   * Loads the corresponding config file from disk in this representation.
   *
   * @throws IOException if an I/O error occurs
   */
  public abstract void load() throws IOException;

  /**
   * Puts a new value for the specified key.
   *
   * @param key the key to have its value set
   * @param value the new value for the corresponding key
   */
  public abstract void put(String key, Object value);

  /**
   * Removes the specified key.
   *
   * @param key the key to remove
   * @return if a change resulted for this configuration file
   */
  public abstract boolean remove(String key);

  /**
   * Extends the specified key's value with the specified one.
   *
   * @param key the key to have its value extended
   * @param value the new value to add to the corresponding key
   */
  public abstract void extend(String key, Object value);

  /**
   * Retracts a given value from the specified key.
   *
   * @param key the key to have one of its value retracted
   * @param value the value to remove from the corresponding key
   * @return if a change resulted for this configuration file
   */
  public abstract boolean retract(String key, Object value);

  /**
   * Gets the current value associated with the specified key.
   *
   * @param key the key for which to retrieved the current value
   * @return the corresponding value or <code>null</code> if none defined
   */
  @Nullable
  public abstract Object get(String key);
}
