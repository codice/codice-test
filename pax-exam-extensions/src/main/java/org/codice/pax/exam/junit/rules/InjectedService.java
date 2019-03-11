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
package org.codice.pax.exam.junit.rules;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.ops4j.pax.exam.Constants;
import org.ops4j.pax.exam.TestContainerException;
import org.ops4j.pax.swissbox.tracker.ServiceLookup;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;

/**
 * This JUnit method rule behaves similarly to Pax Exam support for the <code>@Inject</code>
 * annotation except that it allows subclasses to reference the injected service as part of their
 * implementation.
 *
 * @param <S> the type of the service to be injected
 */
public abstract class InjectedService<S> implements MethodRule {
  private final Class<S> serviceClass;
  private final String filter;
  private final long timeout;
  private BundleContext bundleContext = null;
  private S service = null;

  /**
   * Injects a service of the given type by looking up in the OSGi service registry while waiting
   * for it for a default amount of time defined by the {@link Constants#EXAM_SERVICE_TIMEOUT_KEY}
   * system properties (defaults to {@link Constants#EXAM_SERVICE_TIMEOUT_DEFAULT} milliseconds).
   *
   * @param serviceClass the class of service to inject
   */
  protected InjectedService(Class<S> serviceClass) {
    this(serviceClass, null, -1L);
  }

  /**
   * Injects a service of the given type by looking up in the OSGi service registry while waiting
   * for it for a default amount of time defined by the {@link Constants#EXAM_SERVICE_TIMEOUT_KEY}
   * system properties (defaults to {@link Constants#EXAM_SERVICE_TIMEOUT_DEFAULT} milliseconds).
   * The specified filter can be used to restrict the set of eligible services.
   *
   * <p><i>Note:</i> The service class clause is automatically added to the filter (e.g.
   * (objectClass=com.example.CoolService)).
   *
   * @param serviceClass the class of service to inject
   * @param filter an OSGi LDAP filter syntax to restrict the set of eligible services
   */
  protected InjectedService(Class<S> serviceClass, String filter) {
    this(serviceClass, filter, -1L);
  }

  /**
   * Injects the specified service while waiting for it for the specified amount of time.
   *
   * @param serviceClass the class of service to inject
   * @param timeout the maximum number of milliseconds to wait for the service or <code>-1</code> to
   *     use the {@link Constants#EXAM_SERVICE_TIMEOUT_KEY} system property value as described in
   *     {@link #InjectedService(Class)}
   */
  protected InjectedService(Class<S> serviceClass, long timeout) {
    this(serviceClass, null, timeout);
  }

  /**
   * Injects a service of the given type by looking up in the OSGi service registry while waiting
   * for it for a specified amount of time. The specified filter can be used to restrict the set of
   * eligible services.
   *
   * <p><i>Note:</i> The service class clause is automatically added to the filter (e.g.
   * (objectClass=com.example.CoolService)).
   *
   * @param serviceClass the class of service to inject
   * @param filter an OSGi LDAP filter syntax to restrict the set of eligible services
   * @param timeout the maximum number of milliseconds to wait for the service or <code>-1</code> to
   *     use the {@link Constants#EXAM_SERVICE_TIMEOUT_KEY} system property value as described in
   *     {@link #InjectedService(Class String)}
   */
  protected InjectedService(Class<S> serviceClass, String filter, long timeout) {
    this.serviceClass = serviceClass;
    this.filter = filter;
    this.timeout =
        (timeout != -1L)
            ? timeout
            : Integer.parseInt(
                System.getProperty(
                    Constants.EXAM_SERVICE_TIMEOUT_KEY, Constants.EXAM_SERVICE_TIMEOUT_DEFAULT));
  }

  /**
   * Gets the bundle context associated with this object.
   *
   * @return the bundle context associated with this object
   * @throws TestContainerException if the service has not yet been injected
   */
  public BundleContext getBundleContext() {
    if (bundleContext == null) {
      throw new TestContainerException(
          "service "
              + serviceClass.getName()
              + " has not been injected; bundle context is not available");
    }
    return bundleContext;
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod method, Object target) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        injectService(target.getClass());
        statement.evaluate();
      }
    };
  }

  /**
   * Gets the injected service.
   *
   * @return the injected service
   * @throws TestContainerException if the service has not yet been injected
   */
  protected S getService() {
    if (service == null) {
      throw new TestContainerException(
          "service " + serviceClass.getName() + " has not been injected");
    }
    return service;
  }

  protected void injectService(Class<?> targetClass) {
    if (service != null) {
      return;
    }
    // retrieve bundle context just before calling getService() to avoid that the bundle restarts
    // in between
    final long end = System.currentTimeMillis() + timeout;
    this.bundleContext = getBundleContext(targetClass, timeout);
    this.service =
        (BundleContext.class == serviceClass)
            ? (S) bundleContext
            : ServiceLookup.getService(
                bundleContext,
                serviceClass,
                Math.max(1L, end - System.currentTimeMillis()),
                filter);
  }

  private BundleContext getBundleContext(Class<?> targetClass, long timeout) {
    try {
      final BundleReference bundleRef = BundleReference.class.cast(targetClass.getClassLoader());
      final Bundle bundle = bundleRef.getBundle();

      return getBundleContext(bundle, timeout);
    } catch (ClassCastException exc) {
      throw new TestContainerException(
          "class " + targetClass.getName() + " is not loaded from an OSGi bundle");
    }
  }

  /**
   * Retrieve bundle context from given bundle. If the bundle is being restarted the bundle context
   * can be <code>null</code> for some time.
   *
   * @param bundle the bundle from which to retrieve the context
   * @param timeout the maximum of time in milliseconds to wait for the bundle context
   * @return the corresponding bundle context
   * @throws TestContainerException if bundleContext is <code>null</code> after timeout
   */
  private BundleContext getBundleContext(Bundle bundle, long timeout) {
    final long end = System.currentTimeMillis() + timeout;
    boolean interrupted = false;

    try {
      while (true) {
        final BundleContext bc = bundle.getBundleContext();

        if (bc != null) {
          return bc;
        }
        if (System.currentTimeMillis() >= end) {
          throw new TestContainerException(
              "unable to retrieve bundle context from bundle " + bundle);
        }
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
