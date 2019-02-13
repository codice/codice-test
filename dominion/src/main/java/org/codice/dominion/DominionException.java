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

import java.util.List;
import org.junit.runners.model.InitializationError;

/** General exception thrown by the dominion framework. */
public class DominionException extends RuntimeException {
  /**
   * Creates a new dominion exception with the specified message.
   *
   * @param message the error message
   */
  public DominionException(String message) {
    super(message);
  }

  /**
   * Creates a new dominion exception with the specified message and cause.
   *
   * @param message the error message
   * @param cause the cause for this exception
   */
  public DominionException(String message, Throwable cause) {
    super(message, cause);
    addInitializationErrorCausesAsSuppressed(cause);
  }

  /**
   * Creates a new dominion exception with the specified cause.
   *
   * @param cause the cause for this exception
   */
  public DominionException(Throwable cause) {
    super(cause);
    addInitializationErrorCausesAsSuppressed(cause);
  }

  /**
   * This method makes it easy to troubleshoot JUnit initialization error as by default, they do not
   * get printed along with the stack trace.
   *
   * @param cause the cause to inspect and update
   */
  private static void addInitializationErrorCausesAsSuppressed(Throwable cause) {
    if (cause instanceof InitializationError) {
      final List<Throwable> causes = ((InitializationError) cause).getCauses();

      if (causes != null) {
        causes.forEach(cause::addSuppressed);
      }
    }
  }
}
