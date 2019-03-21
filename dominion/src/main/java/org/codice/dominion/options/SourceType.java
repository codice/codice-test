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
package org.codice.dominion.options;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.codice.dominion.interpolate.Interpolator;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;
import org.codice.maven.MavenUrlReference;

/**
 * Enumeration defining different types for the source content. It also provides utility methods to
 * convert referenced content into temporary files.
 */
public enum SourceType {
  /** Source is a file on disk. */
  FILE {
    @Override
    public File toFile(
        String source,
        @Nullable Interpolator interpolator,
        @Nullable ResourceLoader resourceLoader) {
      return new File(FilenameUtils.separatorsToSystem(source));
    }
  },

  /** Source is from a URL. */
  URL {
    @Override
    public File toFile(
        String source, @Nullable Interpolator interpolator, @Nullable ResourceLoader resourceLoader)
        throws IOException {
      return SourceType.fromUrlToFile(source);
    }
  },

  /** Source is from a Maven artifact URL. */
  ARTIFACT {
    @Override
    public File toFile(
        String source, @Nullable Interpolator interpolator, ResourceLoader resourceLoader)
        throws IOException {
      return SourceType.fromUrlToFile(source);
    }
  },

  /** Source is an actual content string. */
  CONTENT {
    @Override
    public File toFile(
        String source, Interpolator interpolator, @Nullable ResourceLoader resourceLoader)
        throws IOException {
      if (interpolator == null) {
        throw new IllegalArgumentException("must provide an interpolator to load content");
      }
      return SourceType.fromContentToFile(source, interpolator);
    }
  },

  /** Source is from a resource retrievable from the classloader. */
  RESOURCE {
    @Override
    public File toFile(
        String source, @Nullable Interpolator interpolator, ResourceLoader resourceLoader)
        throws IOException {
      if (resourceLoader == null) {
        throw new IllegalArgumentException("must provide a resource loader to load resources");
      }
      return SourceType.fromResourceToFile(source, resourceLoader);
    }
  };

  /**
   * Converts a source of this type to a file.
   *
   * @param source the source of this type to retrieve
   * @param interpolator the interpolator to use for retrieving line separators when needed
   * @param resourceLoader the resource loader to use when loading resources
   * @return a corresponding temporary file with the content of the specified resource of this type
   * @throws IOException if an I/O error occurred
   */
  public abstract File toFile(
      String source, @Nullable Interpolator interpolator, @Nullable ResourceLoader resourceLoader)
      throws IOException;

  /**
   * Converts annotation information referencing a source into a file. This methods supports the
   * annotation defining the following attributes:
   *
   * <ul>
   *   <li><code>String file() default {@link Options#NOT_DEFINED}</code> - the filename for the
   *       source
   *   <li><code>String url() default {@link Options#NOT_DEFINED}</code> - the url for the source
   *   <li><code>
   *       MavenUrl artifact() default @{link MavenUrl}(groupId = {@link Options#NOT_DEFINED}), artifactId = {@link Options#NOT_DEFINED})
   *       </code> - the Maven url for the source
   *   <li><code>String[] content() default {@link Options#NOT_DEFINED}</code> - the text for the
   *       source
   *   <li><code>String resource() default {@link Options#NOT_DEFINED}</code> - the resource name
   *       for the source
   * </ul>
   *
   * <p>Refer to {@link Options.UpdateFile} for an example of such annotation.
   *
   * <p><i>Note:</i> One of <code>file()</code>, <code>url()</code>, <code>artifact()</code>, <code>
   * content()</code> or <code>resource()</code> must be specified.</code>.
   *
   * @param annotation the annotation defining the above information
   * @param interpolator the interpolator to use for retrieving line separators when needed
   * @param resourceLoader the resource loader to use when loading resources
   * @return a corresponding temporary file with the content of the specified source
   * @throws IllegalArgumentException if no attributes are defined; more than one are defined or the
   *     annotation class doesn't define them or it defines them with the wrong type
   * @throws IOException if an I/O error occurred
   */
  public static File fromAnnotationToFile(
      Annotation annotation, Interpolator interpolator, ResourceLoader resourceLoader)
      throws IOException {
    final String file = SourceType.get(annotation, "file", resourceLoader);
    final String url = SourceType.get(annotation, "url", resourceLoader);
    final MavenUrl artifact = SourceType.get(annotation, "artifact", resourceLoader);
    final String[] content = SourceType.get(annotation, "content", resourceLoader);
    final String resource = SourceType.get(annotation, "resource", resourceLoader);
    final boolean fileIsDefined = org.codice.dominion.options.Utilities.isDefined(file);
    final boolean urlIsDefined = org.codice.dominion.options.Utilities.isDefined(url);
    final boolean groupIdIsDefined =
        org.codice.dominion.options.Utilities.isDefined(artifact.groupId());
    final boolean contentIsDefined = Utilities.isDefined(content);
    final boolean resourceIsDefined = org.codice.dominion.options.Utilities.isDefined(resource);
    final long count =
        Stream.of(
                fileIsDefined, urlIsDefined, groupIdIsDefined, contentIsDefined, resourceIsDefined)
            .filter(Boolean.TRUE::equals)
            .count();

    if (count == 0L) {
      throw new IllegalArgumentException(
          "must specify one of file(), url(), artifact(), content(), or resource() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName());
    } else if (count > 1L) {
      throw new IllegalArgumentException(
          "specify only one of file(), url(), artifact(), content(), or resource() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName());
    }
    if (fileIsDefined) {
      return SourceType.FILE.toFile(file, interpolator, resourceLoader);
    } else if (urlIsDefined) {
      return SourceType.URL.toFile(url, interpolator, resourceLoader);
    } else if (groupIdIsDefined) {
      return SourceType.fromArtifactToFile(artifact, resourceLoader);
    } else if (contentIsDefined) {
      return SourceType.CONTENT.toFile(
          Stream.of(content)
              .map(c -> StringUtils.appendIfMissing(c, interpolator.getLineSeparator()))
              .collect(Collectors.joining()),
          interpolator,
          resourceLoader);
    }
    return SourceType.RESOURCE.toFile(resource, interpolator, resourceLoader);
  }

  /**
   * Converts a source url to a file.
   *
   * @param url the source url to retrieve and create a file with
   * @return a corresponding temporary file with the content of the specified url
   * @throws IOException if an I/O error occurred
   */
  public static File fromUrlToFile(String url) throws IOException {
    try (final InputStream is = new URL(url).openStream()) {
      return SourceType.fromStreamToFile(is);
    }
  }

  /**
   * Converts a maven artifact url to a file.
   *
   * @param artifact the maven artifact url to retrieve and create a file with
   * @param resourceLoader the loader to use for loading <code>dependencies.properties</code>
   * @return a corresponding temporary file with the content of the specified maven url
   * @throws IOException if an I/O error occurred
   */
  public static File fromArtifactToFile(MavenUrl artifact, ResourceLoader resourceLoader)
      throws IOException {
    return SourceType.fromUrlToFile(
        MavenUrlReference.resolve(artifact, artifact, resourceLoader).getURL());
  }

  /**
   * Converts a source stream to a file.
   *
   * @param is the source stream to retrieve and create a file with
   * @return a corresponding temporary file with the content of the specified stream
   * @throws IOException if an I/O error occurred
   */
  @SuppressWarnings({
    "squid:S4042" /* deleting a temp file and we don't care if it fails */,
    "squid:S899" /* deleting a temp file and we don't care if it fails */
  })
  public static File fromStreamToFile(InputStream is) throws IOException {
    final File temp = Files.createTempFile(SourceType.class.getName(), ".tmp").toFile();

    temp.deleteOnExit();
    try {
      FileUtils.copyInputStreamToFile(is, temp);
      return temp;
    } catch (IOException e) {
      temp.delete();
      throw e;
    }
  }

  /**
   * Converts a resource to a file.
   *
   * @param resource the resource to retrieve and create a file with
   * @param resourceLoader the loader to use for loading the resource
   * @return a corresponding temporary file with the content of the specified resource
   * @throws IOException if an I/O error occurred
   */
  public static File fromResourceToFile(String resource, ResourceLoader resourceLoader)
      throws IOException {
    try (final InputStream is = resourceLoader.getResourceAsStream(resource)) {
      if (is == null) {
        throw new IOException(
            "unable to locate resource "
                + resource
                + " in "
                + resourceLoader.getLocationClass().getName());
      }
      return SourceType.fromStreamToFile(is);
    }
  }

  /**
   * Converts a string content to a file.
   *
   * @param content the content to write to a file
   * @param interpolator the interpolator to use for retrieving the line separators
   * @return a corresponding temporary file with the specified content
   * @throws IOException if an I/O error occurred
   */
  public static File fromContentToFile(String content, Interpolator interpolator)
      throws IOException {
    try (final InputStream is =
        new ByteArrayInputStream(
            StringUtils.appendIfMissing(content, interpolator.getLineSeparator())
                .getBytes("UTF-8"))) {
      return SourceType.fromStreamToFile(is);
    }
  }

  private static <T> T get(Annotation annotation, String name, ResourceLoader resourceLoader) {
    final Object value;

    try {
      return (T) MethodUtils.invokeExactMethod(annotation, name);
    } catch (ClassCastException | NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalArgumentException(
          "unable to retrieve "
              + name
              + "() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName(),
          e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException(
          "unable to retrieve "
              + name
              + "() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName(),
          e.getTargetException());
    }
  }
}
