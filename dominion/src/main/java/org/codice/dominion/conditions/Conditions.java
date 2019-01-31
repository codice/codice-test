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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.codice.dominion.interpolate.Interpolate;

/**
 * This class defines conditions that can be used to make Dominion options optional. It is solely
 * used for scoping.
 */
public class Conditions {
  /**
   * A condition that evaluates to <code>true</code> only if the specified system property is
   * defined with a value of <code>"true"</code> (case insensitive).
   */
  @Condition.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.BooleanSystemProperties.class)
  public @interface BooleanSystemProperty {
    /**
     * Specifies the name of the system property to evaluate as a boolean.
     *
     * @return the name of the system property to evaluate as a boolean
     */
    @Interpolate
    String value();

    /**
     * Specifies the default if the system property is not defined (defaults to false).
     *
     * @return the default if the system property is not defined
     */
    boolean defaultsTo() default false;
  }

  /**
   * A condition that evaluates to <code>true</code> only if the specified system property is
   * defined and is not blank.
   */
  @Condition.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.NotBlankSystemProperties.class)
  public @interface NotBlankSystemProperty {
    /**
     * Specifies the name of the system property to check.
     *
     * @return the name of the system property to check
     */
    @Interpolate
    String value();
  }

  /**
   * A condition that evaluates to <code>true</code> only if the specified system property is
   * defined and is not empty.
   */
  @Condition.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.NotEmptySystemProperties.class)
  public @interface NotEmptySystemProperty {
    /**
     * Specifies the name of the system property to check.
     *
     * @return the name of the system property to check
     */
    @Interpolate
    String value();
  }

  /** This interface is defined purely to provide scoping. */
  public interface Repeatables {
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link BooleanSystemPropertiy} annotations. */
    public @interface BooleanSystemProperties {
      BooleanSystemProperty[] value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link NotBlankSystemPropertiy} annotations. */
    public @interface NotBlankSystemProperties {
      NotBlankSystemProperty[] value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link NotEmptySystemPropertiy} annotations. */
    public @interface NotEmptySystemProperties {
      NotEmptySystemProperty[] value();
    }
  }

  private Conditions() {}
}
