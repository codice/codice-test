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
package org.codice.junit;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.codice.junit.________________________________________.Repeatable________________________________________;

/**
 * Simple annotation used as a delimiter making annotations more readable when multiple annotations
 * are defined on a given element.
 */
@Retention(RetentionPolicy.SOURCE)
@Repeatable(Repeatable________________________________________.class)
public @interface ________________________________________ {
  /** Defines multiple divider annotations. */
  public @interface Repeatable________________________________________ {
    public ________________________________________[] value();
  }
}
