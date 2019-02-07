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

import org.codice.dominion.pax.exam.internal.DominionProbeRunner;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.manipulation.Sortable;
import org.junit.runner.manipulation.Sorter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.ops4j.pax.exam.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JUnit runner for the Dominion PaxExam driver. */
public class DominionPaxExam extends Runner implements Filterable, Sortable {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionPaxExam.class);

  protected final Class<?> testClass;
  protected final ParentRunner<?> delegate;

  // called via reflection by Dominion
  public DominionPaxExam(Class<?> testClass) throws InitializationError {
    this(testClass, DominionPaxExam.createDelegate(testClass));
    LOGGER.debug("DominionPaxExam({})", testClass.getName());
  }

  protected DominionPaxExam(Class<?> testClass, ParentRunner<?> delegate) {
    LOGGER.debug("DominionPaxExam({}, {})", testClass.getName(), delegate);
    this.testClass = testClass;
    this.delegate = delegate;
  }

  @Override
  public Description getDescription() {
    return delegate.getDescription();
  }

  @Override
  public void run(RunNotifier notifier) {
    delegate.run(notifier);
  }

  @Override
  public void filter(Filter filter) throws NoTestsRemainException {
    delegate.filter(filter);
  }

  @Override
  public void sort(Sorter sorter) {
    delegate.sort(sorter);
  }

  @Override
  public String toString() {
    return "DominionPaxExam[" + testClass.getName() + ", " + delegate + "]";
  }

  @SuppressWarnings(
      "squid:CommentedOutCodeLine" /* left over code from PaxExam to be removed when we are guaranteed we won't support CDI */)
  private static ParentRunner createDelegate(Class<?> testClass) throws InitializationError {
    // force us into a per-suite strategy
    System.setProperty(
        Constants.EXAM_REACTOR_STRATEGY_KEY, Constants.EXAM_REACTOR_STRATEGY_PER_SUITE);
    //    final ReactorManager manager = ReactorManager.getInstance();
    //
    //    if (manager.getSystemType().equals(Constants.EXAM_SYSTEM_CDI)) {
    //      return new InjectingRunner(testClass);
    //    }
    final DominionProbeRunner epr = new DominionProbeRunner(testClass);

    epr.prepare();
    epr.stage();
    return epr;
  }
}
