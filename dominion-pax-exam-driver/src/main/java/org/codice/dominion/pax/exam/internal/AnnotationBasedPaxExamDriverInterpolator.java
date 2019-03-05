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

/**
 * Version of the interpolator which will support interpolating attributes of a given annotation as
 * long as the attribute is prefixed with <code>"annotation."</code>.
 */
public class AnnotationBasedPaxExamDriverInterpolator extends PaxExamDriverInterpolator {
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
      if (key.startsWith("annotation.")) {
        final String attribute = key.substring(11);

        try {
          final Method method = annotation.annotationType().getMethod(attribute);
          final Object obj = method.invoke(annotation);

          value = Objects.toString(obj, null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
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
