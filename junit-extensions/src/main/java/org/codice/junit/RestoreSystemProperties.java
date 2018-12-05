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
 * The <code>RestoreSystemProperties</code> annotation can be used in conjunction with the {@link
 * org.codice.junit.MethodRuleAnnotationRunner} JUnit test runner or the {@link
 * org.codice.junit.rules.MethodRuleAnnotationProcessor} JUnit method rule to restore any changes
 * made to system properties by a given test method.
 *
 * <p>Applying this annotation to a JUnit test class has the same effect as applying it to all its
 * test methods.
 */
@ExtensionMethodRuleAnnotation(org.codice.junit.rules.RestoreSystemProperties.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RestoreSystemProperties {}
