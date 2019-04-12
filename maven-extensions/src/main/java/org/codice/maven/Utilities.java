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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.codice.test.commons.MavenUtils;
import org.ops4j.pax.url.mvn.Handler;

/** Utility methods. */
public class Utilities {
  private static final String PROTOCOL_HANDLER_PKGS_KEY = "java.protocol.handler.pkgs";

  private static boolean initialized = false;

  /**
   * Gets a particular artifact attribute from the provided dependencies properties.
   *
   * @param annotation the annotation instance for which to retrieve the artifact info
   * @param resourceLoader the resource loader the dependencies.properties file was loaded from
   * @param url the maven url (i.e. group id and artifact id) for which we are retrieving an
   *     artifact attribute
   * @param name the name of the attribute for the maven url to retrieve from the
   *     dependencies.properties
   * @param dependencies the loaded dependencies.properties information
   * @return the corresponding attribute's value
   */
  public static String getArtifactAttribute(
      Annotation annotation,
      ResourceLoader resourceLoader,
      MavenUrl url,
      String name,
      Properties dependencies) {
    return Utilities.getArtifactAttribute(
        annotation, resourceLoader, url.groupId(), url.artifactId(), name, dependencies);
  }

  /**
   * Gets a particular artifact attribute from the provided dependencies properties.
   *
   * @param annotation the annotation instance for which to retrieve the artifact info
   * @param resourceLoader the resource loader the dependencies.properties file was loaded from
   * @param groupId the group id of the artifact for which to retrieve an artifact attribute
   * @param artifactId the id of the artifact for which to retrieve an artifact attribute
   * @param name the name of the attribute for the maven url to retrieve from the
   *     dependencies.properties
   * @param dependencies the loaded dependencies.properties information
   * @return the corresponding attribute's value
   */
  public static String getArtifactAttribute(
      Annotation annotation,
      ResourceLoader resourceLoader,
      String groupId,
      String artifactId,
      String name,
      Properties dependencies) {
    final String value = MavenUtils.getArtifactAttribute(dependencies, groupId, artifactId, name);

    if (value == null) {
      throw new IllegalArgumentException(
          "Could not resolve "
              + name
              + " for "
              + annotation
              + " in "
              + resourceLoader.getLocationClass().getName()
              + ". Do you have a dependency for "
              + groupId
              + '/'
              + artifactId
              + " in your maven project?");
    }
    return value;
  }

  /**
   * Gets a particular project attribute from the provided dependencies properties.
   *
   * @param annotation the annotation instance for which to retrieve the artifact info
   * @param resourceLoader the resource loader the dependencies.properties file was loaded from
   * @param name the name of the project attribute to retrieve from the dependencies.properties
   * @param dependencies the loaded dependencies.properties information
   * @return the corresponding attribute's value
   */
  public static String getProjectAttribute(
      Annotation annotation, ResourceLoader resourceLoader, String name, Properties dependencies) {
    final String value = MavenUtils.getProjectAttribute(dependencies, name);

    if (value == null) {
      throw new IllegalArgumentException(
          "Could not resolve "
              + name
              + " from project dependencies for "
              + annotation
              + " in "
              + resourceLoader.getLocationClass().getName());
    }
    return value;
  }

  /**
   * Gets the maven url for a particular project from the provided dependencies properties.
   *
   * @param annotation the annotation instance for which to retrieve the artifact info
   * @param resourceLoader the resource loader the dependencies.properties file was loaded from
   * @param dependencies the loaded dependencies.properties information
   * @return the corresponding maven url
   */
  public static MavenUrlReference getProjectUrl(
      Annotation annotation, ResourceLoader resourceLoader, Properties dependencies) {
    return new MavenUrlReference(
            Utilities.getProjectAttribute(
                annotation, resourceLoader, MavenUtils.GROUP_ID, dependencies),
            Utilities.getProjectAttribute(
                annotation, resourceLoader, MavenUtils.ARTIFACT_ID, dependencies))
        .version(MavenUtils.getProjectAttribute(dependencies, MavenUtils.VERSION))
        .type(MavenUtils.getProjectAttribute(dependencies, MavenUtils.TYPE))
        .classifier(MavenUtils.getProjectAttribute(dependencies, MavenUtils.CLASSIFIER));
  }

  /**
   * Loads the dependencies.properties for the provided annotation using the provided resource
   * loader.
   *
   * @param annotation the annotation instance for which to retrieve the dependencies.properties
   * @param resourceLoader the resource loader to use to load the dependencies.properties file
   * @param dependencies the loaded dependencies.properties information or <code>null</code> if not
   *     yet loaded
   * @return the loaded dependencies.properties or <code>properties</code> if not <code>null</code>
   */
  @SuppressWarnings(
      "squid:CallToDeprecatedMethod" /* perfectly acceptable to not care about errors closing the
                                     file once we have retrieved the info we want from it */)
  public static Properties getDependencies(
      Annotation annotation, ResourceLoader resourceLoader, @Nullable Properties dependencies) {
    if (dependencies == null) {
      InputStream is = null;

      try {
        is =
            AccessController.doPrivileged(
                (PrivilegedAction<InputStream>)
                    () -> resourceLoader.getResourceAsStream(MavenUtils.DEPENDENCIES_FILE));
        if (is == null) {
          throw new FileNotFoundException(
              "File '"
                  + MavenUtils.DEPENDENCIES_FILE
                  + "' associated with "
                  + resourceLoader.getLocationClass().getName()
                  + " could not be found in classpath or on disk");
        }
        dependencies = new Properties();
        dependencies.load(is);
      } catch (IOException e) {
        throw new IllegalArgumentException(
            "Could not retrieved dependency information generated by maven for "
                + annotation
                + " in "
                + resourceLoader.getLocationClass().getName(),
            e);
      } finally {
        IOUtils.closeQuietly(is);
      }
    }
    return dependencies;
  }

  /** Initializes a URL protocol handler for the Maven protocol if not already registered. */
  public static synchronized void initMavenUrlHandler() {
    if (!Utilities.initialized) {
      final String pkgs = System.getProperty(PROTOCOL_HANDLER_PKGS_KEY);
      final String pkg = StringUtils.removeEnd(Handler.class.getPackage().getName(), ".mvn");

      if (StringUtils.isEmpty(pkgs)) {
        System.setProperty(PROTOCOL_HANDLER_PKGS_KEY, pkg);
      } else if (!('|' + pkgs + '|').contains(pkg)) {
        System.setProperty(PROTOCOL_HANDLER_PKGS_KEY, StringUtils.appendIfMissing(pkgs, "|") + pkg);
      }
      Utilities.initialized = true;
    }
  }
}
