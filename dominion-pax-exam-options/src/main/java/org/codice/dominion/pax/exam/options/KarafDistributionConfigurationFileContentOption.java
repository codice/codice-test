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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.codice.dominion.DominionException;
import org.codice.dominion.options.Options.Location;
import org.codice.dominion.options.SourceType;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;

/**
 * Provides an extension to PaxExam's file options which supports adding content to an existing
 * file. This version supports multiple subsequent replacements of the same file without worrying
 * for the key as does PaxExam's KarafDistributionConfigurationFileReplacementOption.
 */
public class KarafDistributionConfigurationFileContentOption
    extends KarafDistributionConfigurationFilePostOption {
  protected final PaxExamInterpolator interpolator;
  @Nullable protected final Location location;

  protected final File source;

  /**
   * Creates a new file content PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param location the location in the file where to add the content
   * @param configurationFilePath the configuration file path to add content to
   * @param type the type of the source (any except for {@link SourceType#RESOURCE})
   * @param source the source where to get the content
   * @throws IllegalArgumentException if <code>type</code> is {@link SourceType#RESOURCE}
   * @throws IOException if an I/O error occurs while retrieving/creating the source
   */
  public KarafDistributionConfigurationFileContentOption(
      PaxExamInterpolator interpolator,
      Location location,
      String configurationFilePath,
      SourceType type,
      String source)
      throws IOException {
    this(interpolator, location, configurationFilePath, type.toFile(source, interpolator, null));
  }

  /**
   * Creates a new file content PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param location the location in the file where to add the content
   * @param configurationFilePath the configuration file path to add content to
   * @param content the source content where each string represents a separate line to be added
   * @throws IOException if an I/O error occurs while creating the source
   */
  public KarafDistributionConfigurationFileContentOption(
      PaxExamInterpolator interpolator,
      Location location,
      String configurationFilePath,
      String... content)
      throws IOException {
    this(
        interpolator,
        location,
        configurationFilePath,
        SourceType.CONTENT,
        Stream.of(content)
            .map(c -> StringUtils.appendIfMissing(c, interpolator.getLineSeparator()))
            .collect(Collectors.joining()));
  }

  /**
   * Creates a new file content PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param location the location in the file where to add the content
   * @param configurationFilePath the configuration file path to add content to
   * @param resource the resource where to get the content
   * @param resourceLoader the resource loader to use if required
   * @throws IOException if an I/O error occurs while retrieving the resource
   */
  public KarafDistributionConfigurationFileContentOption(
      PaxExamInterpolator interpolator,
      Location location,
      String configurationFilePath,
      String resource,
      ResourceLoader resourceLoader)
      throws IOException {
    this(
        interpolator,
        location,
        configurationFilePath,
        SourceType.RESOURCE.toFile(resource, null, resourceLoader));
  }

  /**
   * Creates a new file content PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param configurationFilePath the configuration file path to replace
   * @param location the location in the file where to add the content
   * @param artfact the maven artifact url where to get the content
   * @param resourceLoader the resource loader to use if required
   * @throws IOException if an I/O error occurs while retrieving/creating the resource
   */
  public KarafDistributionConfigurationFileContentOption(
      PaxExamInterpolator interpolator,
      Location location,
      String configurationFilePath,
      MavenUrl artfact,
      ResourceLoader resourceLoader)
      throws IOException {
    this(
        interpolator,
        location,
        configurationFilePath,
        SourceType.fromArtifactToFile(artfact, resourceLoader));
  }

  /**
   * Creates a new file content PaxExam option with the specified source.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param location the location in the file where to add the content
   * @param configurationFilePath the configuration file path to replace
   * @param source the source where to get the content
   */
  public KarafDistributionConfigurationFileContentOption(
      PaxExamInterpolator interpolator,
      Location location,
      String configurationFilePath,
      File source) {
    super(configurationFilePath);
    this.interpolator = interpolator;
    this.location = location;
    this.source = source;
  }

  /**
   * Creates a new file content PaxExam option with no source. The subclass would be expected to
   * override the {@link #getSource(File)} method in order to retrieve the source file to have its
   * content used to replace the original file.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param configurationFilePath the configuration file path to replace
   */
  protected KarafDistributionConfigurationFileContentOption(
      PaxExamInterpolator interpolator, String configurationFilePath) {
    super(configurationFilePath);
    this.interpolator = interpolator;
    this.location = null;
    this.source = null;
  }

  /**
   * Gets the source for the content of the file to be copied over.
   *
   * @return the surce for the content of the file to be copied over
   */
  public File getSource() {
    // find the original file so we can getSource it
    final String path = getConfigurationFilePath();
    // see KarafTestContainer.updateUserSetProperties() for logic on how to find the location
    // of a file
    File original =
        KarafDistributionConfigurationFileContentOption.toFile(interpolator.getKarafHome(), path);

    if (!original.exists()) {
      File customFile = null;

      if (path.startsWith("data/")) {
        customFile =
            KarafDistributionConfigurationFileContentOption.toFile(
                interpolator.getKarafData(), path.substring(5));
      } else if (path.startsWith("etc/")) {
        customFile =
            KarafDistributionConfigurationFileContentOption.toFile(
                interpolator.getKarafEtc(), path.substring(4));
      }
      if ((customFile != null) && customFile.exists()) {
        original = customFile;
      }
    }
    try {
      return getSource(original);
    } catch (DominionException e) {
      throw e;
    } catch (Exception e) {
      throw new DominionException("error occurred while updating file: " + original, e);
    }
  }

  /**
   * Called whenever PaxExam requires the resulting file to copy from when replacing the original
   * one.
   *
   * @param original the original file being replaced (it might not exist)
   * @return a file containing the content that should be copied over the original file
   * @throws Exception if an error occurred while putting together the source file
   */
  @SuppressWarnings({
    "squid:S2095" /* closing of streams is handled by FileUtils.copy */,
    "squid:S00112" /* intended to allow subclasses to throw anything out */
  })
  protected File getSource(File original) throws Exception {
    final File src = this.source;

    if (!original.exists()) { // nothing to append/prepend to
      return src;
    }
    File tmp = null;

    try {
      tmp =
          Files.createTempFile(
                  KarafDistributionConfigurationFileContentOption.class.getName(), ".tmp")
              .toFile();
      tmp.deleteOnExit();
      final InputStream is;

      switch (location) {
        case PREPEND:
          is = new SequenceInputStream(new FileInputStream(source), new FileInputStream(original));
          break;
        case APPEND:
        default:
          is = new SequenceInputStream(new FileInputStream(original), new FileInputStream(source));
          break;
      }
      FileUtils.copyInputStreamToFile(is, tmp);
    } catch (IOException e) {
      FileUtils.deleteQuietly(tmp);
      throw e;
    }
    return tmp;
  }

  /**
   * Gets a path for the given location relative to the specified base directory.
   *
   * @param base the base directory
   * @param location the relative location (any initial file separator is first stripped)
   * @return a corresponding path
   */
  public static File toFile(Path base, String location) {
    if (location.startsWith("/")) {
      return base.resolve(location.substring(1)).toFile();
    }
    return base.resolve(location).toFile();
  }
}
