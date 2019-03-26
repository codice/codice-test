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

import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeatureState;
import org.apache.karaf.features.FeaturesService;

/** Defines a snapshot object to represent a feature. */
public class FeatureSnapshot {
  private static final String NO_VERSION_FEATURE_OSGI_REQUIREMENT_FORMAT = "feature:%s/0";

  private static final String FEATURE_OSGI_REQUIREMENT_FORMAT = "feature:%s/[%s,%s]";

  private final String name;

  @Nullable private final String version;

  private final String id;

  private final FeatureState state;

  @Nullable private final Boolean required;

  @Nullable private final String region;

  /**
   * Constructs a new <code>FeatureSnapshot</code> based on the given feature.
   *
   * @param feature the feature to create a snapshot representation for
   * @param featureService service used for retrieving info for features
   */
  public FeatureSnapshot(Feature feature, FeaturesService featureService) {
    this.name = feature.getName();
    this.id = feature.getId();
    this.version = feature.getVersion();
    this.state = featureService.getState(feature.getId());
    this.required = featureService.isRequired(feature);
    this.region = null;
  }

  /**
   * Constructs a new <code>FeatureSnapshot</code> given the feature annotation representing a
   * feature to be started.
   *
   * @param feature the feature to be started
   */
  public FeatureSnapshot(org.codice.pax.exam.service.Feature.Start feature) {
    this.name = feature.name();
    this.version = feature.version().isEmpty() ? null : feature.version();
    this.id = name + "/" + getVersion();
    this.state = FeatureState.Started;
    this.required = null;
    this.region = feature.region();
  }

  /**
   * Constructs a new <code>FeatureSnapshot</code> given the feature annotation representing a
   * feature to be stopped.
   *
   * @param feature the feature to be stopped
   */
  public FeatureSnapshot(org.codice.pax.exam.service.Feature.Stop feature) {
    this.name = feature.name();
    this.version = null;
    this.id = name + "/" + getVersion();
    this.state = FeatureState.Uninstalled;
    this.required = null;
    this.region = null;
  }

  public String getName() {
    return name;
  }

  public boolean hasVersion() {
    return version != null;
  }

  public String getVersion() {
    return (version != null) ? version : "0.0.0";
  }

  public String getId() {
    return id;
  }

  public FeatureState getState() {
    return state;
  }

  @Nullable
  public Boolean isRequired() {
    return required;
  }

  public String getRegion() {
    return (region != null) ? region : FeaturesService.ROOT_REGION;
  }

  /**
   * Builds a requirement string for this feature.
   *
   * @return the corresponding requirement string
   */
  public String toRequirement() {
    if (version == null) {
      return String.format(FeatureSnapshot.NO_VERSION_FEATURE_OSGI_REQUIREMENT_FORMAT, name);
    }
    return String.format(FeatureSnapshot.FEATURE_OSGI_REQUIREMENT_FORMAT, name, version, version);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof FeatureSnapshot) {
      final FeatureSnapshot feature = (FeatureSnapshot) o;

      return (state == feature.state)
          && name.equals(feature.name)
          && Objects.equals(version, feature.version)
          && id.equals(feature.id)
          && Objects.equals(required, feature.required)
          && Objects.equals(region, feature.region);
    }
    return false;
  }

  @Override
  public String toString() {
    return "feature[name="
        + name
        + ", version="
        + getVersion()
        + ", id="
        + id
        + ", state="
        + state
        + ", required="
        + required
        + ", region="
        + getRegion()
        + "]";
  }

  /**
   * Builds a requirement string for the provided feature.
   *
   * @param feature the feature to get a requirement string for
   * @return the corresponding requirement string
   */
  public static String toRequirement(Feature feature) {
    final String vstr = feature.getVersion();

    return String.format(
        FeatureSnapshot.FEATURE_OSGI_REQUIREMENT_FORMAT, feature.getName(), vstr, vstr);
  }
}
