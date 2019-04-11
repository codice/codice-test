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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.annotation.Nullable;
import org.codice.dominion.DominionInitializationException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.ops4j.pax.exam.ExamConfigurationException;
import org.ops4j.pax.exam.ExceptionHelper;
import org.ops4j.pax.exam.TestAddress;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.spi.ExamReactor;
import org.ops4j.pax.exam.spi.StagedExamReactor;
import org.ops4j.pax.exam.spi.reactors.ReactorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for PaxExam probe runners supporting the dominion framework. */
public abstract class AbstractDominionProbeRunner extends BlockJUnit4ClassRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDominionProbeRunner.class);

  protected final Class<?> testClass;

  protected final Object testInstance;

  /** Reactor manager singleton. */
  protected final ReactorManager manager;

  protected final PaxExamDriverInterpolator interpolator;

  // creates a nice unique enough id for testing which makes it easier to find the exam directory
  protected final String id = new SimpleDateFormat("yyyyMMdd-hhmmss").format(new Date());

  protected volatile ExamReactor reactor = null;

  protected volatile TestAddress beforeClassAddress = null;

  protected volatile TestAddress afterClassAddress = null;

  /**
   * Staged reactor for this test class. This may actually be a reactor already staged for a
   * previous test class, depending on the reactor strategy.
   */
  protected volatile StagedExamReactor stagedReactor = null;

  @Nullable // will be set after staging
  protected volatile DominionConfigurationFactory config = null;

  public AbstractDominionProbeRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
    this.testClass = testClass;
    try {
      this.manager = ReactorManager.getInstance();
      this.testInstance = testClass.newInstance();
      this.interpolator = new PaxExamDriverInterpolator(testClass, id);
    } catch (InstantiationException | IllegalAccessException e) {
      throw new DominionInitializationException(e);
    }
  }

  public ReactorManager getManager() {
    return manager;
  }

  public Object getTestInstance() {
    return testInstance;
  }

  /** Prepares the reactor. */
  public void prepare() {
    LOGGER.debug("{}::prepare()", this);
    LOGGER.info("Preparing Dominion PaxExam reactor for {}", testClass.getName());
    try {
      this.reactor = manager.prepareReactor(testClass, testInstance);
      LOGGER.debug("{}::prepare() - reactor = {}", this, reactor);
      addBeforeClassToReactor();
      addAfterClassToReactor();
      addTestsToReactor();
    } catch (IOException | ExamConfigurationException e) {
      throw new DominionInitializationException(e);
    }
  }

  /** Stages the reactor for the current test class. */
  public void stage() {
    LOGGER.debug("{}::stage()", this);
    LOGGER.info("Staging Dominion PaxExam reactor for {}", testClass.getName());
    try {
      DominionConfigurationFactory.setTestInfo(interpolator, testInstance);
      this.stagedReactor = manager.stageReactor();
      LOGGER.debug("{}::stage() - staged reactor = {}", this, stagedReactor);
      this.config = DominionConfigurationFactory.getConfigInfo();
    } finally {
      DominionConfigurationFactory.clearTestInfo();
    }
  }

  @Override
  protected Statement withBeforeClasses(Statement statement) {
    final List<FrameworkMethod> befores = getTestClass().getAnnotatedMethods(BeforeClass.class);

    LOGGER.debug("{}::withBeforeClasses({}) - count={}", this, statement, befores.size());
    // register the @BeforeClass whether or not methods were annotated. This will give a chance
    // to the probe runner to do prep work of its own
    return new RunBeforeClasses(statement);
  }

  @Override
  protected Statement withAfterClasses(Statement statement) {
    final List<FrameworkMethod> afters = getTestClass().getAnnotatedMethods(AfterClass.class);

    LOGGER.debug("{}::withAfterClasses({}) - count={}", this, statement, afters.size());
    // register the @BeforeClass whether or not methods were annotated. This will give a chance
    // to the probe runner to do cleanup work of its own
    return new RunAfterClasses(statement);
  }

  /**
   * Adds test methods to the reactor, mapping method names to test addresses which are used by the
   * probe invoker.
   *
   * <p>Note that when a collection of test classes is passed to an external JUnit runner like
   * Eclipse or MavenUrl Surefire, this method is invoked (via the constructor of this runner) for
   * each class <em>before</em> the {@link #run(org.junit.runner.notification.RunNotifier)} method
   * is invoked for any class.
   *
   * <p>This way, we can register all test methods in the reactor before the actual test execution
   * starts.
   *
   * @throws IOException if an I/O error occurs
   * @throws ExamConfigurationException if a configuration error occurs
   */
  protected abstract void addTestsToReactor() throws IOException, ExamConfigurationException;

  @Nullable
  private TestAddress findStagedAddress(TestAddress preparedAddress) {
    return stagedReactor
        .getTargets()
        .stream()
        .filter(a -> preparedAddress.equals(a.root()))
        .findFirst()
        .orElse(null);
  }

  private void addBeforeClassToReactor() throws IOException, ExamConfigurationException {
    LOGGER.debug("{}::addBeforeClassToReactor()", this);
    final TestProbeBuilder probe = manager.createProbeBuilder(testInstance);

    this.beforeClassAddress = probe.addTest(testClass, "@BeforeClass");
    manager.storeTestMethod(beforeClassAddress, null);
  }

  private void addAfterClassToReactor() throws IOException, ExamConfigurationException {
    LOGGER.debug("{}::addAfterClassToReactor()", this);
    final TestProbeBuilder probe = manager.createProbeBuilder(testInstance);

    this.afterClassAddress = probe.addTest(testClass, "@AfterClass");
    manager.storeTestMethod(afterClassAddress, null);
  }

  private class RunBeforeClasses extends Statement {
    private final Statement next;

    RunBeforeClasses(Statement next) {
      this.next = next;
    }

    @Override
    public void evaluate() throws Throwable {
      LOGGER.debug(
          "{}.RunBeforeClasses::evaluate() - running @BeforeClasses -> {}",
          AbstractDominionProbeRunner.this,
          beforeClassAddress);
      try {
        stagedReactor.invoke(findStagedAddress(beforeClassAddress));
      } catch (Exception e) {
        throw ExceptionHelper.unwind(e);
      }
      next.evaluate();
    }
  }

  private class RunAfterClasses extends Statement {
    private final Statement previous;

    RunAfterClasses(Statement previous) {
      this.previous = previous;
    }

    @SuppressWarnings({
      "squid:S1163" /* per design where we want to unwind any exceptions thrown */,
      "squid:S1143" /* per design where we want to unwind any exceptions thrown */
    })
    @Override
    public void evaluate() throws Throwable {
      try {
        previous.evaluate();
      } finally {
        LOGGER.debug(
            "{}.RunAfterClasses::evaluate() - running @AfterClasses -> {}",
            AbstractDominionProbeRunner.this,
            afterClassAddress);
        try {
          stagedReactor.invoke(findStagedAddress(afterClassAddress));
        } catch (Exception e) {
          throw ExceptionHelper.unwind(e);
        }
      }
    }
  }
}
