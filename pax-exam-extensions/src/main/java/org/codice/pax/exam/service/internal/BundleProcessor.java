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

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.codice.pax.exam.service.ServiceException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class used to process bundles by comparing the snapshot version with the memory one and
 * determining if it should be started, stopped, installed, or uninstalled.
 */
public class BundleProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(BundleProcessor.class);

  private final BundleContext context;

  /**
   * Creates a new bundle processor with the specified bundle context.
   *
   * @param context the bundle context from which to retrieve bundles
   */
  public BundleProcessor(BundleContext context) {
    this.context = context;
  }

  /**
   * Gets all available bundles from memory.
   *
   * @return an array of all available bundles
   */
  public Bundle[] listBundles() {
    final Bundle[] bundles = context.getBundles();

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "Memory bundles: {}",
          Stream.of(bundles)
              .map(b -> String.format("%s (%s)", b, BundleSnapshot.getStateString(b)))
              .collect(Collectors.joining(", ")));
    }
    return bundles;
  }

  /**
   * Installs the specified bundle.
   *
   * @param report the report where to record errors if unable to install the bundle
   * @param bundle the bundle to install
   * @return <code>true</code> if the bundle was installed successfully; <code>false</code>
   *     otherwise
   */
  public boolean installBundle(SnapshotReport report, Bundle bundle) {
    return run(
        report, bundle, Operation.INSTALL, () -> context.installBundle(bundle.getLocation()));
  }

  /**
   * Installs the specified bundle.
   *
   * @param report the report where to record errors if unable to install the bundle
   * @param name the name of the bundle to install
   * @param location the location for the bundle to install
   * @return <code>true</code> if the bundle was installed successfully; <code>false</code>
   *     otherwise
   */
  public boolean installBundle(SnapshotReport report, String name, String location) {
    return run(
        report,
        name,
        BundleSnapshot.UNINSTALLED_STATE_STRING,
        Operation.INSTALL,
        () -> context.installBundle(location));
  }

  /**
   * Uninstalls the specified bundle.
   *
   * @param report the report where to record errors if unable to uninstall the bundle
   * @param bundle the bundle to uninstall
   * @return <code>true</code> if the bundle was uninstalled successfully; <code>false</code>
   *     otherwise
   */
  public boolean uninstallBundle(SnapshotReport report, Bundle bundle) {
    return run(report, bundle, Operation.UNINSTALL, bundle::uninstall);
  }

  /**
   * Starts the specified bundle.
   *
   * @param report the report where to record errors if unable to start the bundle
   * @param bundle the bundle to start
   * @return <code>true</code> if the bundle was started successfully; <code>false</code> otherwise
   */
  public boolean startBundle(SnapshotReport report, Bundle bundle) {
    return run(report, bundle, Operation.START, bundle::start);
  }

  /**
   * Stops the specified bundle.
   *
   * @param report the report where to record errors if unable to stop the bundle
   * @param bundle the bundle to stop
   * @return <code>true</code> if the bundle was stopped successfully; <code>false</code> otherwise
   */
  public boolean stopBundle(SnapshotReport report, Bundle bundle) {
    return run(report, bundle, Operation.STOP, bundle::stop);
  }

  /**
   * Processes bundles by recording tasks to start, stop, install, or uninstall bundles that were
   * originally in the corresponding state.
   *
   * @param profile the profile where to retrieve the set of bundles from the snapshot
   * @param tasks the task list where to record tasks to be executed
   */
  public void processBundlesAndPopulateTaskList(Profile profile, TaskList tasks) {
    LOGGER.trace("Processing bundles");
    final Map<String, Bundle> leftover = processSnapshotBundlesAndPopulateTaskList(profile, tasks);

    if (!profile.shouldOnlyProcessSnapshot()) {
      processLeftoverBundlesAndPopulateTaskList(leftover, tasks);
    }
  }

  /**
   * Processes bundles that were exported by recording tasks to start, stop, install, or uninstall
   * bundles that were originally in the corresponding state.
   *
   * @param profile the profile where to retrieve the set of bundles from the snapshot
   * @param tasks the task list where to record tasks to be executed
   * @return the set of bundles in memory not defined in the provided snapshot
   */
  public Map<String, Bundle> processSnapshotBundlesAndPopulateTaskList(
      Profile profile, TaskList tasks) {
    LOGGER.trace("Processing snapshot bundles");
    final Map<String, Bundle> bundles =
        Stream.of(listBundles())
            .collect(Collectors.toMap(BundleSnapshot::getFullName, Function.identity()));

    profile
        .bundles()
        .forEach(
            b ->
                processBundleAndPopulateTaskList(
                    b,
                    bundles.remove(b.getFullName()), // remove from bundles when we find it
                    tasks));
    return bundles;
  }

  /**
   * Processes bundles that were left over after having dealt with what was snapshot. The
   * implementation will try to uninstall all of them.
   *
   * @param bundles the set of bundles in memory that were not snapshot
   * @param tasks the task list where to record tasks to be executed
   */
  public void processLeftoverBundlesAndPopulateTaskList(
      Map<String, Bundle> bundles, TaskList tasks) {
    LOGGER.trace("Processing leftover bundles");
    for (final Bundle bundle : bundles.values()) {
      if (!processUninstalledBundleAndPopulateTaskList(bundle, tasks) && LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Skipping bundle '{}'; already {}",
            BundleSnapshot.getFullName(bundle),
            BundleSnapshot.getSimpleState(bundle));
      }
    }
  }

  /**
   * Processes the specified bundle by comparing its state in memory to the one from the snapshot
   * and determining if it needs to be uninstalled, installed, started, or stopped.
   *
   * <p><i>Note:</i> Stopped or installed are handled the same way. Started is handled as making the
   * bundle active.
   *
   * @param snapshotBundle the original bundle information
   * @param bundle the current bundle from memory or <code>null</code> if it is not installed
   * @param tasks the task list where to record tasks to be executed
   */
  public void processBundleAndPopulateTaskList(
      BundleSnapshot snapshotBundle, @Nullable Bundle bundle, TaskList tasks) {
    if (bundle == null) {
      processMissingBundleAndPopulateTaskList(snapshotBundle, tasks);
    } else {
      switch (snapshotBundle.getSimpleState()) {
        case UNINSTALLED:
          processUninstalledBundleAndPopulateTaskList(bundle, tasks);
          break;
        case ACTIVE:
          processActiveBundleAndPopulateTaskList(bundle, tasks);
          break;
        case INSTALLED:
        default: // assume any other states we don't know about is treated as if we should stop
          processInstalledBundleAndPopulateTaskList(bundle, tasks);
          break;
      }
    }
  }

  /**
   * Processes the specified bundle for installation since it was missing from memory.
   *
   * <p><i>Note:</i> A missing bundle will only be installed in this attempt which will not be
   * setting it to the state it was (unless it was uninstalled). The change from installed to
   * uninstalled or active will require another process attempt as we only want to deal with one
   * state change at a time. The next processing round will see the state of the bundle in memory as
   * installed instead of missing like it is right now such that it can then be finally uninstalled
   * or started as it was snapshot.
   *
   * @param bundle the original bundle information
   * @param tasks the task list where to record tasks to be executed
   */
  public void processMissingBundleAndPopulateTaskList(BundleSnapshot bundle, TaskList tasks) {
    // we need to force an install and on the next round, whatever it used to be.
    // Even if it was uninstall because we need to reserve its spot in the bundle order
    final String name = bundle.getFullName();

    tasks.add(Operation.INSTALL, name, r -> installBundle(r, name, bundle.getLocation()));
  }

  /**
   * Processes the specified bundle for uninstallation if the bundle in memory is not uninstalled.
   *
   * @param bundle the current bundle from memory
   * @param tasks the task list where to record tasks to be executed
   * @return <code>true</code> if processed; <code>false</code> otherwise
   */
  public boolean processUninstalledBundleAndPopulateTaskList(Bundle bundle, TaskList tasks) {
    final BundleSnapshot.SimpleState state = BundleSnapshot.getSimpleState(bundle);

    if (state != BundleSnapshot.SimpleState.UNINSTALLED) {
      final String name = BundleSnapshot.getFullName(bundle);

      tasks.add(Operation.UNINSTALL, name, r -> uninstallBundle(r, bundle));
      return true;
    }
    return false;
  }

  /**
   * Processes the specified bundle for activation if the bundle in memory is not installed or
   * active.
   *
   * <p><i>Note:</i> An uninstalled bundle will only be installed in this attempt which will not be
   * setting it to the active state. The change from installed to active will require another
   * process attempt as we only want to deal with one state change at a time. The next processing
   * round will see the state of the bundle in memory as installed instead of uninstalled like it is
   * right now such that it can then be finally started as it was snapshot.
   *
   * @param bundle the current bundle from memory
   * @param tasks the task list where to record tasks to be executed
   */
  public void processActiveBundleAndPopulateTaskList(Bundle bundle, TaskList tasks) {
    final BundleSnapshot.SimpleState state = BundleSnapshot.getSimpleState(bundle);
    final String name = BundleSnapshot.getFullName(bundle);

    if (state == BundleSnapshot.SimpleState.UNINSTALLED) {
      // we need to first install it and on the next round, start it
      tasks.add(Operation.INSTALL, name, r -> installBundle(r, bundle));
    } else if (state != BundleSnapshot.SimpleState.ACTIVE) {
      tasks.add(Operation.START, name, r -> startBundle(r, bundle));
    }
  }

  /**
   * Processes the specified bundle for installation if the bundle in memory is not uninstalled.
   *
   * <p><i>Note:</i> A bundle that is active in memory will need to be stopped in order to be in the
   * same installed state it was snapshot.
   *
   * @param bundle the current bundle from memory
   * @param tasks the task list where to record tasks to be executed
   */
  public void processInstalledBundleAndPopulateTaskList(Bundle bundle, TaskList tasks) {
    final BundleSnapshot.SimpleState state = BundleSnapshot.getSimpleState(bundle);
    final String name = BundleSnapshot.getFullName(bundle);

    if (state == BundleSnapshot.SimpleState.UNINSTALLED) {
      tasks.add(Operation.INSTALL, name, r -> installBundle(r, bundle));
    } else if (state == BundleSnapshot.SimpleState.ACTIVE) {
      tasks.add(Operation.STOP, name, r -> stopBundle(r, bundle));
    }
  }

  private boolean run(
      SnapshotReport report,
      Bundle bundle,
      Operation operation,
      ThrowingRunnable<BundleException> task) {
    return run(
        report,
        BundleSnapshot.getFullName(bundle),
        BundleSnapshot.getStateString(bundle),
        operation,
        task);
  }

  private boolean run(
      SnapshotReport report,
      String name,
      String state,
      Operation operation,
      ThrowingRunnable<BundleException> task) {
    final String attempt = report.getBundleAttemptString(operation, name);
    final String operating = operation.getOperatingName();

    LOGGER.info("{} Karaf bundle '{}'{}", operating, name, attempt);
    try {
      task.run();
    } catch (IllegalStateException | BundleException | SecurityException e) {
      report.recordOnFinalAttempt(
          new ServiceException(
              "Reset error: failed to "
                  + operation.name().toLowerCase()
                  + " bundle ["
                  + name
                  + "] from state ["
                  + state
                  + "]; "
                  + e.getMessage(),
              e));
      return false;
    }
    return true;
  }
}
