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
import org.codice.junit.ExtensionMethodRuleAnnotation;

/**
 * The <code>ConfigurationAdmin</code> annotation can be used in conjunction with the {@link
 * org.codice.junit.MethodRuleAnnotationRunner} JUnit test runner, the {@link
 * org.codice.junit.rules.MethodRuleAnnotationProcessor} JUnit method rule, or any Dominion driver
 * to provide support for restoring all configurations objects after each tests to their initial
 * state and to pre-initialize configuration objects based on provided annotations (see the {@link
 * org.codice.pax.exam.config.Configuration} class for a set of available annotations).
 */
@ExtensionMethodRuleAnnotation(org.codice.pax.exam.junit.rules.ConfigurationAdmin.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface ConfigurationAdmin {
  /**
   * Specifies the maximum number of milliseconds to wait for the service. Defaults to an amount of
   * time defined by the {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_KEY} system
   * properties (defaults to {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_DEFAULT}
   * milliseconds if not defined).
   *
   * @return the maximum number of milliseconds to wait for the service
   */
  long timeout() default -1L;
}
