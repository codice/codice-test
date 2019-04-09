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
package org.codice.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The <code>TestDelimiter</code> annotation can be used in conjunction with the {@link
 * MethodRuleAnnotationRunner} JUnit or Dominion test runner or with the {@link
 * org.codice.junit.rules.MethodRuleAnnotationProcessor} JUnit method rule to delimit each executed
 * tests by logging its start and optionaly its end.
 *
 * <p>Applying this annotation to a JUnit test class has the same effect as applying it to all its
 * test methods.
 */
@ExtensionMethodRuleAnnotation(org.codice.junit.rules.TestDelimiter.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface TestDelimiter {
  /**
   * Specifies whether to log the information on standard output or using slf4j at info level.
   *
   * @return <code>true</code> to log to standard out; <code>false</code> to log via slf4j at info
   *     level
   */
  boolean stdout() default true;

  /**
   * Specifies whether the elapsed time for executing the test should also be logged.
   *
   * @return <code>true</code> to log the elapsed time for executing the test; <code>false</code> to
   *     not log it (the default)
   */
  boolean elapsed() default false;
}
