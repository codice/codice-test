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
package org.codice.pax.exam.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.codice.junit.ExtensionMethodRuleAnnotation;
import org.codice.junit.rules.MethodRuleAnnotationProcessor;

/**
 * The <code>ServiceAdmin</code> annotation can be used in conjunction with the {@link
 * org.codice.junit.MethodRuleAnnotationRunner} JUnit test runner, the {@link
 * MethodRuleAnnotationProcessor} JUnit method rule, or any Dominion driver to provide support for
 * restoring all repositories, features, and bundles after each tests to their initial state as
 * obtained before the test started. It also support starting up features based on provided
 * annotations and ensuring everything is ready before proceeding with a test.
 */
@ExtensionMethodRuleAnnotation(org.codice.pax.exam.junit.rules.ServiceAdmin.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface ServiceAdmin {
  /**
   * Default amount of milliseconds to wait for repositories, features, and bundles to stabilize
   * before a test start.
   */
  public static final String EXAM_SERVICE_STABILIZE_TIMEOUT_KEY =
      "pax.exam.service.stabilize.timeout";

  /**
   * Default amount of milliseconds to wait for repositories, features, and bundles to stabilize
   * before a test start.
   */
  public static final long EXAM_SERVICE_STABILIZE_TIMEOUT_DEFAULT = TimeUnit.MINUTES.toMillis(10L);

  /**
   * Specifies the maximum number of milliseconds to wait for the services. Defaults to an amount of
   * time defined by the {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_KEY} system
   * property (defaults to {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_DEFAULT}
   * milliseconds if not defined).
   *
   * @return the maximum number of milliseconds to wait for the service
   */
  long timeout() default -1L;

  /**
   * Specifies the maximum number of milliseconds to wait for repositories, features, and bundles to
   * stabilize before a test starts. Defaults to an amount of time defined by the {@link
   * #EXAM_SERVICE_STABILIZE_TIMEOUT_KEY} system property (defaults to {@link
   * #EXAM_SERVICE_STABILIZE_TIMEOUT_DEFAULT} milliseconds if not defined).
   *
   * @return the maximum number of milliseconds to wait for repositories, features, and bundles to
   *     stabilize
   */
  long stabilizeTimeout() default -1L;
}
