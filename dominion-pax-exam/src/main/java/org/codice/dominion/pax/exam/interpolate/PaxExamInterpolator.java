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
package org.codice.dominion.pax.exam.interpolate;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.codice.dominion.interpolate.ContainerNotStagedException;
import org.codice.dominion.interpolate.Interpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interpolation service specific to PaxExam.
 *
 * <p>Additional supported <code>expression</code> are be:
 *
 * <ul>
 *   <li><code>"{karaf.home}"</code>
 *   <li><code>"{karaf.bin}"</code>
 *   <li><code>"{karaf.data}"</code>
 *   <li><code>"{karaf.etc}"</code>
 * </ul>
 *
 * <p>All <code>"{karaf.XXXX}"</code> expressions represents the absolute path to the corresponding
 * location and are dependent on PaxExam having first staged the container. If an attempt is made to
 * reference these before that time, a {@link
 * org.codice.dominion.interpolate.ContainerNotStagedException} will be thrown out of the
 * interpolation.
 */
public class PaxExamInterpolator extends Interpolator {
  private static final Logger LOGGER = LoggerFactory.getLogger(PaxExamInterpolator.class);

  protected volatile Path karafHome = null;
  protected volatile Path karafBin = null;
  protected volatile Path karafData = null;
  protected volatile Path karafEtc = null;

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param testClass the current test class
   * @param id a unique id for this the corresponding test run
   */
  public PaxExamInterpolator(Class<?> testClass, String id) {
    this(testClass, id, PaxExamInterpolator.DEFAULT_CONTAINER);
  }

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param testClass the current test class
   * @param id a unique id for this the corresponding test run
   * @param container the name of the container for which to create an interpolator
   */
  public PaxExamInterpolator(Class<?> testClass, String id, String container) {
    super(testClass, id, container);
    LOGGER.debug("PaxExamInterpolator({}, {}, {})", testClass, id, container);
  }

  /**
   * Initializes a new interpolator inside a container.
   *
   * <p>The container name and the port information will be retrieved from system properties.
   *
   * @param testClass the current test class
   */
  public PaxExamInterpolator(Class<?> testClass) {
    super(testClass, PaxExamInterpolator.getFileFromSystemProperties());
    this.karafHome = Paths.get(initFromReplacements("karaf.home"));
    LOGGER.debug(
        "PaxExamInterpolator({}, {}, {}) - karaf.home = {}", testClass, id, container, karafHome);
    this.karafBin = Paths.get(initFromReplacements("karaf.bin"));
    this.karafData = Paths.get(initFromReplacements("karaf.data"));
    this.karafEtc = Paths.get(initFromReplacements("karaf.etc"));
  }

  /**
   * Initializes a new interpolator with all information from the provided interpolator.
   *
   * @param interpolator the interpolator to proxy
   */
  protected PaxExamInterpolator(PaxExamInterpolator interpolator) {
    super(interpolator);
    LOGGER.debug("PaxExamInterpolator({})", interpolator);
    this.karafHome = interpolator.karafHome;
    this.karafBin = interpolator.karafBin;
    this.karafData = interpolator.karafData;
    this.karafEtc = interpolator.karafEtc;
  }

  /**
   * Gets the location of <code>"{karaf.home}"</code>.
   *
   * @return the path to <code>"{karaf.home}"</code> if known
   * @throws ContainerNotStagedException if the container was not staged yet
   */
  @Nullable
  public Path getKarafHome() {
    final Path path = karafHome;

    if (path == null) {
      throw new ContainerNotStagedException("container was not staged yet");
    }
    return path;
  }

  /**
   * Gets the location of <code>"{karaf.bin}"</code>.
   *
   * @return the path to <code>"{karaf.bin}"</code> if known
   * @throws ContainerNotStagedException if the container was not staged yet
   */
  public Path getKarafBin() {
    getKarafHome();
    return karafBin;
  }

  /**
   * Gets the location of <code>"{karaf.data}"</code>.
   *
   * @return the path to <code>"{karaf.data}"</code> if known
   * @throws ContainerNotStagedException if the container was not staged yet
   */
  public Path getKarafData() {
    getKarafHome();
    return karafData;
  }

  /**
   * Gets the location of <code>"{karaf.etc}"</code>.
   *
   * @return the path to <code>"{karaf.etc}"</code> if known
   * @throws ContainerNotStagedException if the container was not staged yet
   */
  public Path getKarafEtc() {
    getKarafHome();
    return karafEtc;
  }

  private static File getFileFromSystemProperties() {
    final String filename = System.getProperty(Interpolator.INFO_FILE_KEY);

    if (filename == null) {
      throw new ContainerNotStagedException(
          "Unable to retrieved interpolation file from system property: "
              + Interpolator.INFO_FILE_KEY);
    }
    return new File(filename);
  }
}
