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

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param uuid a unique id for this the corresponding test run
   */
  public PaxExamInterpolator(String uuid) {
    this(uuid, PaxExamInterpolator.DEFAULT_CONTAINER);
  }

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param uuid a unique id for this the corresponding test run
   * @param container the name of the container for which to create an interpolator
   */
  public PaxExamInterpolator(String uuid, String container) {
    super(uuid, container);
    LOGGER.debug("PaxExamInterpolator({}, {})", uuid, container);
  }

  /**
   * Initializes a new interpolator inside a container.
   *
   * <p>The container name and the port information will be retrieved from system properties.
   */
  public PaxExamInterpolator() {
    super(System.getProperties());
    final String home = initFromReplacements("karaf.home");

    LOGGER.debug("PaxExamInterpolator({}, {}) - karaf.home = {}", id, container, home);
    initFromReplacements("karaf.bin");
    initFromReplacements("karaf.data");
    initFromReplacements("karaf.etc");
  }
}
