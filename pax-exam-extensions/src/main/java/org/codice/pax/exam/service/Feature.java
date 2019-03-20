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
package org.codice.pax.exam.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.apache.karaf.features.FeaturesService;
import org.codice.maven.MavenUrl;

/** This interface is defined purely to provide scoping. */
public interface Feature {
  /**
   * This annotation can be used on the test class to apply to all test methods or on specific test
   * methods to indicate that a feature should first be started before the test method is invoked.
   */
  @Target({ElementType.METHOD, ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Repeatable(Features.Start.class)
  @Documented
  public @interface Start {
    /**
     * Specifies the optional maven repository url where the feature is defined.
     *
     * <p><i>Note:</i> If no repository URL is specified, a repository containing the feature will
     * assume to have already been installed. If specified, the repository will be installed if not
     * already installed.
     *
     * @return the optional maven repository url where the feature is defined
     */
    MavenUrl repository() default @MavenUrl(groupId = "", artifactId = "");

    /**
     * Specifies the optional maven repository url where the feature is defined.
     *
     * <p><i>Note:</i> If no repository URL is specified, a repository containing the feature will
     * assume to have already been installed. If specified, the repository will be installed if not
     * already installed.
     *
     * @return the optional maven repository url where the feature is defined
     */
    String repositoryUrl() default "";

    /**
     * Specifies the name of the feature to be started.
     *
     * @return the name of the feature to be started
     */
    String name();

    /**
     * Specifies the optional version of the feature to be started.
     *
     * @return the optional version of the feature to be started or <code>""</code> if unknown
     */
    String version() default "";

    /**
     * Specifies the region for the feature (defaults to {@link FeaturesService#ROOT_REGION}).
     *
     * @return the region for the feature
     */
    String region() default FeaturesService.ROOT_REGION;
  }

  /**
   * This annotation can be used on the test class to apply to all test methods or on specific test
   * methods to indicate that a feature should first be stopped before the test method is invoked.
   */
  @Target({ElementType.METHOD, ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Repeatable(Features.Stop.class)
  @Documented
  public @interface Stop {
    /**
     * Specifies the name of the feature to be stopped.
     *
     * @return the name of the feature to be stopped
     */
    String name();
  }
}
