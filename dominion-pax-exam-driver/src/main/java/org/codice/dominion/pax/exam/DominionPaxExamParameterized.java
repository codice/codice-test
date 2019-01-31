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
package org.codice.dominion.pax.exam;

import org.codice.dominion.pax.exam.internal.DominionParameterizedProbeRunner;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.ops4j.pax.exam.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DominionPaxExamParameterized extends DominionPaxExam {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionPaxExamParameterized.class);

  // called via reflection by Dominion
  public DominionPaxExamParameterized(Class<?> testClass) throws InitializationError {
    super(testClass, DominionPaxExamParameterized.createDelegate(testClass));
    LOGGER.debug("DominionPaxExamParameterized({})", testClass.getName());
  }

  @Override
  public String toString() {
    return "DominionPaxExamParameterized[" + testClass.getName() + ", " + delegate + "]";
  }

  @SuppressWarnings("squid:CommentedOutCodeLine" /* left over code from PaxExam */)
  private static ParentRunner createDelegate(Class<?> testClass) throws InitializationError {
    // force us into a per-suite strategy
    System.setProperty(
        Constants.EXAM_REACTOR_STRATEGY_KEY, Constants.EXAM_REACTOR_STRATEGY_PER_SUITE);
    //    final ReactorManager manager = ReactorManager.getInstance();
    //
    //    if (manager.getSystemType().equals(Constants.EXAM_SYSTEM_CDI)) {
    //      return new ParameterizedInjectingRunner(testClass);
    //    }
    final DominionParameterizedProbeRunner eppr = new DominionParameterizedProbeRunner(testClass);

    eppr.prepare();
    eppr.stage();
    return eppr;
  }
}
