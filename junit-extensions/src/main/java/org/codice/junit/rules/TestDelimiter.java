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
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
          @Override
          public void evaluate() throws Throwable {
            final long start = System.nanoTime();
            String msg = null;

            try {
              msg = log("Test: %s (%s:%d)", getName(), getSourceFile(), getLineNumber());
              base.evaluate();
            } finally {
              if (elapsed && (msg != null)) {
                final long duration = System.nanoTime() - start;

                log(
                    msg
                        + ": "
                        + DurationFormatUtils.formatDuration(
                            TimeUnit.NANOSECONDS.toMillis(duration), "HH:mm:ss.S"));
              }
            }
          }
        },
        method,
        target);
  }

  private String log(String format, Object... args) {
    final String msg = String.format(format, args);
    final String line = StringUtils.repeat('=', msg.length());

    if (stdout) {
      System.out.printf("%n%s%n%s%n%s%n", line, msg, line);
    } else {
      LOGGER.info("\n{}\n{}\n{}", line, msg, line);
    }
    return msg;
  }
}
