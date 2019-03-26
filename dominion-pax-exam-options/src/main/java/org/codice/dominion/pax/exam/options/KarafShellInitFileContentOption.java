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

import java.io.IOException;
import org.codice.dominion.options.Options.Location;
import org.codice.dominion.options.SourceType;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.resources.ResourceLoader;

/**
 * Provides an extension to PaxExam's KarafDistributionConfigurationFileReplacementOption which
 * supports appending commands to the <code>etc/shell.init.script</code> file.
 */
public class KarafShellInitFileContentOption
    extends KarafDistributionConfigurationFileContentOption {
  private static final String SHELL_INIT_SCRIPT = "etc/shell.init.script";

  /**
   * Creates a new shell.init.script content PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param type the type of the source (any except for {@link SourceType#RESOURCE})
   * @param source the source where to get the content
   * @throws IllegalArgumentException if <code>type</code> is {@link SourceType#RESOURCE}
   * @throws IOException if an I/O error occurs while retrieving/creating the source
   */
  public KarafShellInitFileContentOption(
      PaxExamInterpolator interpolator, SourceType type, String source) throws IOException {
    super(
        interpolator,
        Location.APPEND,
        KarafShellInitFileContentOption.SHELL_INIT_SCRIPT,
        type,
        source);
  }

  /**
   * Creates a new shell.init.script content PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param content the source content where each string represents a separate line to be added
   * @throws IOException if an I/O error occurs while creating the source
   */
  public KarafShellInitFileContentOption(PaxExamInterpolator interpolator, String... content)
      throws IOException {
    super(
        interpolator, Location.APPEND, KarafShellInitFileContentOption.SHELL_INIT_SCRIPT, content);
  }

  /**
   * Creates a new shell.init.script content PaxExam option.
   *
   * @param interpolator the interpolator from which to retrieve Karaf directory locations
   * @param resourceLoader the resource loader to use if required
   * @throws IOException if an I/O error occurs while retrieving the source
   */
  public KarafShellInitFileContentOption(
      PaxExamInterpolator interpolator, String resource, ResourceLoader resourceLoader)
      throws IOException {
    super(
        interpolator,
        Location.APPEND,
        KarafShellInitFileContentOption.SHELL_INIT_SCRIPT,
        resource,
        resourceLoader);
  }
}
