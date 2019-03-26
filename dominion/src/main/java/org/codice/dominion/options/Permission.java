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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Permission information. */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Permission {
  /**
   * Specifies the fully qualified class name of a permission; the class must be a subclass of
   * {@code java.security.Permission}.
   *
   * <p><i>Note:</i> One of {@link #type} or {@link #clazz()} must be specified.
   *
   * @return the fully qualified class name of the permission
   */
  String type() default "";

  /**
   * Specifies the permission class.
   *
   * <p><i>Note:</i> One of {@link #type} or {@link #clazz()} must be specified.
   *
   * @return the permission class
   */
  Class<? extends java.security.Permission> clazz() default java.security.Permission.class;

  /**
   * Specifies the permission name.
   *
   * @return the permission name
   */
  String name();

  /**
   * Specifies the optional permission actions.
   *
   * @return the optional permission actions
   */
  String actions() default "";
}
