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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import javax.annotation.Nullable;
import org.apache.felix.cm.file.ConfigurationHandler;

public class DominionKarafConfigFile extends DominionKarafConfigurationFile {
  private volatile Dictionary configuration = new Hashtable();

  public DominionKarafConfigFile(File karafHome, String location) {
    super(karafHome, location);
  }

  @Override
  public void store() throws IOException {
    try (final FileOutputStream os = new FileOutputStream(file)) {
      ConfigurationHandler.write(os, configuration);
    }
  }

  @Override
  public void extend(String key, Object value) {
    final Object current = configuration.get(key);

    if (current == null) {
      final Object array = Array.newInstance(value.getClass(), 1);

      Array.set(array, 0, value);
      configuration.put(key, array);
    } else {
      final Class clazz = current.getClass();

      if (clazz.isArray()) {
        final int length = Array.getLength(current);
        final Object array = Array.newInstance(clazz.getComponentType(), length + 1);

        System.arraycopy(current, 0, array, 0, length);
        Array.set(array, length, value);
        configuration.put(key, array);
      } else if (current instanceof Collection) {
        ((Collection) current).add(value);
      } else {
        throw new IllegalArgumentException(String.format("Cannot extend %s by %s.", key, value));
      }
    }
  }

  @Override
  public void load() throws IOException {
    if (!file.exists()) {
      return;
    }
    try (final FileInputStream is = new FileInputStream(file)) {
      this.configuration = ConfigurationHandler.read(is);
    }
  }

  @Override
  public void put(String key, Object value) {
    configuration.put(key, value);
  }

  @Override
  public boolean remove(String key) {
    return configuration.remove(key) != null;
  }

  @Override
  public boolean retract(String key, Object value) {
    Object current = get(key);

    if (key == null) {
      return false;
    }
    final Class<?> clazz = current.getClass();
    boolean store = false;

    if (clazz.isArray()) {
      for (int i = 0; i < Array.getLength(current); i++) {
        if (value.equals(Array.get(current, i))) {
          current = DominionKarafConfigFile.retract(current, i);
          store = true;
        }
      }
    } else if (current instanceof Collection) {
      final Collection<?> collection = (Collection<?>) current;

      while (collection.remove(value)) { // nothing else to do
        store = true;
      }
    } else {
      throw new IllegalArgumentException(String.format("Cannot retract %s from %s.", value, key));
    }
    if (store) {
      put(key, current);
      return true;
    }
    return false;
  }

  @Nullable
  @Override
  public Object get(final String key) {
    return configuration.get(key);
  }

  private static Object retract(Object array, int index) {
    final int length = Array.getLength(array);
    final Object retracted = Array.newInstance(array.getClass().getComponentType(), length - 1);

    System.arraycopy(array, 0, retracted, 0, index);
    final int rest = length - index;

    if (rest > 0) {
      System.arraycopy(array, index + 1, retracted, index, rest);
    }
    return retracted;
  }
}
