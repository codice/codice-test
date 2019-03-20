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
package org.codice.pax.exam.service.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.codice.pax.exam.service.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotReport {
  private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotReport.class);

  private static final String ATTEMPTS_SUFFIX = " attempt)";

  /**
   * Keeps track of the number of times a particular operation was attempted on a particular
   * repository.
   */
  private final Map<Operation, Map<URI, AtomicInteger>> repositoriesAttempts =
      new EnumMap<>(Operation.class);

  /**
   * Keeps track of the number of times a particular operation was attempted on a particular
   * feature.
   */
  private final Map<Operation, Map<String, AtomicInteger>> featuresAttempts =
      new EnumMap<>(Operation.class);

  /**
   * Keeps track of the number of times a particular operation was attempted on a particular bundle.
   */
  private final Map<Operation, Map<String, AtomicInteger>> bundlesAttempts =
      new EnumMap<>(Operation.class);

  private volatile boolean finalAttempt = false;

  /** Tracks whether at least one recorded error was suppressed. */
  private boolean suppressedErrors = false;

  /** Tracks whether at least one task was recorded as part of this report. */
  private boolean recordedTasks = false;

  private final List<ServiceException> errors = new ArrayList<>();

  /** Creates a new snapshot report. */
  public SnapshotReport() {}

  public static String ordinal(int i) {
    final String[] suffixes =
        new String[] {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};

    switch (i % 100) {
      case 11:
      case 12:
      case 13:
        return i + "th";
      default:
        return i + suffixes[i % 10];
    }
  }

  /**
   * Resets all previously recorded info while indicating if this report is now associated with a
   * final attempt.
   *
   * @param finalAttempt <code>true</code> if this report is associated with a final attempt; <code>
   *      false</code> otherwise
   * @return this for chaining
   */
  public SnapshotReport reset(boolean finalAttempt) {
    this.finalAttempt = finalAttempt;
    this.repositoriesAttempts.clear();
    this.featuresAttempts.clear();
    this.bundlesAttempts.clear();
    this.suppressedErrors = false;
    this.recordedTasks = false;
    this.errors.clear();
    return this;
  }

  /**
   * Checks if this report is associated with a final attempt in which case errors recorded via
   * {@link #recordOnFinalAttempt(ServiceException)} should never be suppressed.
   *
   * @return <code>true</code> if this report is associated with a final attempt; <code>false
   *     </code> otherwise
   */
  public boolean isFinalAttempt() {
    return finalAttempt;
  }

  /**
   * Checks if at least one recorded error was suppressed any errors because the report was not
   * associated with a final attempt.
   *
   * @return <code>true</code> if at least one recorded error was suppressed; <code>false</code>
   *     otherwise
   */
  public boolean hasSuppressedErrors() {
    return suppressedErrors;
  }

  /**
   * Checks if at least one task was recorded as part of this report.
   *
   * @return <code>true</code> if at least one task was executed as part of this report; <code>false
   *     </code> otherwise
   */
  public boolean hasRecordedTasks() {
    return recordedTasks;
  }

  /**
   * Indicates a task is being recorded as part of this report.
   *
   * @return this for chaining
   */
  @SuppressWarnings(
      "PMD.DefaultPackage" /* designed to be called by TaskList within this package */)
  SnapshotReport recordTask() {
    this.recordedTasks = true;
    return this;
  }

  /**
   * Checks if this report indicates it was successful or errors were recorded.
   *
   * @return <code>true</code> if no errors were recorded; <code>false</code> if at least one error
   *     was recorded
   */
  public boolean wasSuccessful() {
    return errors.isEmpty();
  }

  /**
   * Runs the specified code and report whether or not it was successful.
   *
   * <p><i>Note:</i> This method will only account for errors generated from the point where the
   * provided code is called to the moment it terminates.
   *
   * @param code the code to run
   * @return <code>true</code> if no new errors were recorded while running the provided code;
   *     <code>false</code> if at least one error was recorded
   */
  public boolean wasSuccessful(Runnable code) {
    final int nerrs = errors.size();

    if (code != null) {
      code.run();
    }
    return (errors.size() == nerrs);
  }

  /**
   * Fails with the recorded error if the report indicate a failure.
   *
   * @throws ServiceException if at least one error was recorded
   */
  @Nullable
  public void failIfErrorsWereRecorded() {
    if (errors.isEmpty()) {
      return;
    }
    final ServiceException first = errors.get(0);

    errors.stream().skip(1).forEach(first::addSuppressed);
    throw first;
  }

  /**
   * Records the specified exception as a restore error.
   *
   * @param e the error to be recorded
   * @return this for chaining
   */
  public SnapshotReport record(ServiceException e) {
    errors.add(e);
    LOGGER.debug("Execution error: ", e);
    return this;
  }

  /**
   * Records the specified exception as an error only if this is the report is associated with a
   * final attempt otherwise suppress it.
   *
   * @param e the error to be recorded only if the report is associated with a final attempt
   * @return this for chaining
   */
  public SnapshotReport recordOnFinalAttempt(ServiceException e) {
    if (finalAttempt) {
      return record(e);
    } // else - suppressed it
    this.suppressedErrors = true;
    LOGGER.debug("Suppressed execution error: ", e);
    return this;
  }

  /**
   * Gets a trace string representing the number of attempts for a given repository operation.
   *
   * @param op the operation for which to get an attempt trace string
   * @param uri the attempt URI for which to get an attempt trace string
   * @return the corresponding attempt trace string
   */
  public String getRepositoryAttemptString(Operation op, URI uri) {
    final int attempt =
        repositoriesAttempts
            .computeIfAbsent(op, o -> new HashMap<>())
            .computeIfAbsent(uri, n -> new AtomicInteger(0))
            .incrementAndGet();

    return (attempt > 1)
        ? " (" + SnapshotReport.ordinal(attempt) + SnapshotReport.ATTEMPTS_SUFFIX
        : "";
  }

  /**
   * Gets a trace string representing the number of attempts for a given feature operation.
   *
   * @param op the operation for which to get an attempt trace string
   * @param id the attempt id for which to get an attempt trace string
   * @return the corresponding attempt trace string
   */
  public String getFeatureAttemptString(Operation op, String id) {
    final int attempt =
        featuresAttempts
            .computeIfAbsent(op, o -> new HashMap<>())
            .computeIfAbsent(id, n -> new AtomicInteger(0))
            .incrementAndGet();

    return (attempt > 1)
        ? " (" + SnapshotReport.ordinal(attempt) + SnapshotReport.ATTEMPTS_SUFFIX
        : "";
  }

  /**
   * Gets a trace string representing the number of attempts for a given bundle operation.
   *
   * @param op the operation for which to get an attempt trace string
   * @param id the attempt id for which to get an attempt trace string
   * @return the corresponding attempt trace string
   */
  public String getBundleAttemptString(Operation op, String id) {
    final int attempt =
        bundlesAttempts
            .computeIfAbsent(op, o -> new HashMap<>())
            .computeIfAbsent(id, n -> new AtomicInteger(0))
            .incrementAndGet();

    return (attempt > 1)
        ? " (" + SnapshotReport.ordinal(attempt) + SnapshotReport.ATTEMPTS_SUFFIX
        : "";
  }
}
