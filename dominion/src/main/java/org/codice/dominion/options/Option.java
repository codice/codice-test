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
package org.codice.dominion.options;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import javax.annotation.Nullable;
import org.codice.dominion.Dominion;
import org.codice.dominion.DominionException;
import org.codice.dominion.interpolate.Interpolate;

/** This class provides configuration support for containers. It is primarily used for scoping. */
public class Option {
  /**
   * Gets an option extension from a specific subclass for the given option annotation.
   *
   * <p><i>Note:</i> The implementation provided here will search registered factories until one
   * returns a non-<code>null</code> extension that is an instance of the expected class.
   *
   * @param <E> the type of extension expected
   * @param clazz the specific class of option extension expected
   * @param annotation the option annotation for which to create an extension point (this will be of
   *     the annotation class annotated with {@link Annotation})
   * @return the corresponding extension to use or <code>null</code> if none found
   * @throws OptionException if a failure occurred while retrieving the extension
   */
  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  @Nullable
  public static <E extends Extension> E getExtension(
      Class<E> clazz, java.lang.annotation.Annotation annotation) {
    try {
      for (final Iterator<Factory> i = ServiceLoader.load(Factory.class).iterator();
          i.hasNext(); ) {
        final Extension extension = i.next().getExtension(annotation);

        if (clazz.isInstance(extension)) {
          return clazz.cast(extension);
        }
      }
      return null;
    } catch (VirtualMachineError | DominionException e) {
      throw e;
    } catch (Throwable t) {
      throw new OptionException(
          "failure retrieving an option extension for: " + annotation.annotationType(), t);
    }
  }

  /**
   * Gets all option extensions from a specific subclass for the given option annotation.
   *
   * <p><i>Note:</i> The implementation provided here will search registered factories for a given
   * annotation and returns a stream of all extensions that are an instance of the expected class.
   *
   * @param <E> the type of extension expected
   * @param clazz the specific class of option extensions expected
   * @param annotation the option annotation for which to create extension points (this will be of
   *     the annotation class annotated with {@link Annotation})
   * @return the corresponding extensions to use or empty if none found
   * @throws OptionException if a failure occurred while retrieving the extension
   */
  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  public static <E extends Extension> List<E> getExtensions(
      Class<E> clazz, java.lang.annotation.Annotation annotation) {
    try {
      final List<E> extensions = new ArrayList<>(4);

      for (final Iterator<Factory> i = ServiceLoader.load(Factory.class).iterator();
          i.hasNext(); ) {
        final Extension extension = i.next().getExtension(annotation);

        if (clazz.isInstance(extension)) {
          extensions.add(clazz.cast(extension));
        }
      }
      return extensions;
    } catch (VirtualMachineError | DominionException e) {
      throw e;
    } catch (Throwable t) {
      throw new OptionException(
          "failure retrieving an option extension for: " + annotation.annotationType(), t);
    }
  }

  /**
   * Defines a meta annotation which enables the developer to create a new option annotation which
   * can be used to configure a test container.
   *
   * <p>Before a container is staged and the {@link Dominion} test runner will recursively search
   * all annotations associated with the test class to find all instances of this annotation type.
   * It will then combine them together in the provided order in order to configure a container.
   *
   * <p>It is possible to also annotate these annotations with {@link
   * org.codice.dominion.conditions.Condition.Annotation} in order to render the whole option
   * annotation conditional.
   *
   * <p><code>String</code> and <code>String[]</code> methods on these annotation can be annotated
   * with the {@link Interpolate} annotation to indicate their value(s) supports interpolation.
   *
   * <p>Each option for which no driver-specific extension implementation can be located will be
   * silently ignored.
   */
  @Target(ElementType.ANNOTATION_TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface Annotation {}

  /** Extension point for user-defined option annotations. */
  public interface Extension {}

  /**
   * Factory which can be registered as a service retrievable via Java's {@link
   * java.util.ServiceLoader} to instantiate extension classes for specific meta-annotations.
   *
   * <p>The factory search stops when a registered factory returns a non-<code>null</code> extension
   * of an expected class.
   */
  public interface Factory {
    /**
     * Gets an extension implementation point for the given option annotation.
     *
     * @param annotation the option annotation for which to create an extension point (this will be
     *     of the annotation class annotated with {@link Annotation}
     * @return the corresponding extension to use or <code>null</code> if this factory does not
     *     support the specified option annotation
     * @throws OptionException if a failure occurred while retrieving the extension
     */
    @Nullable
    public Extension getExtension(java.lang.annotation.Annotation annotation);
  }

  private Option() {}
}
