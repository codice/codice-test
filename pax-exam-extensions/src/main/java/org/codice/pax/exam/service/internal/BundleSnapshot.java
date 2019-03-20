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
package org.codice.pax.exam.service.internal;

import com.google.common.annotations.VisibleForTesting;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/** Defines a snapshot object to represent a bundle. */
public class BundleSnapshot {
  public static final String UNINSTALLED_STATE_STRING =
      BundleSnapshot.getStateString(Bundle.UNINSTALLED);

  private final String name;

  private final Version version;

  private final long id;

  private final int state;

  private final String location;

  /**
   * Constructs a new <code>BundleSnapshot</code> based on the given bundle.
   *
   * @param bundle the bundle to create a snapshot representation for
   */
  public BundleSnapshot(Bundle bundle) {
    this(
        bundle.getSymbolicName(),
        bundle.getVersion(),
        bundle.getBundleId(),
        bundle.getState(),
        bundle.getLocation());
  }

  /**
   * Constructs a new <code>BundleSnapshot</code> given its separate parts.
   *
   * @param name the bundle name
   * @param version the bundle version
   * @param id the bundle identifier
   * @param state the bundle state
   * @param location the bundle location
   */
  @VisibleForTesting
  BundleSnapshot(String name, String version, long id, int state, String location) {
    this.name = name;
    this.version = new Version(version);
    this.id = id;
    this.state = state;
    this.location = location;
  }

  /**
   * Constructs a new <code>BundleSnapshot</code> given its separate parts.
   *
   * @param name the bundle name
   * @param version the bundle version
   * @param id the bundle identifier
   * @param state the bundle state
   * @param location the bundle location
   */
  @VisibleForTesting
  BundleSnapshot(String name, Version version, long id, int state, String location) {
    this.name = name;
    this.version = version;
    this.id = id;
    this.state = state;
    this.location = location;
  }

  /**
   * Gets the symbolic name for this bundle.
   *
   * @return the symbolic name for this bundle
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the name for this bundle (including its version).
   *
   * @return the name for this bundle (including its version)
   */
  public String getFullName() {
    return name + '/' + version;
  }

  public Version getVersion() {
    return version;
  }

  public long getId() {
    return id;
  }

  /**
   * Gets the state for this bundle.
   *
   * @return the state for this bundle
   */
  public int getState() {
    return state;
  }

  /**
   * Gets a simple representation for this bundle state.
   *
   * @return a simple representation for this bundle state
   */
  public SimpleState getSimpleState() {
    return BundleSnapshot.getSimpleState(state);
  }

  public String getLocation() {
    return location;
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode() + version.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof BundleSnapshot) {
      final BundleSnapshot bundle = (BundleSnapshot) o;

      return name.equals(bundle.name)
          && version.equals(bundle.version)
          && (id == bundle.id)
          && (state == bundle.state)
          && location.equals(bundle.location);
    }
    return false;
  }

  @Override
  public String toString() {
    return "bundle[name="
        + name
        + ", version="
        + version
        + ", id="
        + id
        + ", state="
        + getStateString(state)
        + ", location="
        + location
        + "]";
  }

  public static String getStateString(Bundle bundle) {
    return BundleSnapshot.getStateString(bundle.getState());
  }

  public static String getFullName(Bundle bundle) {
    return bundle.getSymbolicName() + '/' + bundle.getVersion();
  }

  public static SimpleState getSimpleState(Bundle bundle) {
    return BundleSnapshot.getSimpleState(bundle.getState());
  }

  private static String getStateString(int state) {
    return String.format("%s/%x", BundleSnapshot.getSimpleState(state), state);
  }

  private static SimpleState getSimpleState(int state) {
    if (state == Bundle.UNINSTALLED) {
      return SimpleState.UNINSTALLED;
    } else if ((state == Bundle.STARTING) || (state == Bundle.ACTIVE)) {
      return SimpleState.ACTIVE;
    } // else - INSTALLED OR STOPPING
    return SimpleState.INSTALLED;
  }

  /** Simple representation for bundle states. */
  public enum SimpleState {
    UNINSTALLED,
    INSTALLED,
    ACTIVE
  }
}
