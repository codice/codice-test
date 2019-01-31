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
package org.codice.dominion;

/** General exception thrown by the dominion framework when it fails to initialize. */
public class DominionInitializationException extends DominionException {
  /**
   * Creates a new dominion initialization exception with the specified message.
   *
   * @param message the error message
   */
  public DominionInitializationException(String message) {
    super(message);
  }

  /**
   * Creates a new dominion initialization exception with the specified message and cause.
   *
   * @param message the error message
   * @param cause the cause for this exception
   */
  public DominionInitializationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new dominion initialization exception with the cause.
   *
   * @param cause the cause for this exception
   */
  public DominionInitializationException(Throwable cause) {
    super(cause);
  }
}
