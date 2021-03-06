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
package org.codice.junit.rules;

import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.junit.Test;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.util.Nullable;

/**
 * This class is designed as a JUnit method rule capable of delimiting each test executed and
 * optionally indicated the elapsed time for each test by logging the information.
 */
public class TestDelimiter extends AbstractMethodRule {
  private static final Logger LOGGER = LoggerFactory.getLogger(TestDelimiter.class);

  private final boolean stdout;

  private final boolean elapsed;

  /** Constructs a test delimiter with no elapsed time reporting logging to standard out. */
  public TestDelimiter() {
    this(true, false);
  }

  /**
   * Constructs a test delimiter.
   *
   * @param stdout <code>true</code> to log to standard out; <code>false</code> to log via slf4j at
   *     info level
   * @param elapsed <code>true</code> to log the elapsed time for executing the test; <code>false
   *     </code> to not log it
   */
  public TestDelimiter(boolean stdout, boolean elapsed) {
    this.stdout = stdout;
    this.elapsed = elapsed;
  }

  /**
   * Constructs a test delimiter.
   *
   * @param annotation the test delimiter annotation
   */
  public TestDelimiter(org.codice.junit.TestDelimiter annotation) {
    this(annotation.stdout(), annotation.elapsed());
  }

  @Override
  public Statement applyAfterSnapshot(Statement base, FrameworkMethod method, Object target) {
    return super.applyAfterSnapshot(
        new Statement() {
          long start = -1L;

          @Override
          public void evaluate() throws Throwable {
            start = System.nanoTime();
            String msg = null;

            try {
              msg =
                  log(
                      null,
                      "Test: %s.%s(%s:%d)",
                      target.getClass().getName(),
                      getName(),
                      getSourceFile(),
                      getLineNumber());
              base.evaluate();
              log("Successful", msg);
            } catch (
                @SuppressWarnings("deprecation")
                org.junit.internal.AssumptionViolatedException e) {
              log("Skipped", msg);
              throw e;
            } catch (VirtualMachineError e) {
              throw e;
            } catch (Throwable t) {
              // check if that error was expected because if it is ... that is not a failure
              final Test test = method.getAnnotation(Test.class);

              if ((test == null) || !test.expected().isAssignableFrom(t.getClass())) {
                log("Failed: " + t.getClass().getName(), msg);
              } else {
                log("Successful", msg);
              }
              throw t;
            }
          }

          private String log(@Nullable String status, String format, Object... args) {
            String msg = String.format(format, args);

            if (status != null) {
              msg += ": ";
              if (elapsed) {
                final long duration = System.nanoTime() - start;

                msg +=
                    "["
                        + DurationFormatUtils.formatDuration(
                            TimeUnit.NANOSECONDS.toMillis(duration), "HH:mm:ss.S")
                        + "] ";
              }
              msg += status;
            }
            final String line = StringUtils.repeat('=', msg.length());

            if (stdout) {
              System.out.printf("%s%n%s%n%s%n", line, msg, line);
            } else {
              LOGGER.info("{}\n{}\n{}", line, msg, line);
            }
            return msg;
          }
        },
        method,
        target);
  }
}
