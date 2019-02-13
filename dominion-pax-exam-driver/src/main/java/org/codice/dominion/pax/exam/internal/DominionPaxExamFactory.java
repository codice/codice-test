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

import javax.annotation.Nullable;
import org.codice.dominion.Dominion;
import org.codice.dominion.Dominion.Factory;
import org.codice.dominion.DominionParameterized;
import org.codice.dominion.pax.exam.DominionPaxExam;
import org.codice.dominion.pax.exam.DominionPaxExamParameterized;
import org.junit.runner.Runner;

/** Dominion factory for the PaxExam driver. */
public class DominionPaxExamFactory implements Factory {
  @Override
  @Nullable
  public Class<? extends Runner> getDriver(String type) {
    switch (type) {
      case Dominion.TYPE:
        return DominionPaxExam.class;
      case DominionParameterized.TYPE:
        return DominionPaxExamParameterized.class;
      default:
        return null;
    }
  }
}
