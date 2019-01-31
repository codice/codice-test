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
package org.codice.dominion.interpolate;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to indicate a <code>String</code> or <code>String[]</code> should be
 * automatically interpolated.
 *
 * <p>It can be used in tests running inside the container by annotating public static <code>String
 * </code> or <code>String[]</code> fields of a test class at which point, the interpolation will
 * occur before any tests or {@link org.junit.BeforeClass}, {@link org.junit.Before}, {@link
 * org.junit.AfterClass}, or {@link org.junit.After} methods are executed.
 *
 * <p>It can also be used on <code>String</code> or <code>String[]</code> methods of annotations
 * annotated with the {@link org.codice.dominion.options.Option.Annotation} or {@link
 * org.codice.dominion.conditions.Condition.Annotation} meta annotations to have PaxExam
 * automatically interpolate the result of those methods before the corresponding extensions are
 * called inside PaxExam driver.
 *
 * <p>Interpolation works by replacing instances of <code>"{expression}"</code> as long as they are
 * not preceded with a <code>$</code>. Where <code>expression</code> can be:
 *
 * <ul>
 *   <li><code>"{test.id}"</code> which represents the unique test run id (will be different each
 *       time execution the test is started)
 *   <li><code>"{container.name}"</code> which represents the current container name
 *   <li><code>"{test-classes.path}</code> which represents an absolute path for the location where
 *       test classes and resources are laid down by Maven.
 *   <li><code>"{test-classes.url}</code> which represents a url for the location where test classes
 *       and resources are laid down by Maven.
 *   <li><code>"{classes.path}</code> which represents a n absolute path for the location where main
 *       classes and resources are laid down by Maven.
 *   <li><code>"{classes.url}</code> which represents a url for the location where main classes and
 *       resources are laid down by Maven.
 *   <li><code>"{/}</code> which represents the system default system file name separator (see
 *       {@link java.io.File#separator}).
 *   <li>a system property key. When used inside PaxExam driver, the system property must be defined
 *       in PaxExam. When used in PaxExam container, the system property must be defined using
 *       PaxExam options.
 *   <li><code>"{port.<i>name</i>}"</code>. Where <code>name</code> is a unique name for a free port
 *       number to reserve. The interpolation will result in the same free reserved port whether the
 *       interpolation happens inside PaxExam driver or container.
 *   <li><code>"{<i>condition</i>?<i>then</i>:<i>else</i>}"</code> where <code>condition</code> is
 *       evaluated as a boolean condition which must be equal to <code>"true"</code> (case
 *       insensitive) to result in the <code>then</code> string; otherwise it results in the <code>
 *       false</code> string. Since interpolation is recursive, the <code>condition</code>, <code>
 *       then</code>, or <code>else</code> strings will themselves be interpolated using the same
 *       rules defined here.
 * </ul>
 *
 * <p>Some container specific Dominion driver will add additional support to the standard
 * interpolation. Please refer to the corresponding subclasses of {@link Interpolator} for
 * additional details.
 *
 * <p>Interpolation is recursive. For example, interpolating <code>"{test-classes.{what}}"</code>
 * would first interpolate <code>"{what}"</code> to its system property value (e.g. <code>"url"
 * </code>) and then <code>"{test-classes.{what}}"</code> would result in <code>"{test-classes.url}"
 * </code> and be interpolated accordingly.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Interpolate {}
