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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.karaf.features.Repository;
import org.codice.pax.exam.service.ServiceException;

/** Defines an object to represent a snapshot profile. */
public class Profile {
  private final Set<URI> repositories;
  private final List<FeatureSnapshot> features;
  private final List<BundleSnapshot> bundles;
  private final boolean snapshotOnly;

  /**
   * Constructs an empty profile.
   *
   * @param snapshotOnly <code>true</code> to only process this profile when it is later restored;
   *     <code>false</code> to also process any left over from memory after having processed the
   *     profile; thus resetting the system exactly as recorded here
   */
  public Profile(boolean snapshotOnly) {
    this.repositories = new HashSet<>();
    this.features = new LinkedList<>();
    this.bundles = new LinkedList<>();
    this.snapshotOnly = snapshotOnly;
  }

  /**
   * Constructs a new snapshot profile given the provided set of repositories, features, and
   * bundles. When later restored, this system will be reset to match exactly what is recorded here.
   *
   * @param repositories the repositories for this profile
   * @param features the features for this profile
   * @param bundles the bundles for this profile
   */
  public Profile(
      Stream<Repository> repositories,
      Stream<FeatureSnapshot> features,
      Stream<BundleSnapshot> bundles) {
    this.repositories = repositories.map(Repository::getURI).collect(Collectors.toSet());
    this.features = features.collect(Collectors.toList()); // preserve order
    this.bundles = bundles.collect(Collectors.toList()); // preserve order
    this.snapshotOnly = false;
  }

  /**
   * Checks if only the entries in this profile should be processed when later restored or if any
   * left over from memory should also be processed after this profile.
   *
   * @return <code>true</code> if only the profile should be processed; <code>false</code> to also
   *     process any left over from memory after having processed this profile; thus resetting the
   *     system exactly as recorded here
   */
  public boolean shouldOnlyProcessSnapshot() {
    return snapshotOnly;
  }

  /**
   * Adds the specified feature repository.
   *
   * @param uri the repository uri to be added to this profile
   * @return this for chaining
   */
  public Profile add(URI uri) {
    repositories.add(uri);
    return this;
  }

  /**
   * Adds the specified feature repository.
   *
   * @param uri the repository uri to be added to this profile
   * @return this for chaining
   */
  public Profile add(String uri) {
    try {
      repositories.add(new URI(uri));
    } catch (URISyntaxException e) {
      throw new ServiceException(e);
    }
    return this;
  }

  /**
   * Adds the specified feature snapshot.
   *
   * @param feature the feature to be added to this profile
   * @return this for chaining
   */
  public Profile add(FeatureSnapshot feature) {
    features.add(feature);
    return this;
  }

  /**
   * Adds the specified bundle snapshot.
   *
   * @param bundle the bundle to be added to this profile
   * @return this for chaining
   */
  public Profile add(BundleSnapshot bundle) {
    bundles.add(bundle);
    return this;
  }

  /**
   * Checks if a particular repository URI was recorded in this profile.
   *
   * @param uri the repository URI to check
   * @return <code>true</code> if the specified repository URI was recorded; <code>false</code>
   *     otherwise
   */
  public boolean isRecorded(URI uri) {
    return repositories.contains(uri);
  }

  /**
   * Retrieves all recorded repository URIs.
   *
   * @return a stream of all recorded repository URIs
   */
  public Stream<URI> repositories() {
    return repositories.stream();
  }

  /**
   * Retrieves all recorded features.
   *
   * @return a stream of all recorded features
   */
  public Stream<FeatureSnapshot> features() {
    return features.stream();
  }

  /**
   * Retrieves all recorded bundles
   *
   * @return a stream of all recorded bundles
   */
  public Stream<BundleSnapshot> bundles() {
    // sort bundles based on bundle id to be sure we process them in that order
    return bundles.stream().sorted(Comparator.comparing(BundleSnapshot::getId));
  }

  /**
   * Checks if this profile is empty; meaning there are no repositories, features, and bundles were
   * recorded.
   *
   * @return <code>true</code> if this profile is empty; <code>false</code> otherwise
   */
  public boolean isEmpty() {
    return repositories.isEmpty() && features.isEmpty() && bundles.isEmpty();
  }

  @Override
  public int hashCode() {
    return Objects.hash(repositories, features, bundles);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof Profile) {
      final Profile profile = (Profile) o;

      return repositories.equals(profile.repositories)
          && features.equals(profile.features)
          && bundles.equals(profile.bundles);
    }
    return false;
  }

  @Override
  public String toString() {
    return Stream.of(repositories.stream(), features.stream(), bundles.stream())
        .flatMap(Function.identity())
        .map(Object::toString)
        .collect(java.util.stream.Collectors.joining(", ", "profile [", "]"));
  }
}
