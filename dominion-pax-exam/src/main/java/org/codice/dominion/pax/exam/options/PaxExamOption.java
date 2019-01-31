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
package org.codice.dominion.pax.exam.options;

import org.codice.dominion.resources.ResourceLoader;

/**
 * This class provides configuration support for PaxExam containers. It is solely used for scoping.
 */
public class PaxExamOption {
  /**
   * Extension point for user-defined option annotations. This class will be invoked whenever
   * options are retrieved to stage a pax exam container for a given test.
   *
   * @param <T> the type of annotation this extension is used with
   */
  @SuppressWarnings("squid:S2176" /* scoping is used to uniquely identify the interface */)
  public interface Extension<T extends java.lang.annotation.Annotation>
      extends org.codice.dominion.options.Option.Extension {
    /**
     * Called to provide options to be combined together with all other recursively referenced
     * {@link org.codice.dominion.options.Option.Extension} provided options to configure a single
     * PaxExam container.
     *
     * @param annotation the annotation where this extension was specified
     * @param testClass the class for test for which a test container will be staged
     * @param resourceLoader a resource loader capable of loading resources associated with where
     *     the annotation is used
     * @return <code>true</code> if the annotated option should be considered before staging the
     *     PaxExam test container
     * @return an array of PaxExam options to be combined together when staging the test container
     *     or <code>null</code> if no options are available
     * @throws Throwable if unable to generate the options for this extension
     */
    @SuppressWarnings("squid:S00112" /* the framework will handle any exceptions thrown out */)
    public org.ops4j.pax.exam.Option[] options(
        T annotation, Class<?> testClass, ResourceLoader resourceLoader) throws Throwable;
  }

  private PaxExamOption() {}
}
