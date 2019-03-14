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
package org.codice.maven;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Annotation used to specify a maven URL reference. */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface MavenUrl {
  /**
   * Constant to use when the actual version, type, and/or classifier of the referenced artifact
   * should be retrieved from the maven project's <code>dependencies.properties</code> file where
   * the annotation is used. It does require the project to use the depends-maven-plugin plugin in
   * order for the file to be generated and added to the jar/bundle artifact.
   */
  public static final String AS_IN_PROJECT = "_as_in_project_";

  /**
   * Constant to use when the group id, artifact id, or version of the current project's artifact
   * should be retrieved from the maven project's <code>dependencies.properties</code> file where
   * the annotation is used. It does require the project to use the depends-maven-plugin plugin in
   * order for the file to be generated and added to the jar/bundle artifact.
   */
  public static final String AS_PROJECT = "_as_project_";

  /**
   * Specifies the maven group id.
   *
   * @return the maven group id
   */
  String groupId();

  /**
   * Specifies the maven artifact id.
   *
   * @return the marven artifact id
   */
  String artifactId();

  /**
   * Specifies the optional maven artifact version.
   *
   * @return the optional marven artifact version
   */
  String version() default "";

  /**
   * Specifies the maven artifact type.
   *
   * @return the marven artifact type
   */
  String type() default "";

  /**
   * Specifies the maven artifact classifier.
   *
   * @return the marven artifact classifier
   */
  String classifier() default "";
}
