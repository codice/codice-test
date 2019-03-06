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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;
import org.codice.dominion.interpolate.InterpolationException;

/**
 * Version of the interpolator which will support interpolating attributes of a given annotation as
 * long as the attribute is prefixed with <code>"annotation."</code>.
 */
public class AnnotationBasedPaxExamDriverInterpolator extends PaxExamDriverInterpolator {
  private static final String ANNOTATION_PREFIX = "annotation.";
  private static final int ANNOTATION_PREFIX_LENGTH =
      AnnotationBasedPaxExamDriverInterpolator.ANNOTATION_PREFIX.length();

  private final Annotation annotation;

  private final PaxExamDriverInterpolator interpolator;

  public AnnotationBasedPaxExamDriverInterpolator(
      Annotation annotation, PaxExamDriverInterpolator interpolator) {
    super(interpolator);
    this.annotation = annotation;
    this.interpolator = interpolator;
  }

  @Nullable
  @Override
  public String lookup(String key) {
    String value = interpolator.lookup(key);

    if (value == null) {
      if (key.startsWith(AnnotationBasedPaxExamDriverInterpolator.ANNOTATION_PREFIX)) {
        final String attribute =
            key.substring(AnnotationBasedPaxExamDriverInterpolator.ANNOTATION_PREFIX_LENGTH);

        try {
          final Method method = annotation.annotationType().getMethod(attribute);
          final Object obj = method.invoke(annotation);

          value = Objects.toString(obj, null);
        } catch (NoSuchMethodException | IllegalAccessException e) {
          throw new InterpolationException(
              "failed to find annotation attribute '" + attribute + "' for: " + annotation, e);
        } catch (InvocationTargetException e) {
          throw new InterpolationException(
              "failed to find annotation attribute '" + attribute + "' for: " + annotation,
              e.getTargetException());
        }
      }
    }
    return value;
  }

  @Override
  public Path getKarafHome() {
    return interpolator.getKarafHome();
  }

  @Override
  public Path getKarafBin() {
    return interpolator.getKarafBin();
  }

  @Override
  public Path getKarafData() {
    return interpolator.getKarafData();
  }

  @Override
  public Path getKarafEtc() {
    return interpolator.getKarafEtc();
  }
}
