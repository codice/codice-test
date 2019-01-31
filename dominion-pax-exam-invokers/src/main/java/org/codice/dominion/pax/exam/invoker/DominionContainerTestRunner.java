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
package org.codice.dominion.pax.exam.invoker;

import java.util.List;
import org.codice.junit.rules.MethodRuleAnnotationProcessor;
import org.junit.rules.MethodRule;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.ops4j.pax.exam.invoker.junit.internal.ContainerTestRunner;
import org.ops4j.pax.exam.util.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DominionContainerTestRunner extends ContainerTestRunner implements DominionRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionContainerTestRunner.class);

  public DominionContainerTestRunner(Class<?> testClass, Injector injector)
      throws InitializationError {
    super(testClass, injector);
    LOGGER.debug("DominionContainerTestRunner({}, {})", testClass, injector);
  }

  @Override
  public void callBeforeClasses() throws Throwable {
    withBeforeClasses(EmptyStatement.EMPTY).evaluate();
  }

  @Override
  public void callAfterClasses() throws Throwable {
    withAfterClasses(EmptyStatement.EMPTY).evaluate();
  }

  @Override
  public String toString() {
    return "DominionContainerTestRunner(" + getTestClass() + ")";
  }

  /**
   * Override to avoid running BeforeClass and AfterClass as it will be initiate separately from the
   * probe invoker before running the first test and when it detect all tests registered by the
   * probe runner have been run.
   */
  @Override
  protected Statement classBlock(RunNotifier notifier) {
    LOGGER.debug("{}::classBlock({})", this, notifier);
    return childrenInvoker(notifier);
  }

  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    LOGGER.debug("{}::methodBlock({})", this, method);
    return super.methodBlock(method);
  }

  @Override
  protected List<MethodRule> rules(Object target) {
    LOGGER.debug("{}::rules({})", this, target);
    return MethodRuleAnnotationProcessor.around(super.rules(target));
  }
}
