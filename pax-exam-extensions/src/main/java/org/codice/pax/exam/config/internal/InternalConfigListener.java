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
package org.codice.pax.exam.config.internal;

import org.codice.pax.exam.config.ConfigTimeoutException;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;

/**
 * An internal configuration listener capable of waiting for any configuration events on a given
 * config object.
 */
public class InternalConfigListener implements ConfigurationListener {
  private final String pid;

  private boolean received = false;

  /**
   * Creates a config listener which will be listening for configuration events for the specified
   * configuration object.
   *
   * @param pid the persistent id of the config object for which to listen for events
   */
  public InternalConfigListener(String pid) {
    this.pid = pid;
  }

  @Override
  public void configurationEvent(ConfigurationEvent event) {
    if (event.getPid().equals(pid)) {
      synchronized (this) {
        this.received = true;
        notifyAll();
      }
    }
  }

  /**
   * Waits for the specified amount of time for this listener to be notified of any configuration
   * events for the corresponding configuration object.
   *
   * @param timeout the maximum amount of time in milliseconds to wait for a config change on the
   *     corresponding config object
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ConfigTimeoutException if we timed out before receiving a config change event for the
   *     corresponding config object
   */
  public void waitForNotification(long timeout) throws InterruptedException {
    synchronized (this) {
      if (!received) {
        if (timeout > 0L) {
          wait(timeout);
        }
        if (!received) {
          throw new ConfigTimeoutException("timed out waiting for a config event for: " + pid);
        }
      }
    }
  }
}
