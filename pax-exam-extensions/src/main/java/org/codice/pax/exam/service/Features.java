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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** This interface is defined purely to provide scoping. */
public interface Features {
  @Target({ElementType.METHOD, ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  /** Defines several {@link Feature.Start} annotations. */
  public @interface Start {
    /**
     * List of all {@link Feature.Start} annotations.
     *
     * @return the list of all {@link Feature.Start} annotations
     */
    Feature.Start[] value();
  }

  @Target({ElementType.METHOD, ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  /** Defines several {@link Feature.Stop} annotations. */
  public @interface Stop {
    /**
     * List of all {@link Feature.Stop} annotations.
     *
     * @return the list of all {@link Feature.Stop} annotations
     */
    Feature.Stop[] value();
  }
}
