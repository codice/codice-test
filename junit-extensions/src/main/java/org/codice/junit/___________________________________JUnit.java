package org.codice.junit;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.codice.junit.___________________________________JUnit.Repeatable___________________________________JUnit;

/**
 * Simple annotation used as a delimiter making annotations more readable when multiple annotations
 * are defined on a given element.
 */
@Retention(RetentionPolicy.SOURCE)
@Repeatable(Repeatable___________________________________JUnit.class)
public @interface ___________________________________JUnit {
  /** Defines multiple divider annotations. */
  public @interface Repeatable___________________________________JUnit {
    public ___________________________________JUnit[] value();
  }
}
