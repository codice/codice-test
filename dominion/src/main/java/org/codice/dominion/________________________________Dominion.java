package org.codice.dominion;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.codice.dominion.________________________________Dominion.Repeatable________________________________Dominion;

/**
 * Simple annotation used as a delimiter making annotations more readable when multiple annotations
 * are defined on a given element.
 */
@Retention(RetentionPolicy.SOURCE)
@Repeatable(Repeatable________________________________Dominion.class)
public @interface ________________________________Dominion {
  /** Defines multiple divider annotations. */
  public @interface Repeatable________________________________Dominion {
    public ________________________________Dominion[] value();
  }
}
