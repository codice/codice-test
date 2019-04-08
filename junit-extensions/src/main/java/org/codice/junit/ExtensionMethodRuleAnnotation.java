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
import org.codice.junit.rules.MethodRuleAnnotationProcessor;
import org.junit.rules.MethodRule;

/**
 * Defines a meta annotation which enables the developer to create a new annotation referencing a
 * JUnit method rule that can be automatically plugged into the JUnit test class as long as the test
 * class is running with the {@link MethodRuleAnnotationRunner} or the dominion test runner, or the
 * {@link MethodRuleAnnotationProcessor} method rule has been defined in the test class.
 *
 * <p>It is possible to annotate the test class if the method rule should apply to all tests in that
 * class or a particular test method if the method rule should only apply to that test method.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface ExtensionMethodRuleAnnotation {
  /**
   * Specifies the method rules to be plugged into the JUnit test class. The method rule class must
   * define either a public constructor with a single argument of the same annotation type as the
   * annotation that is annotated with this meta-annotation or a public default constructor.
   * Defining a constructor with the annotation as a parameter allows customization of the method
   * rule using your own annotation.
   *
   * <p>{@link org.codice.junit.rules.SnapshotMethodRule} are supported as well.
   *
   * @return the class for the method rule to be plugged into the JUnit test class
   */
  Class<? extends MethodRule> value();
}
