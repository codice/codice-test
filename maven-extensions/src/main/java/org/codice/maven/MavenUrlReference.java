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
package org.codice.maven;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Properties;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.codice.test.commons.MavenUtils;

/** This class is used to represent a maven URL. */
public class MavenUrlReference {

  static {
    // make sure we register the protocol handler for the mvn protocol
    Utilities.initMavenUrlHandler();
  }

  /**
   * Resolves a given maven url annotation to a maven url reference. If this one references the
   * project in anyway (i.e. {@link MavenUrl#AS_IN_PROJECT} or {@link MavenUrl#AS_PROJECT}) then the
   * information is retrieved from the specified annotation and resource loader.
   *
   * <p>This method will load the dependencies.properties file that was generated and packaged by
   * the maven project where the annotation was used.
   *
   * @param url the maven url for which to resolve the maven artifact url info
   * @param annotation the annotation instance for which to retrieve the maven url info
   * @param resourceLoader the resource loader to load the dependencies.properties file with
   */
  public static MavenUrlReference resolve(
      MavenUrl url, Annotation annotation, ResourceLoader resourceLoader) {
    String groupId = url.groupId();
    String artifactId = url.artifactId();
    final String version = url.version();
    final String type = url.type();
    final String classifier = url.classifier();
    Properties dependencies = null;

    if (MavenUrl.AS_PROJECT.equals(groupId)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      groupId =
          Utilities.getProjectAttribute(
              annotation, resourceLoader, MavenUtils.GROUP_ID, dependencies);
    }
    if (MavenUrl.AS_PROJECT.contains(artifactId)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      artifactId =
          Utilities.getProjectAttribute(
              annotation, resourceLoader, MavenUtils.ARTIFACT_ID, dependencies);
    }
    final MavenUrlReference maven = new MavenUrlReference(groupId, artifactId);

    if (MavenUrl.AS_IN_PROJECT.equals(version)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      maven.version(
          Utilities.getArtifactAttribute(
              annotation, resourceLoader, groupId, artifactId, MavenUtils.VERSION, dependencies));
    } else if (MavenUrl.AS_PROJECT.equals(version)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      maven.version(
          Utilities.getProjectAttribute(
              annotation, resourceLoader, MavenUtils.VERSION, dependencies));
    } else if (!version.isEmpty()) {
      maven.version(version);
    }
    if (MavenUrl.AS_IN_PROJECT.equals(type)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      maven.type(
          Utilities.getArtifactAttribute(
              annotation, resourceLoader, groupId, artifactId, MavenUtils.TYPE, dependencies));
    } else if (MavenUrl.AS_PROJECT.equals(type)) {
      throw new IllegalArgumentException("Must specify a valid type for: " + annotation);
    } else if (!type.isEmpty()) {
      maven.type(type);
    }
    if (MavenUrl.AS_IN_PROJECT.equals(classifier)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      maven.classifier(
          Utilities.getArtifactAttribute(
              annotation,
              resourceLoader,
              groupId,
              artifactId,
              MavenUtils.CLASSIFIER,
              dependencies));
    } else if (MavenUrl.AS_PROJECT.equals(classifier)) {
      throw new IllegalArgumentException("Must specify a valid classifier for: " + annotation);
    } else if (!classifier.isEmpty()) {
      maven.classifier(classifier);
    }
    return maven;
  }

  /** Artifact group id. */
  private final String groupId;

  /** Artifact id. */
  private final String artifactId;

  /** Artifact type (can be null case when the default type is used = jar). */
  @Nullable private String type;

  /** Artifact version/version range (can be null case when latest version will be used). */
  @Nullable private String version;

  /** Artifact classifier. */
  @Nullable private String classifier;

  /**
   * Creates a new maven URL reference.
   *
   * @param groupId the group id
   * @param artifactId the artifact id
   * @throws IllegalArgumentException if <code>groupId</code> or <code>artifactId</code> are invalid
   */
  public MavenUrlReference(String groupId, String artifactId) {
    if (StringUtils.isEmpty(groupId) || MavenUrl.AS_PROJECT.equals(groupId)) {
      throw new IllegalArgumentException("invalid maven group id: " + groupId);
    }
    if (StringUtils.isEmpty(artifactId) || MavenUrl.AS_PROJECT.equals(artifactId)) {
      throw new IllegalArgumentException("invalid maven artifact id: " + artifactId);
    }
    this.groupId = groupId;
    this.artifactId = artifactId;
  }

  /**
   * Sets the artifact version or version range.
   *
   * @param version the artifact version or version range
   * @return this for chaining
   */
  public MavenUrlReference version(String version) {
    this.version = version;
    return this;
  }

  /**
   * Sets the artifact type.
   *
   * @param type the artifact type
   * @return this for chaining
   */
  public MavenUrlReference type(final String type) {
    this.type = type;
    return this;
  }

  /**
   * Sets the artifact classifier.
   *
   * @param classifier the artifact type
   * @return this for chaining
   */
  public MavenUrlReference classifier(String classifier) {
    this.classifier = classifier;
    return this;
  }

  /**
   * Checks if this maven URL represent a snapshot version.
   *
   * @return <code>true</code> if the specified version is a snapshot version, <code>false</code> if
   *     not and <code>null</code> if the version is not yet specified
   */
  public Boolean isSnapshot() {
    return (version == null) ? null : version.endsWith("SNAPSHOT");
  }

  /**
   * Gets the artifact group id.
   *
   * @return the artifact group id
   */
  public String getGroupId() {
    return groupId;
  }

  /**
   * Gets the artifact id.
   *
   * @return the artifact id
   */
  public String getArtifactId() {
    return artifactId;
  }

  /**
   * Gets the artifact version.
   *
   * @return the artifact version or <code>null</code> if not defined
   */
  @Nullable
  public String getVersion() {
    return version;
  }

  /**
   * Gets the artifact type.
   *
   * @return the artifact type or <code>null</code> if not defined
   */
  @Nullable
  public String getType() {
    return type;
  }

  /**
   * Gets the artifact classifier.
   *
   * @return the artifact classifier or <code>null</code> if not defined
   */
  @Nullable
  public String getClassifier() {
    return classifier;
  }

  /**
   * Gets the corresponding URL string representation.
   *
   * @return the corresponding URL
   */
  public String getURL() {
    final StringBuilder sb = new StringBuilder();

    sb.append("mvn:").append(groupId).append("/").append(artifactId);
    if (version != null || type != null || classifier != null) {
      sb.append("/");
    }
    if (version != null) {
      sb.append(version);
    }
    if ((type != null) || (classifier != null)) {
      sb.append("/");
    }
    if (type != null) {
      sb.append(type);
    }
    if (classifier != null) {
      sb.append("/").append(classifier);
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, artifactId, version, type, classifier);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof MavenUrlReference) {
      final MavenUrlReference ref = (MavenUrlReference) obj;

      return groupId.equals(ref.groupId)
          && artifactId.equals(ref.artifactId)
          && Objects.equals(version, ref.version)
          && Objects.equals(type, ref.type)
          && Objects.equals(classifier, ref.classifier);
    }
    return false;
  }

  @Override
  public String toString() {
    return getURL();
  }
}
