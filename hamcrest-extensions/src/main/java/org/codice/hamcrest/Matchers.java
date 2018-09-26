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
package org.codice.hamcrest;

import org.codice.hamcrest.stack.StackContainsDoPrivilegedCalls;

/** Aggregates all Codice matchers. */
public class Matchers {
  private Matchers() {}

  /**
   * Factory method that returns an instance of {@link StackContainsDoPrivilegedCalls} that expects
   * only one {@code AccessController.doPrivileged()} call on the stack.
   *
   * @return {@link StackContainsDoPrivilegedCalls} custom Hamcrest matcher
   */
  public static StackContainsDoPrivilegedCalls stackContainsDoPrivilegedCall() {
    return StackContainsDoPrivilegedCalls.stackContainsDoPrivilegedCall();
  }

  /**
   * Factory method that returns an instance of {@link StackContainsDoPrivilegedCalls} that expects
   * a specific number of {@code AccessController.doPrivileged()} calls on the stack.
   *
   * @return {@link StackContainsDoPrivilegedCalls} custom Hamcrest matcher
   */
  public static StackContainsDoPrivilegedCalls stackContainsDoPrivilegedCalls(int times) {
    return StackContainsDoPrivilegedCalls.stackContainsDoPrivilegedCalls(times);
  }
}
