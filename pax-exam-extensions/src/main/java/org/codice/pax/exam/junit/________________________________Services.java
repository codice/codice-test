package org.codice.pax.exam.junit;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.codice.pax.exam.junit.________________________________Services.Repeatable________________________________Services;

/**
 * Simple annotation used as a delimiter making annotations more readable when multiple annotations
 * are defined on a given element.
 */
@Retention(RetentionPolicy.SOURCE)
@Repeatable(Repeatable________________________________Services.class)
public @interface ________________________________Services {
  /** Defines multiple divider annotations. */
  public @interface Repeatable________________________________Services {
    public ________________________________Services[] value();
  }
}
