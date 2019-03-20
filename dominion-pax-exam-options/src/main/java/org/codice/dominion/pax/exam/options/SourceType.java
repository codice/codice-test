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
package org.codice.dominion.pax.exam.options;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;
import org.codice.maven.MavenUrlReference;

/** Different types for the source content. */
public enum SourceType {
  /** Source if a file is on disk. */
  FILE {
    @Override
    public File toFile(
        String source,
        @Nullable PaxExamInterpolator interpolator,
        @Nullable ResourceLoader resourceLoader) {
      return new File(FilenameUtils.separatorsToSystem(source));
    }
  },

  /** Source is from a URL. */
  URL {
    @Override
    public File toFile(
        String source,
        @Nullable PaxExamInterpolator interpolator,
        @Nullable ResourceLoader resourceLoader)
        throws IOException {
      return SourceType.fromUrlToFile(source);
    }
  },

  /** Source is from a Maven artifact URL. */
  ARTIFACT {
    @Override
    public File toFile(
        String source, @Nullable PaxExamInterpolator interpolator, ResourceLoader resourceLoader)
        throws IOException {
      return SourceType.fromUrlToFile(source);
    }
  },

  /** Source is an actual content string. */
  CONTENT {
    @Override
    public File toFile(
        String source, PaxExamInterpolator interpolator, @Nullable ResourceLoader resourceLoader)
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
        String source, @Nullable PaxExamInterpolator interpolator, ResourceLoader resourceLoader)
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
      String source,
      @Nullable PaxExamInterpolator interpolator,
      @Nullable ResourceLoader resourceLoader)
      throws IOException;

  /**
   * Converts a source of this type to a file.
   *
   * @param source the maven artifact url of source to retrieve
   * @param resourceLoader the resource loader to use when loading resources
   * @return a corresponding temporary file with the content of the specified resource of this type
   * @throws IOException if an I/O error occurred
   */
  public static File toFile(MavenUrl source, ResourceLoader resourceLoader) throws IOException {
    return SourceType.fromArtifactToFile(source, resourceLoader);
  }

  /**
   * Converts a source url to a file.
   *
   * @param url the source url to retrieve and create a file with
   * @return a corresponding temporary file with the content of the specified url
   * @throws IOException if an I/O error occurred
   */
  private static File fromUrlToFile(String url) throws IOException {
    try (final InputStream is = new URL(url).openStream()) {
      return SourceType.fromStreamToFile(is);
    }
  }

  /**
   * Converts a maven artifact url to a file.
   *
   * @param mavenUrl the maven artifact url to retrieve and create a file with
   * @param resourceLoader the loader to use for loading <code>dependencies.properties</code>
   * @return a corresponding temporary file with the content of the specified maven url
   * @throws IOException if an I/O error occurred
   */
  private static File fromArtifactToFile(MavenUrl mavenUrl, ResourceLoader resourceLoader)
      throws IOException {
    return SourceType.fromUrlToFile(
        MavenUrlReference.resolve(mavenUrl, mavenUrl, resourceLoader).getURL());
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
  private static File fromStreamToFile(InputStream is) throws IOException {
    final File temp =
        Files.createTempFile(
                KarafDistributionConfigurationFileReplaceOption.class.getName(), ".tmp")
            .toFile();

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
  private static File fromResourceToFile(String resource, ResourceLoader resourceLoader)
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
  private static File fromContentToFile(String content, PaxExamInterpolator interpolator)
      throws IOException {
    try (final InputStream is =
        new ByteArrayInputStream(
            StringUtils.appendIfMissing(content, interpolator.getLineSeparator())
                .getBytes("UTF-8"))) {
      return SourceType.fromStreamToFile(is);
    }
  }
}
