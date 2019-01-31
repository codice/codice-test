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
package org.codice.dominion;

import java.util.ServiceLoader;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.Sortable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The parameterized test runner to use to run with the Dominion framework.
 *
 * <p>This test runner actually delegates to a Dominion driver implementation which is itself a
 * JUnit parameterized test runner.
 *
 * <p>Dominion discovers which driver to use by locating a registered {@link Factory} service on the
 * classpath using Java's {@link ServiceLoader} class. It is possible to override this search by
 * defining the system property {@link #DRIVER_FACTORY_PROPERTY} with the class name of a {@link
 * Factory}.
 *
 * <p>Each driver should provide support for specification of JUnit method rules via the
 * meta-annotation {@link org.codice.junit.ExtensionMethodRuleAnnotation}.
 */
public class DominionParameterized extends Dominion implements Filterable, Sortable {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionParameterized.class);

  /** The type of driver this is. */
  public static final String TYPE = "Parameterized";

  public DominionParameterized(Class<?> testClass) {
    super(testClass, Dominion.newDriver(testClass, DominionParameterized.TYPE));
    LOGGER.debug("DominionParameterized({})", testClass.getName());
  }

  @Override
  public String toString() {
    return "DominionParameterized[" + testClass.getName() + ", " + delegate + "]";
  }
}
