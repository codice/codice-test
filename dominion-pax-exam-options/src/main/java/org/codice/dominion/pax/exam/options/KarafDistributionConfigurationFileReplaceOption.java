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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileReplacementOption;

/**
 * Provides an extension to PaxExam's KarafDistributionConfigurationFileReplacementOption which
 * supports the source being provided via a file, url, hard-coded content, or a resource.
 */
public class KarafDistributionConfigurationFileReplaceOption
    extends KarafDistributionConfigurationFileReplacementOption {
  /** Different types for the source content. */
  public enum Type {
    /** Source if a file is on disk. */
    FILE,

    /** Source is from a URL. */
    URL,

    /** Source is an actual content string. */
    CONTENT,

    /** Source is from a resource retrievable from the classloader. */
    RESOURCE
  }

  /**
   * Creates a new file replace PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve line separator
   * @param configurationFilePath the configuration file path to replace
   * @param type the type of the source (any except for {@link Type#RESOURCE})
   * @param source the source where to get the content
   * @throws IllegalArgumentException if <code>type</code> is {@link Type#RESOURCE}
   * @throws IOException if an I/O error occurs while retrieving/creating the source
   */
  public KarafDistributionConfigurationFileReplaceOption(
      PaxExamInterpolator interpolator, String configurationFilePath, Type type, String source)
      throws IOException {
    this(
        configurationFilePath,
        KarafDistributionConfigurationFileReplaceOption.toFile(interpolator, type, source, null));
  }

  /**
   * Creates a new file replace PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve line separator
   * @param configurationFilePath the configuration file path to replace
   * @param content the source where to get the content where each string represents a separate line
   *     to be added
   * @throws IOException if an I/O error occurs while creating the source
   */
  public KarafDistributionConfigurationFileReplaceOption(
      PaxExamInterpolator interpolator, String configurationFilePath, String... content)
      throws IOException {
    this(
        interpolator,
        configurationFilePath,
        Type.CONTENT,
        Stream.of(content)
            .map(c -> StringUtils.appendIfMissing(c, interpolator.getLineSeparator()))
            .collect(Collectors.joining()));
  }

  /**
   * Creates a new file replace PaxExam option.
   *
   * @param configurationFilePath the configuration file path to replace
   * @param source the source where to get the content
   * @param resourceLoader the resource loader to use if required
   * @throws IOException if an I/O error occurs while retrieving/creating the source
   */
  public KarafDistributionConfigurationFileReplaceOption(
      String configurationFilePath, String source, ResourceLoader resourceLoader)
      throws IOException {
    this(
        configurationFilePath,
        KarafDistributionConfigurationFileReplaceOption.toFile(
            null, Type.RESOURCE, source, resourceLoader));
  }

  /**
   * Creates a new file replace PaxExam option.
   *
   * @param configurationFilePath the configuration file path to replace
   * @param source the source where to get the content
   */
  public KarafDistributionConfigurationFileReplaceOption(
      String configurationFilePath, File source) {
    super(configurationFilePath, source);
  }

  protected static File toFile(
      @Nullable PaxExamInterpolator interpolator,
      Type type,
      String source,
      @Nullable ResourceLoader resourceLoader)
      throws IOException {
    switch (type) {
      case URL:
        return KarafDistributionConfigurationFileReplaceOption.fromUrlToFile(source);
      case CONTENT:
        if (interpolator == null) {
          throw new IllegalArgumentException("must provide an interpolator to load content");
        }
        return KarafDistributionConfigurationFileReplaceOption.fromContentToFile(
            interpolator, source);
      case RESOURCE:
        if (resourceLoader == null) {
          throw new IllegalArgumentException("must provide a resource loader to load resources");
        }
        return KarafDistributionConfigurationFileReplaceOption.fromResourceToFile(
            source, resourceLoader);
      case FILE:
      default:
        return new File(FilenameUtils.separatorsToSystem(source));
    }
  }

  protected static File fromUrlToFile(String url) throws IOException {
    try (final InputStream is = new URL(url).openStream()) {
      return KarafDistributionConfigurationFileReplaceOption.fromStreamToFile(is);
    }
  }

  @SuppressWarnings({
    "squid:S4042" /* deleting a temp file and we don't care if it fails */,
    "squid:S899" /* deleting a temp file and we don't care if it fails */
  })
  protected static File fromStreamToFile(InputStream is) throws IOException {
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

  protected static File fromResourceToFile(String resource, ResourceLoader resourceLoader)
      throws IOException {
    try (final InputStream is = resourceLoader.getResourceAsStream(resource)) {
      if (is == null) {
        throw new IOException(
            "unable to locate resource "
                + resource
                + " in "
                + resourceLoader.getLocationClass().getName());
      }
      return KarafDistributionConfigurationFileReplaceOption.fromStreamToFile(is);
    }
  }

  protected static File fromContentToFile(PaxExamInterpolator interpolator, String content)
      throws IOException {
    try (final InputStream is =
        new ByteArrayInputStream(
            StringUtils.appendIfMissing(content, interpolator.getLineSeparator())
                .getBytes("UTF-8"))) {
      return KarafDistributionConfigurationFileReplaceOption.fromStreamToFile(is);
    }
  }
}
