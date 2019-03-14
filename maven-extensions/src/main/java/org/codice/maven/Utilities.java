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
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.codice.test.commons.MavenUtils;

/** Utility methods. */
public class Utilities {
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
    final String value =
        MavenUtils.getArtifactAttribute(dependencies, url.groupId(), url.artifactId(), name);

    if (value == null) {
      throw new IllegalArgumentException(
          "Could not resolve "
              + name
              + " for "
              + annotation
              + " in "
              + resourceLoader.getLocationClass().getName()
              + ". Do you have a dependency for "
              + url.groupId()
              + '/'
              + url.artifactId()
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
   * Loads the dependencies.properties for the provided annotation using the provided resource
   * loader.
   *
   * @param annotation the annotation instance for which to retrieve the dependencies.properties
   * @param resourceLoader the resource loader to use to load the dependencies.properties file
   * @param dependencies the loaded dependencies.properties information or <code>null</code> if not
   *     yet loaded
   * @return the loaded depdencies.properties or <code>properties</code> if not <code>null</code>
   */
  @SuppressWarnings(
      "squid:CallToDeprecatedMethod" /* perfectly acceptable to not care about errors closing the
                                     file once we have retrieved the info we want from it */)
  public static Properties getDependencies(
      Annotation annotation, ResourceLoader resourceLoader, Properties dependencies) {
    if (dependencies == null) {
      InputStream is = null;

      try {
        is = resourceLoader.getResourceAsStream(MavenUtils.DEPENDENCIES_FILE);
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
}
