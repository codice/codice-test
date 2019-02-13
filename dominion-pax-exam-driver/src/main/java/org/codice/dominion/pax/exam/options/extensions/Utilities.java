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
package org.codice.dominion.pax.exam.options.extensions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Properties;
import java.util.function.Function;
import org.apache.commons.io.IOUtils;
import org.codice.dominion.options.Options;
import org.codice.dominion.options.Options.MavenUrl;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.test.commons.MavenUtils;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;

/** Utility methods for defining extensions. */
public class Utilities {
  public static boolean isDefined(String value) {
    return !Options.NOT_DEFINED.equals(value);
  }

  public static String resolve(String value, String dflt) {
    return Utilities.isDefined(value) ? value : dflt;
  }

  public static <T> T applyIfDefined(String value, T t, Function<String, ?> function) {
    return Utilities.isDefined(value) ? (T) function.apply(value) : t;
  }

  public static <T, S> T mapAndApplyIfDefined(
      String value, T t, Function<String, S> mapper, Function<S, ?> function) {
    return Utilities.isDefined(value) ? (T) function.apply(mapper.apply(value)) : t;
  }

  public static MavenArtifactUrlReference getProjectReference(
      Annotation annotation, ResourceLoader resourceLoader) {
    final Properties dependencies = Utilities.getDependencies(annotation, resourceLoader, null);

    return CoreOptions.maven()
        .groupId(
            Utilities.getProjectAttribute(
                annotation, resourceLoader, MavenUtils.GROUP_ID, dependencies))
        .artifactId(
            Utilities.getProjectAttribute(
                annotation, resourceLoader, MavenUtils.ARTIFACT_ID, dependencies))
        .version(
            Utilities.getProjectAttribute(
                annotation, resourceLoader, MavenUtils.VERSION, dependencies));
  }

  public static MavenArtifactUrlReference toReference(
      Annotation annotation, MavenUrl url, ResourceLoader resourceLoader) {
    final MavenArtifactUrlReference maven = CoreOptions.maven();
    final String groupId = url.groupId();
    final String artifactId = url.artifactId();
    final String version = url.version();
    final String type = url.type();
    final String classifier = url.classifier();
    Properties dependencies = null;

    if (Options.MavenUrl.AS_PROJECT.equals(groupId)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      maven.groupId(
          Utilities.getProjectAttribute(
              annotation, resourceLoader, MavenUtils.GROUP_ID, dependencies));
    } else {
      maven.groupId(groupId);
    }
    if (Options.MavenUrl.AS_PROJECT.contains(artifactId)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);

      maven.artifactId(
          Utilities.getProjectAttribute(
              annotation, resourceLoader, MavenUtils.ARTIFACT_ID, dependencies));
    } else {
      maven.artifactId(artifactId);
    }
    if (Options.MavenUrl.AS_IN_PROJECT.equals(version)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);

      maven.version(
          Utilities.getArtifactAttribute(
              annotation, resourceLoader, url, MavenUtils.VERSION, dependencies));
    } else if (Options.MavenUrl.AS_PROJECT.equals(version)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);

      maven.version(
          Utilities.getProjectAttribute(
              annotation, resourceLoader, MavenUtils.VERSION, dependencies));
    } else {
      Utilities.applyIfDefined(version, maven, maven::version);
    }
    if (Options.MavenUrl.AS_IN_PROJECT.equals(type)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      maven.type(
          Utilities.getArtifactAttribute(
              annotation, resourceLoader, url, MavenUtils.TYPE, dependencies));
    } else if (Options.MavenUrl.AS_PROJECT.equals(type)) {
      throw new IllegalArgumentException("Must specify a valid type for: " + annotation);
    } else {
      Utilities.applyIfDefined(type, maven, maven::type);
    }
    if (Options.MavenUrl.AS_IN_PROJECT.equals(classifier)) {
      dependencies = Utilities.getDependencies(annotation, resourceLoader, dependencies);
      maven.classifier(
          Utilities.getArtifactAttribute(
              annotation, resourceLoader, url, MavenUtils.CLASSIFIER, dependencies));
    } else if (Options.MavenUrl.AS_PROJECT.equals(classifier)) {
      throw new IllegalArgumentException("Must specify a valid classifier for: " + annotation);
    } else {
      Utilities.applyIfDefined(classifier, maven, maven::classifier);
    }
    return maven;
  }

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

  @SuppressWarnings(
      "squid:CallToDeprecatedMethod" /* perfectly acceptable to not care about errors closing the
                                     file once we have retrieved the info we want from it */)
  private static Properties getDependencies(
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

  private Utilities() {}
}
