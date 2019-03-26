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
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.codice.dominion.options.SourceType;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileReplacementOption;

/**
 * Provides an extension to PaxExam's KarafDistributionConfigurationFileReplacementOption which
 * supports the source being provided via a file, url, hard-coded content, or a resource.
 */
public class KarafDistributionConfigurationFileReplaceOption
    extends KarafDistributionConfigurationFileReplacementOption {

  /**
   * Creates a new file replace PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve line separator
   * @param configurationFilePath the configuration file path to replace
   * @param type the type of the source (any except for {@link SourceType#RESOURCE})
   * @param source the source where to get the content
   * @throws IOException if an I/O error occurs while retrieving/creating the source
   */
  public KarafDistributionConfigurationFileReplaceOption(
      PaxExamInterpolator interpolator,
      String configurationFilePath,
      SourceType type,
      String source)
      throws IOException {
    this(configurationFilePath, type.toFile(source, interpolator, null));
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
        SourceType.CONTENT,
        Stream.of(content)
            .map(c -> StringUtils.appendIfMissing(c, interpolator.getLineSeparator()))
            .collect(Collectors.joining()));
  }

  /**
   * Creates a new file replace PaxExam option.
   *
   * @param configurationFilePath the configuration file path to replace
   * @param resource the source where to get the content
   * @param resourceLoader the resource loader to use if required
   * @throws IOException if an I/O error occurs while retrieving/creating the resource
   */
  public KarafDistributionConfigurationFileReplaceOption(
      String configurationFilePath, String resource, ResourceLoader resourceLoader)
      throws IOException {
    this(configurationFilePath, SourceType.RESOURCE.toFile(resource, null, resourceLoader));
  }

  /**
   * Creates a new file replace PaxExam option.
   *
   * @param configurationFilePath the configuration file path to replace
   * @param source the source where to get the content or <code>null</code> if there is no source
   */
  public KarafDistributionConfigurationFileReplaceOption(
      String configurationFilePath, @Nullable File source) {
    super(configurationFilePath, source);
  }

  /**
   * Creates a new file replace PaxExam option.
   *
   * @param configurationFilePath the configuration file path to replace
   * @param artifact the maven artifact url where to get the content
   * @param resourceLoader the resource loader to use if required
   * @throws IOException if an I/O error occurs while retrieving/creating the resource
   */
  public KarafDistributionConfigurationFileReplaceOption(
      String configurationFilePath, MavenUrl artifact, ResourceLoader resourceLoader)
      throws IOException {
    super(configurationFilePath, SourceType.fromArtifactToFile(artifact, resourceLoader));
  }

  /**
   * Creates a new file replace PaxExam option with no source. The subclass would be expected to
   * override the {@link #getSource} method in order to provide the file to replace the original
   * with.
   *
   * @param configurationFilePath the configuration file path to replace
   */
  protected KarafDistributionConfigurationFileReplaceOption(String configurationFilePath) {
    this(configurationFilePath, null);
  }
}
