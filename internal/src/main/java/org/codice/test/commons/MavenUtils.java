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
package org.codice.test.commons;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Properties;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ProxyInputStream;

/** Sets of maven useful functions. */
public class MavenUtils {
  public static final String DEPENDENCIES_FILE = "META-INF/maven/dependencies.properties";

  public static final String GROUP_ID = "groupId";
  public static final String ARTIFACT_ID = "artifactId";
  public static final String VERSION = "version";
  public static final String TYPE = "type";
  public static final String CLASSIFIER = "classifier";

  private MavenUtils() {}

  /**
   * Gets the information from the <code>dependencies.properties</code> file defined in the same
   * location as the specified class.
   *
   * @param clazz the class where to locate the dependencies information
   * @return the corresponding dependencies or <code>null</code> if not found
   */
  @SuppressWarnings(
      "squid:CallToDeprecatedMethod" /* perfectly acceptable to not care about errors closing the
                                     file once we have retrieved the info we want from it */)
  @Nullable
  public static Properties getDependenciesFrom(Class<?> clazz) {
    final ProtectionDomain domain = clazz.getProtectionDomain();

    if (domain != null) {
      final CodeSource codesource = domain.getCodeSource();
      InputStream is = null;

      try {
        is = MavenUtils.getResourceAsStreamFromCodeSource(MavenUtils.DEPENDENCIES_FILE, codesource);
        if (is != null) {
          final Properties dependencies = new Properties();

          dependencies.load(is);
          return dependencies;
        }
      } catch (IOException e) { // ignored
      } finally {
        IOUtils.closeQuietly(is);
      }
    }
    return null;
  }

  /**
   * Gets a project attribute from the <code>dependencies.properties</code> file defined in the same
   * * location as the specified class.
   *
   * @param clazz the class where to locate the dependencies information
   * @param name the name of the attribute to retrieve
   * @return the corresponding attribute value or <code>null</code> if no dependencies provided or
   *     if the attribute was not found in the dependencies
   */
  public static String getProjectAttribute(Class<?> clazz, String name) {
    return MavenUtils.getProjectAttribute(MavenUtils.getDependenciesFrom(clazz), name);
  }

  /**
   * Gets a project attribute from the provided dependencies.
   *
   * @param dependencies the dependencies from which to retrieve the attribute
   * @param name the name of the attribute to retrieve
   * @return the corresponding attribute value or <code>null</code> if no dependencies provided or
   *     if the attribute was not found in the dependencies
   */
  public static String getProjectAttribute(@Nullable Properties dependencies, String name) {
    return (dependencies != null) ? dependencies.getProperty(name) : null;
  }

  /**
   * Gets a n artifact's attribute from the provided dependencies.
   *
   * @param dependencies the dependencies from which to retrieve the attribute
   * @param groupId the group id of the artifact for which to retrieve the attribute
   * @param artifactId the id of the artifact for which to retrieve the attribute
   * @param name the name of the attribute to retrieve
   * @return the corresponding attribute value or <code>null</code> if no dependencies provided or
   *     if the attribute was not found in the dependencies
   */
  public static String getArtifactAttribute(
      @Nullable Properties dependencies, String groupId, String artifactId, String name) {
    return (dependencies != null)
        ? dependencies.getProperty(groupId + '/' + artifactId + '/' + name)
        : null;
  }

  /**
   * Gets an input stream for a resource defined in a given code source location.
   *
   * @param name the name of the resource to retrieve
   * @param codesource the code source from which to retrieve it
   * @return the corresponding input stream or <code>null</code> if not found
   */
  @SuppressWarnings("squid:S2095" /* the classloader is being closed by the stream itself */)
  @Nullable
  public static InputStream getResourceAsStreamFromCodeSource(
      String name, @Nullable CodeSource codesource) {
    if (codesource == null) {
      return null;
    }
    final URL location = codesource.getLocation();

    if (location != null) {
      final URLClassLoader classloader =
          new URLClassLoader(new URL[] {location}) {
            @Override
            public URL getResource(String name) {
              // don't bother checking the parent classloader as we will do that in a different step
              return super.findResource(name);
            }
          };
      final InputStream is = classloader.getResourceAsStream(name);

      if (is != null) {
        return new ProxyInputStream(is) {
          @Override
          public void close() throws IOException {
            super.close();
            classloader.close();
          }
        };
      }
    }
    return null;
  }
}
