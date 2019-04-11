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
package org.codice.dominion.pax.exam.internal;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;
import org.codice.dominion.conditions.Condition;
import org.codice.dominion.conditions.ConditionException;
import org.codice.dominion.interpolate.Interpolator;
import org.codice.dominion.options.Option;
import org.codice.test.commons.ReflectionUtils;
import org.codice.test.commons.ReflectionUtils.AnnotationEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Annotation utilities functions. */
public class AnnotationUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationUtils.class);

  /**
   * Gets the annotations that are <em>associated</em> with the specified source class and the meta
   * annotations recursively contained in annotations that are <em>associated</em> with the
   * specified source class while evaluating any {@link Condition} that may exist.
   *
   * @param <A> the type of annotation to find
   * @param interpolator the interpolator to use when evaluating condition annotations
   * @param source the source class to find all annotations and meta annotations for
   * @param clazz the type of annotations to find
   * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
   *     specified type found in the order they are defined
   * @throws ConditionException if an error occurred while evaluation a condition annotation
   */
  public static <A extends Annotation> Stream<AnnotationEntry<A>> annotationsByType(
      Interpolator interpolator, Class<?> source, Class<A> clazz) {
    return ReflectionUtils.annotationsByType(
        e -> AnnotationUtils.filterConditionAnnotations(interpolator, source, e), source, clazz);
  }

  private static boolean filterConditionAnnotations(
      Interpolator interpolator, Class<?> source, AnnotationEntry<?> entry) {
    // check directly contained annotations, one by one for annotations that are only doing
    // conditions. Those are are doing both conditions and options are ignored at this level and
    // left to be evaluated when we get to deepest annotations
    return entry
        .annotations()
        .allMatch(e -> AnnotationUtils.filterConditionAnnotation(interpolator, source, e));
  }

  private static boolean filterConditionAnnotation(
      Interpolator interpolator, Class<?> source, AnnotationEntry<?> entry) {
    if (entry.isEnclosingAnInstanceOf(Option.Annotation.class)) {
      // if it encloses an option annotation, then we shall not look for recursive annotated
      // conditions as we will let the deepest annotations take care of them
      return true;
    }
    return entry
        .enclosingAnnotationsByType(Condition.Annotation.class)
        .allMatch(e -> AnnotationUtils.evaluateConditionAnnotation(interpolator, source, e));
  }

  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  private static boolean evaluateConditionAnnotation(
      Interpolator interpolator, Class<?> source, AnnotationEntry<Condition.Annotation> entry) {
    // getEnclosingAnnotation() cannot be null since the Option.Annotation can only be added to
    // other annotations
    final AnnotationEntry<?> enclosingEntry = entry.getEnclosingAnnotation();
    final Annotation annotation = enclosingEntry.getAnnotation();

    try {
      final Condition.Extension<Annotation> extension =
          Condition.getExtension(Condition.Extension.class, annotation);

      if (extension == null) {
        LOGGER.debug(
            "AnnotationUtils::evaluateConditionAnnotation - ignoring condition {} as no extension implementation could be located",
            annotation);
        return true;
      }
      return extension.evaluate(
          interpolator.interpolate(annotation), source, new AnnotationResourceLoader(entry));
    } catch (VirtualMachineError | ConditionException e) {
      throw e;
    } catch (Throwable t) {
      throw new ConditionException(
          "failed to evaluate condition from "
              + annotation.annotationType().getSimpleName()
              + " extension for "
              + source.getName()
              + " and "
              + annotation,
          t);
    }
  }

  private AnnotationUtils() {}
}
