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

import java.util.Dictionary;
import java.util.Hashtable;
import javax.annotation.Nullable;
import org.osgi.service.cm.Configuration;

/** This class creates a snapshot of a given configuration object. */
public class ConfigurationSnapshot {
  private static final Dictionary<String, Object> EMPTY_PROPERTIES = new Hashtable<>(1);

  private final String pid;
  @Nullable private final String factoryPid;
  @Nullable private final String bundleLocation;
  private final Dictionary<String, Object> properties;

  /**
   * Creates a snapshot of the given configuration object.
   *
   * @param cfg the configuration object to snapshot
   */
  public ConfigurationSnapshot(Configuration cfg) {
    this.pid = cfg.getPid();
    this.factoryPid = cfg.getFactoryPid();
    this.bundleLocation = cfg.getBundleLocation();
    this.properties = cfg.getProperties(); // will get a private copy
  }

  /**
   * Get the PID for this configuration object.
   *
   * @return the PID for this configuration object
   */
  public String getPid() {
    return pid;
  }

  /**
   * For a factory configuration return the PID of the corresponding managed service factory.
   *
   * @return the PID for the corresponding managed service factory or <code>null</code> if not a
   *     factory configuration
   */
  @Nullable
  public String getFactoryPid() {
    return factoryPid;
  }

  /**
   * Get the bundle location.
   *
   * @return the bundle location to which this configuration is bound, or <code>null</code> if it is
   *     not yet bound to a bundle location
   */
  @Nullable
  public String getBundleLocation() {
    return bundleLocation;
  }

  /**
   * Gets the properties of this configuration object.
   *
   * @return the properties of this configuration object
   */
  public Dictionary<String, Object> getProperties() {
    return properties;
  }
}
