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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** PaxExam probe runner which supports the Dominion framework. */
public class DominionProbeRunner extends AbstractDominionProbeRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionProbeRunner.class);

  private final Map<FrameworkMethod, TestAddress> addresses =
      Collections.synchronizedMap(new LinkedHashMap<>());

  private final Object childrenLock = new Object();

  private volatile Collection<org.junit.runners.model.FrameworkMethod> filteredChildren = null;

  public DominionProbeRunner(Class<?> testClass) throws InitializationError {
    super(testClass);
    LOGGER.debug("DominionProbeRunner({})", testClass.getName());
    LOGGER.info("Creating Dominion PaxExam runner for {}", testClass.getName());
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
      // which is where the target/dominion directory is typically created and everything laid down
      // under it
      manager.beforeClass(stagedReactor, testClass);
      // now let the config factory know the container was started
      config.processPostStartOptions();
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
      LOGGER.debug("{}::classBlock() - adding @BeforeExam and @AfterExam statements", this);
      statement = withBeforeClasses(statement);
      statement = withAfterClasses(statement);
    } else {
      LOGGER.debug(
          "{}::classBlock() - no @BeforeExam and @AfterExam statements as all tests are ignored",
          this);
    }
    return statement;
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
                  + Arrays.toString(address.root().arguments()));
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

  @Override
  @SuppressWarnings("squid:CommentedOutCodeLine" /* left over from PaxExam's code */)
  protected void addTestsToReactor() throws IOException, ExamConfigurationException {
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
}
