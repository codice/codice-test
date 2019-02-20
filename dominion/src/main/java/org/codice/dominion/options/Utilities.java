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
package org.codice.dominion.options;

import java.util.function.Function;

/** Utility methods for defining extensions. */
public class Utilities {
  /**
   * Checks if an annotation attribute value is defined.
   *
   * @param value the value to be checked
   * @return <code>true</code> if the value is defined; <code>false</code> if it is the default
   *     value {@link Options#NOT_DEFINED}
   */
  public static boolean isDefined(String value) {
    return !Options.NOT_DEFINED.equals(value);
  }

  /**
   * Checks if an annotation attribute value is defined.
   *
   * @param value the value to be checked
   * @return <code>true</code> if the value is defined; <code>false</code> if it is an array where
   *     the first element is not defined as {@link Options#NOT_DEFINED}
   */
  public static boolean isDefined(String[] value) {
    return (value.length > 0) && Utilities.isDefined(value[0]);
  }

  /**
   * Resolves the specified value.
   *
   * @param value the value to be resolved
   * @param dflt the default value to return if <code>value</code> is not defined
   * @return <code>value</code> if it is defined; otherwise <code>dflt</code>
   */
  public static String resolve(String value, String dflt) {
    return Utilities.isDefined(value) ? value : dflt;
  }

  /**
   * Resolves the specified value by applying the given function to it if it is defined.
   *
   * @param value the value to be resolved
   * @param dflt the default value to return if <code>value</code> is not defined
   * @param function the function to apply too <code>value</code> if it is defined
   * @return the result from the function applied to <code>value</code> if it is defined; otherwise
   *     <code>dflt</code>
   */
  public static <T> T applyIfDefined(String value, T dflt, Function<String, ?> function) {
    return Utilities.isDefined(value) ? (T) function.apply(value) : dflt;
  }

  /**
   * Resolves the specified value by applying the given function to it if it is defined.
   *
   * @param value the value to be resolved
   * @param dflt the default value to return if <code>value</code> is not defined
   * @param function the function to apply too <code>value</code> if it is defined
   * @return the result from the function applied to <code>value</code> if it is defined; otherwise
   *     <code>dflt</code>
   */
  public static <T> T applyIfDefined(String[] value, T dflt, Function<String[], ?> function) {
    return Utilities.isDefined(value) ? (T) function.apply(value) : dflt;
  }
}
