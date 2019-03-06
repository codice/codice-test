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

/** Pax Exam option for executing a shell command after the container has started. */
// should be enhanced to support matching a string from the ssh output to detect success or failure
public class KarafSshCommandOption implements Option {
  /** Ssh command to execute. */
  private final String command;

  /** Timeout in milliseconds. */
  private final long timeout;

  /**
   * @param command the command to be executed
   * @param timeout the maximum amount of time in milliseconds to wait for the command to complete
   */
  public KarafSshCommandOption(String command, long timeout) {
    this.command = command;
    this.timeout = timeout;
  }

  /**
   * Gets the command to execute.
   *
   * @return the command to execute
   */
  public String getCommand() {
    return command;
  }

  /**
   * Gets the maximum amount of time in milliseconds to wait for the command to complete.
   *
   * @return the maximum amount of time to wait
   */
  public long getTimeout() {
    return timeout;
  }

  @Override
  public String toString() {
    return KarafSshCommandOption.class.getSimpleName()
        + "{command="
        + command
        + ", timeout="
        + timeout
        + "}";
  }
}
