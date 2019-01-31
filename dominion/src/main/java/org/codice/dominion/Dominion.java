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

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.ServiceLoader;
import javax.annotation.Nullable;
import org.codice.test.commons.MavenUtils;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The test runner to use to run with the Dominion framework.
 *
 * <p>This test runner actually delegates to a Dominion driver implementation which is itself a
 * JUnit test runner.
 *
 * <p>Dominion discovers which driver to use by locating a registered {@link Factory} service on the
 * classpath using Java's {@link ServiceLoader} class. It is possible to override this search by
 * defining the system property {@link #DRIVER_FACTORY_PROPERTY} with the class name of a {@link
 * Factory}.
 *
 * <p>Each driver should provide support for specification of JUnit method rules via the
 * meta-annotation {@link org.codice.junit.ExtensionMethodRuleAnnotation}.
 */
public class Dominion extends Runner implements Filterable, Sortable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Dominion.class);

  /** The type of driver this is. */
  public static final String TYPE = "";

  /** System property to use for overriding the driver search with a specific factory. */
  public static final String DRIVER_FACTORY_PROPERTY = "dominion.driver.factory";

  protected final Class<?> testClass;
  protected final Runner delegate;
  private final Filterable filterable;
  private final Sortable sortable;

  public Dominion(Class<?> testClass) {
    this(testClass, Dominion.newDriver(testClass, ""));
    LOGGER.debug("Dominion({})", testClass.getName());
  }

  protected Dominion(Class<?> testClass, Runner delegate) {
    LOGGER.debug("Dominion({}, {})", testClass.getName(), delegate);
    this.testClass = testClass;
    this.delegate = delegate;
    if (delegate instanceof Filterable) {
      this.filterable = (Filterable) delegate;
    } else {
      this.filterable =
          new Filterable() {
            @Override
            public void filter(Filter filter) throws NoTestsRemainException { // nothing to filter
            }
          };
    }
    if (delegate instanceof Sortable) {
      this.sortable = (Sortable) delegate;
    } else {
      this.sortable = sorter -> {};
    }
  }

  @Override
  public Description getDescription() {
    return delegate.getDescription();
  }

  @Override
  public void run(RunNotifier notifier) {
    delegate.run(notifier);
  }

  @Override
  public void filter(Filter filter) throws NoTestsRemainException {
    filterable.filter(filter);
  }

  @Override
  public void sort(Sorter sorter) {
    sortable.sort(sorter);
  }

  @Override
  public String toString() {
    return "Dominion[" + testClass.getName() + ", " + delegate + "]";
  }

  /**
   * Factory which can be registered as a service retrievable via Java's {@link ServiceLoader} to
   * let the Dominion framework know which driver classes to instantiate.
   *
   * <p><i>Note:</i> Defining the {@link #DRIVER_FACTORY_PROPERTY} system property will provide a
   * factory which has priority over those registered via Java's {@link ServiceLoader}.
   *
   * <p>The factory search stops when a registered factory returns a non-<code>null</code> driver
   * class.
   */
  public static interface Factory {
    /**
     * Gets the class implementing the standard Dominion driver.
     *
     * @param type the type of JUnit runner required for the driver (e.g. <code>"Parameterized"
     *     </code> ) or blank for the default type
     * @return the class implementing the specified Dominion driver or <code>null</code> if none
     *     found
     */
    @Nullable
    public Class<? extends Runner> getDriver(String type);
  }

  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  static Runner newDriver(Class<?> testClass, String type) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(
          "Dominion Framework (Version: "
              + MavenUtils.getProjectAttribute(Dominion.class, MavenUtils.VERSION)
              + ") created");
    }
    final String factory = System.getProperty(Dominion.DRIVER_FACTORY_PROPERTY);
    Class<? extends Runner> clazz = null;

    try {
      if (factory != null) {
        clazz = ((Factory) (Class.forName(factory).newInstance())).getDriver(type);
      }
      for (final Iterator<Factory> i = ServiceLoader.load(Factory.class).iterator();
          i.hasNext() && (clazz == null); ) {
        clazz = i.next().getDriver(type);
      }
      if (clazz != null) {
        return clazz.getConstructor(Class.class).newInstance(testClass);
      }
      throw new DominionInitializationException("no driver available");
    } catch (InvocationTargetException e) {
      final Throwable t = e.getTargetException();

      if (t instanceof DominionException) {
        throw (DominionException) t;
      }
      throw new DominionInitializationException(
          "failed to initialize dominion driver: " + clazz.getName(), t);
    } catch (VirtualMachineError | DominionException e) {
      throw e;
    } catch (Throwable t) {
      if (clazz != null) {
        throw new DominionInitializationException(
            "failed to initialize dominion driver: " + clazz.getName(), t);
      }
      throw new DominionInitializationException("failed to locate a dominion driver", t);
    }
  }
}
