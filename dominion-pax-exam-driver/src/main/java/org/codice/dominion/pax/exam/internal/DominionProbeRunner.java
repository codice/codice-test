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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.codice.dominion.DominionInitializationException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.ops4j.pax.exam.ExamConfigurationException;
import org.ops4j.pax.exam.ExceptionHelper;
import org.ops4j.pax.exam.TestAddress;
import org.ops4j.pax.exam.TestContainerException;
import org.ops4j.pax.exam.TestDirectory;
import org.ops4j.pax.exam.TestInstantiationInstruction;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.spi.ExamReactor;
import org.ops4j.pax.exam.spi.StagedExamReactor;
import org.ops4j.pax.exam.spi.reactors.ReactorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** PaxExam probe runner which supports the Dominion framework. */
public class DominionProbeRunner extends BlockJUnit4ClassRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionProbeRunner.class);

  /** Reactor manager singleton. */
  private final Class<?> testClass;

  private final ReactorManager manager;

  private final Object testInstance;

  private volatile ExamReactor reactor = null;

  private volatile TestAddress beforeClassAddress = null;

  private volatile TestAddress afterClassAddress = null;

  /**
   * Staged reactor for this test class. This may actually be a reactor already staged for a
   * previous test class, depending on the reactor strategy.
   */
  private volatile StagedExamReactor stagedReactor = null;

  private final Map<FrameworkMethod, TestAddress> addresses =
      Collections.synchronizedMap(new LinkedHashMap<>());

  private final Object childrenLock = new Object();

  private volatile Collection<org.junit.runners.model.FrameworkMethod> filteredChildren = null;

  // creates a nice unique enough id for testing which makes it easier to find the exam directory
  private final String id = new SimpleDateFormat("yyyyMMdd-hhmmss").format(new Date());

  private final PaxExamDriverInterpolator interpolator = new PaxExamDriverInterpolator(id);

  public DominionProbeRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
    LOGGER.debug("DominionProbeRunner({})", testClass.getName());
    LOGGER.info("Creating Dominion PaxExam runner for {}", testClass.getName());
    try {
      this.testClass = testClass;
      this.manager = ReactorManager.getInstance();
      this.testInstance = testClass.newInstance();
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
    } finally {
      DominionConfigurationFactory.clearTestInfo();
    }
  }

  /**
   * We decorate the super method by reactor setup and teardown. This method is called once per
   * class. Note that the given reactor strategy decides whether or not the setup and teardown
   * actually happens at this level.
   */
  @SuppressWarnings({
    "squid:S1181" /* catching VirtualMachineError first */,
    "squid:CallToDeprecatedMethod" /* don't care if interpolation closing fails at this point */
  })
  @Override
  public void run(RunNotifier notifier) {
    LOGGER.debug("{}::run({})", this, notifier);
    LOGGER.info("Running test class {}", testClass.getName());
    try {
      // the call to manager.beforeClass() is where we will be starting the Karaf container
      // which is where the target/exam directory is typically created and everything laid down
      // under it
      manager.beforeClass(stagedReactor, testClass);
      super.run(notifier);
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) {
      // rethrowing the exception does not help, we have to use the notifier here
      final Description description = Description.createSuiteDescription(testClass);

      notifier.fireTestFailure(new Failure(description, t));
    } finally {
      manager.afterClass(stagedReactor, testClass);
      IOUtils.closeQuietly(interpolator);
    }
  }

  @Override
  public String toString() {
    return "DominionProbeRunner[" + getTestClass().getName() + "]";
  }

  /**
   * Override to avoid running BeforeClass and AfterClass by the driver. They shall only be run by
   * the container when using a probe invoker.
   */
  @Override
  protected Statement classBlock(RunNotifier notifier) {
    LOGGER.debug("{}::classBlock({})", this, notifier);
    Statement statement = childrenInvoker(notifier);

    if (!filteredChildren().allMatch(this::isIgnored)) {
      LOGGER.debug("{}::1lassBlock() - adding @BeforeExam and @AfterExam statements", this);
      statement = withBeforeClasses(statement);
      statement = withAfterClasses(statement);
    } else {
      LOGGER.debug(
          "{}::classBlock() - no @BeforeExam and @AfterExam statements as all tests are ignored",
          this);
    }
    return statement;
  }

  @Override
  protected Statement withBeforeClasses(Statement statement) {
    final List<FrameworkMethod> befores = getTestClass().getAnnotatedMethods(BeforeClass.class);

    LOGGER.debug("{}::withBeforeClasses({}) - count={}", this, statement, befores.size());
    return befores.isEmpty() ? statement : new RunBeforeClasses(statement);
  }

  @Override
  protected Statement withAfterClasses(Statement statement) {
    final List<FrameworkMethod> afters = getTestClass().getAnnotatedMethods(AfterClass.class);

    LOGGER.debug("{}::withAfterClasses({}) - count={}", this, statement, afters.size());
    return afters.isEmpty() ? statement : new RunAfterClasses(statement);
  }

  /**
   * Override to avoid running Before, After and Rule methods by the driver. They shall only be run
   * by the container when using a probe invoker.
   */
  @Override
  protected Statement methodBlock(FrameworkMethod method) {
    LOGGER.debug("{}::methodBlock({})", this, method);
    // no point instantiating the test class here since we don't use it
    return methodInvoker(method, null);
  }

  /**
   * When using a probe invoker, we replace the super method and invoke the test method indirectly
   * via the reactor.
   */
  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        final TestAddress address = addresses.get(method);

        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(
              "Invoke "
                  + method.getName()
                  + " @ "
                  + address
                  + " Arguments: "
                  + address.root().arguments());
        }
        try {
          stagedReactor.invoke(address);
        } catch (Exception e) {
          throw ExceptionHelper.unwind(e);
        }
      }
    };
  }

  /**
   * When using a probe invoker, we replace the test methods of this class by a potentially larger
   * set of decorated test methods. Each original test method may give rise to multiple copies per
   * test container or configuration.
   */
  @Override
  protected List<FrameworkMethod> getChildren() {
    LOGGER.debug("{}::getChildren()", this);
    synchronized (addresses) {
      if (addresses.isEmpty()) {
        fillChildren();
      }
      return new ArrayList<>(addresses.keySet());
    }
  }

  /**
   * Adds test methods to the reactor, mapping method names to test addresses which are used by the
   * probe invoker.
   *
   * <p>Note that when a collection of test classes is passed to an external JUnit runner like
   * Eclipse or MavenUrl Surefire, this method is invoked (via the constructor of this runner) for
   * each class <em>before</em> the {@link #run(RunNotifier)} method is invoked for any class.
   *
   * <p>This way, we can register all test methods in the reactor before the actual test execution
   * starts.
   *
   * @throws IOException if an I/O error occurs
   * @throws ExamConfigurationException if a configuration error occurs
   */
  @SuppressWarnings("squid:CommentedOutCodeLine" /* left over from PaxExam's code */)
  private void addTestsToReactor() throws IOException, ExamConfigurationException {
    LOGGER.debug("{}::addTestsToReactor()", this);
    final TestProbeBuilder probe = manager.createProbeBuilder(testInstance);

    // probe.setAnchor( testClass );
    for (final FrameworkMethod method : super.getChildren()) {
      // record the method -> address matching
      TestAddress address = delegateTest(probe, method);

      if (address == null) {
        address = probe.addTest(testClass, method.getMethod().getName());
      }
      manager.storeTestMethod(address, method);
    }
    reactor.addProbe(probe);
  }

  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  private TestAddress delegateTest(TestProbeBuilder probe, FrameworkMethod method) {
    try {
      final Class<?>[] types = method.getMethod().getParameterTypes();

      if ((types.length == 1) && types[0].isAssignableFrom(TestProbeBuilder.class)) {
        // do some backtracking:
        return (TestAddress) method.getMethod().invoke(testInstance, probe);
      } else {
        return null;
      }
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) {
      throw new TestContainerException("failed delegating to test: " + method, t);
    }
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

  private Stream<FrameworkMethod> filteredChildren() {
    if (filteredChildren == null) {
      synchronized (childrenLock) {
        if (filteredChildren == null) {
          this.filteredChildren = getChildren();
        }
      }
    }
    return filteredChildren.stream();
  }

  private void fillChildren() {
    final Set<TestAddress> targets = stagedReactor.getTargets();
    final TestDirectory testDirectory = TestDirectory.getInstance();
    final boolean mangleMethodNames = manager.getNumConfigurations() > 1;
    final String className = testClass.getName();

    for (final TestAddress address : targets) {
      final FrameworkMethod testMethod = (FrameworkMethod) manager.lookupTestMethod(address.root());

      // The reactor may contain targets which do not belong to the current test class
      if (testMethod == null) {
        continue;
      }
      final Class<?> testMethodClass = testMethod.getMethod().getDeclaringClass();
      final String methodName = testMethod.getName();

      if (testMethodClass.isAssignableFrom(testClass)) {
        final FrameworkMethod method =
            mangleMethodNames ? new DecoratedFrameworkMethod(address, testMethod) : testMethod;

        testDirectory.add(address, new TestInstantiationInstruction(className + ";" + methodName));
        addresses.put(method, address);
      }
    }
  }

  @Nullable
  private TestAddress findStagedAddress(TestAddress preparedAddress) {
    return stagedReactor
        .getTargets()
        .stream()
        .filter(a -> preparedAddress.equals(a.root()))
        .findFirst()
        .orElse(null);
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
          DominionProbeRunner.this,
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
            DominionProbeRunner.this,
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
