package org.codice.pax.exam.junit;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.codice.pax.exam.junit.__________________________Configurations.Repeatable__________________________Configurations;

/**
 * Simple annotation used as a delimiter making annotations more readable when multiple annotations
 * are defined on a given element.
 */
@Retention(RetentionPolicy.SOURCE)
@Repeatable(Repeatable__________________________Configurations.class)
public @interface __________________________Configurations {
  /** Defines multiple divider annotations. */
  public @interface Repeatable__________________________Configurations {
    public __________________________Configurations[] value();
  }
}
