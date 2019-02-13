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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.model.RunnerBuilder;
import org.ops4j.pax.exam.ProbeInvoker;
import org.ops4j.pax.exam.TestContainerException;
import org.ops4j.pax.exam.WrappedTestContainerException;
import org.ops4j.pax.exam.util.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DominionProbeInvoker implements ProbeInvoker {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionProbeInvoker.class);

  private final PaxExamInterpolator interpolator = new PaxExamInterpolator();

  private final Class<?> testClass;
  private final String method;
  private final Injector injector;

  public DominionProbeInvoker(Class<?> testClass, String method, Injector injector) {
    LOGGER.debug("DominionProbeInvoker({}, {}, {})", testClass, method, injector);
    this.testClass = interpolator.interpolate(testClass);
    this.method = method;
    this.injector = injector;
  }

  @Override
  public void call(Object... args) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("{}::call({})", this, Arrays.toString(args));
    }
    final RunnerBuilder builder;

    // if args are present, they represent the index of the parameter set for a parameterized test
    if (args.length > 0) {
      if (!(args[0] instanceof Integer)) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.error("{}::call({}) - unexpected argument", this, Arrays.toString(args));
        }
        throw new TestContainerException("Integer argument expected");
      }
      builder = createParameterizedRunnerBuilder((Integer) args[0]);
    } else {
      builder = createRunnerBuilder();
    }
    final Request request =
        new Request() {
          @Override
          public Runner getRunner() {
            return builder.safeRunnerForClass(testClass);
          }
        };

    if (method.equals("@BeforeClass")) {
      callBeforeClass(request);
    } else if (method.equals("@AfterClass")) {
      callAfterClass(request);
    } else {
      callTest(request, findMethod(), args);
    }
  }

  @Override
  public String toString() {
    return "DominionProbeInvoker[" + testClass.getName() + ", " + method + "]";
  }

  protected void callBeforeClass(Request request) {
    LOGGER.debug("{}::callBeforeClass(Request)", this);
    try {
      ((DominionRunner) request.getRunner()).callBeforeClasses();
    } catch (StoppedByUserException e) {
      throw e;
    } catch (Throwable e) {
      throw DominionProbeInvoker.createTestContainerException("@BeforeClass: " + e.getMessage(), e);
    }
  }

  protected void callAfterClass(Request request) {
    LOGGER.debug("{}::callAfterClass(Request)", this);
    try {
      ((DominionRunner) request.getRunner()).callAfterClasses();
    } catch (StoppedByUserException e) {
      throw e;
    } catch (Throwable e) {
      throw DominionProbeInvoker.createTestContainerException("@AfterClass: " + e.getMessage(), e);
    }
  }

  protected void callTest(Request request, Method method, Object... args) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("{}::callTest(Request, {}, {})", this, method, Arrays.toString(args));
    }
    final Result result = call(request.filterWith(getDescription()));

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("{}::call({}) - result = {}", this, Arrays.toString(args), result);
    }
    final List<Failure> failures = result.getFailures();

    if (!failures.isEmpty()) {
      throw DominionProbeInvoker.createTestContainerException(
          failures.toString(), failures.get(0).getException());
    }
  }

  protected Result call(Request request) {
    final JUnitCore junit = new JUnitCore();

    return junit.run(request);
  }

  protected RunnerBuilder createRunnerBuilder() {
    return new RunnerBuilder() {
      @Override
      public Runner runnerForClass(Class<?> testClass) throws Throwable {
        return new DominionContainerTestRunner(testClass, injector);
      }
    };
  }

  protected RunnerBuilder createParameterizedRunnerBuilder(int index) {
    return new RunnerBuilder() {
      @Override
      public Runner runnerForClass(Class<?> testClass) throws Throwable {
        return new DominionParameterizedContainerTestRunner(testClass, injector, index);
      }
    };
  }

  protected Method findMethod() {
    final Method result =
        Stream.of(testClass.getMethods())
            .filter(m -> method.equals(m.getName()))
            .findFirst()
            .orElseThrow(
                () -> {
                  LOGGER.error("{}::findMethod() - method not found", this);
                  return new TestContainerException(
                      " Test " + method + " not found in test class " + testClass.getName());
                });

    LOGGER.debug("{}::findMethod() - {}", this, result);
    return result;
  }

  protected Description getDescription() {
    final Description d = Description.createTestDescription(testClass, method);

    LOGGER.debug("{}::getDescription() - {}", this, d);
    return d;
  }

  protected static TestContainerException createTestContainerException(
      String message, Throwable error) {
    return DominionProbeInvoker.isSerializable(error)
        ? new TestContainerException(message, error)
        : new WrappedTestContainerException(message, error);
  }

  @SuppressWarnings("squid:S1181" /* first catching VirtualMachineError */)
  private static boolean isSerializable(Throwable error) {
    try {
      new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(error);
      return true;
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) {
      return false;
    }
  }
}
