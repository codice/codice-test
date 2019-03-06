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
import java.util.Properties;
import java.util.StringTokenizer;
import javax.annotation.Nullable;
import org.ops4j.pax.exam.karaf.container.internal.JoinUtil;

/** Class that supports Karaf <code>.cfg</code> configuration files. */
public class DominionKarafCfgFile extends DominionKarafConfigurationFile {
  private final Properties properties = new Properties();

  public DominionKarafCfgFile(File karafHome, String location) {
    super(karafHome, location);
  }

  @Override
  public void store() throws IOException {
    try (final FileOutputStream os = new FileOutputStream(file)) {
      properties.store(os, "Modified by Dominion");
    }
  }

  @Override
  public void load() throws IOException {
    if (!file.exists()) {
      return;
    }
    try (final FileInputStream is = new FileInputStream(file)) {
      properties.load(is);
    }
  }

  @Override
  public void put(String key, Object value) {
    properties.put(key, value);
  }

  @Override
  public boolean remove(String key) {
    return properties.remove(key) != null;
  }

  @Override
  public void extend(String key, Object value) {
    final String current = properties.getProperty(key);

    if (current == null) {
      properties.put(key, value);
    } else {
      properties.put(key, JoinUtil.join(current, (String) value));
    }
  }

  @Override
  public boolean retract(String key, Object value) {
    final String current = properties.getProperty(key);

    if (current == null) {
      return false;
    }
    // supports boot features too in org.apache.karaf.features.cfg
    final StringTokenizer tokenizer = new StringTokenizer(current, " \t\r\n,()", true);
    final StringBuilder sb = new StringBuilder(current.length());
    final String retract = value.toString();
    boolean retracted = false;

    while (tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();

      if (token.equals(retract)) {
        retracted = true;
      } else if (!token.matches("[ \t\r\n]+")) { // ignore spaces
        sb.append(token);
      }
    }
    if (retracted) {
      put(key, DominionKarafCfgFile.cleanup(sb.toString()));
      return true;
    }
    return false;
  }

  @Nullable
  @Override
  public Object get(String key) {
    return properties.getProperty(key);
  }

  /**
   * Cleans up a string by removing un-needed commas and parenthesis.
   *
   * @param s the string to be cleaned
   * @return the corresponding cleaned string
   */
  private static String cleanup(String s) {
    String cleaned = s;
    String previous;

    do {
      previous = cleaned;
      cleaned =
          previous
              // removes starting commas
              .replaceAll("^,+", "")
              // removes ending commas
              .replaceAll(",+$", "")
              // removes consecutive commas
              .replaceAll(",,+", ",")
              // removes empty parenthesis and parenthesis with only commas
              .replaceAll("\\(,*\\)", "")
              // removes related double opening and closing parenthesis
              .replaceAll("\\(\\(([^\\(,]*?)\\)\\)", "$1")
              // removes commas after opening parenthesis
              .replaceAll("\\(,+", "(")
              // removes commas before closing parenthesis
              .replaceAll(",+\\)", ")");
    } while (!previous.equals(cleaned));
    return cleaned;
  }
}
