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
package org.codice.dominion.conditions;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Iterator;
import java.util.ServiceLoader;
import javax.annotation.Nullable;
import org.codice.dominion.Dominion;
import org.codice.dominion.DominionException;
import org.codice.dominion.options.OptionException;
import org.codice.dominion.resources.ResourceLoader;

/**
 * This interface provides conditional support for condition annotations. It is primarily used for
 * scoping.
 */
public class Condition {
  /**
   * Gets a condition extension from a specific subclass for the given condition annotation.
   *
   * <p><i>Note:</i> The implementation provided here will search registered factories until one
   * returns a non-<code>null</code> extension that is an instance of the expected class.
   *
   * @param <E> the type of extension expected
   * @param clazz the specific subclass of condition expected
   * @param annotation the condition annotation for which to create an extension point (this will be
   *     of the annotation class annotated with {@link Annotation}
   * @return the corresponding extension to use
   * @throws ConditionException if a failure occurred while retrieving the extension or if no
   *     registered factories support the specified condition annotation and/or type of extension
   */
  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  public static <E extends Condition.Extension> E getExtension(
      Class<E> clazz, java.lang.annotation.Annotation annotation) {
    try {
      for (final Iterator<Factory> i = ServiceLoader.load(Factory.class).iterator();
          i.hasNext(); ) {
        final Extension<?> extension = i.next().getExtension(annotation);

        if (clazz.isInstance(extension)) {
          return clazz.cast(extension);
        }
      }
      throw new OptionException(
          "unable to find a condition extension for: " + annotation.annotationType());
    } catch (VirtualMachineError | DominionException e) {
      throw e;
    } catch (Throwable t) {
      throw new OptionException(
          "failure retrieving a condition extension for: " + annotation.annotationType(), t);
    }
  }

  /**
   * Defines a meta annotation which enables the developer to create a new condition for option
   * annotations which can be used to configure a test container. A condition must evaluate to
   * <code>true</code> for the annotation to be considered when staging the test container.
   *
   * <p>Before a container is staged and the {@link Dominion} test runner will recursively search
   * all for {@link org.codice.dominion.options.Option.Annotation} associated with the test class.
   * All annotations using this meta-annotation which are also annotated with {@link
   * Condition.Annotation}, will have the specified condition extension instantiated and evaluated.
   * If it returns <code>false
   * </code> the options will not be considered when staging the test container.
   *
   * <p>When multiple conditions are defined, they must all evaluate to true for the option to be
   * considered when staging the test container. Each condition for which no driver-specific
   * extension implementation can be located will be interpreted as <code>true</code>.
   *
   * <p><code>String</code> and <code>String[]</code> methods on these annotation can be annotated
   * with the {@link org.codice.dominion.interpolate.Interpolate} annotation to indicate their
   * value(s) supports interpolation.
   */
  @Target(ElementType.ANNOTATION_TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface Annotation {}

  /**
   * Extension point for user-defined condition annotations.
   *
   * @param <T> the type of annotation this extension is used with
   */
  public interface Extension<T extends java.lang.annotation.Annotation> {
    /**
     * Called to verify if an associated option should be considered before staging the test
     * container.
     *
     * @param annotation the annotation where this extension was specified
     * @param testClass the class for test for which a test container will be staged
     * @param resourceLoader a resource loader capable of loading resources associated with where
     *     the annotation is used
     * @return <code>true</code> if the annotated option should be considered before staging the
     *     PaxExam test container
     * @throws Throwable if unable to instantiate or evaluate the condition
     */
    @SuppressWarnings("squid:S00112" /* the framework will handle any exceptions thrown out */)
    public boolean evaluate(T annotation, Class<?> testClass, ResourceLoader resourceLoader)
        throws Throwable;
  }

  /**
   * Factory which can be registered as a service retrievable via Java's {@link
   * java.util.ServiceLoader} to instantiate extension classes for specific meta-annotations.
   *
   * <p>The factory search stops when a registered factory returns a non-<code>null</code>
   * extension.
   */
  public interface Factory {
    /**
     * Gets an extension implementation point for the given condition annotation.
     *
     * @param annotation the condition annotation for which to create an extension point (this will
     *     of of the annotation class annotated with {@link Annotation}
     * @return the corresponding extension to use or <code>null</code> if this factory does not
     *     support the specified condition annotation
     * @throws ConditionException if a failure occurred while retrieving the extension
     */
    @Nullable
    public Extension getExtension(java.lang.annotation.Annotation annotation);
  }

  private Condition() {}
}
