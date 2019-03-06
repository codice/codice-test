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
package org.codice.pax.exam.config;

/** Utility used for waiting for configuration stabilization. */
@FunctionalInterface
public interface ConfigStabilizer {
  /**
   * Waits a maximum of time for the configuration admin to stabilize. This methods attempts to
   * ensure that the configuration admin service is done dispatching all configuration events.
   *
   * @param timeout the maximum amount of time in milliseconds to wait for the config admin to
   *     stabilize
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws org.codice.pax.exam.config.ConfigTimeoutException if we timed out before the config
   *     admin was able to stabilize
   */
  public void stabilize(long timeout) throws InterruptedException;
}
