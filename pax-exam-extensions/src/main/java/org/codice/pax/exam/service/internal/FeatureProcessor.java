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

import com.google.common.collect.ImmutableMap;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeatureState;
import org.apache.karaf.features.FeaturesService;
import org.codice.pax.exam.service.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility classes used to process features by comparing the snapshot version with the memory one
 * and determining if it should be started, resolved, installed, or uninstalled.
 *
 * <p><i>Note:</i> A feature in the resolved state is considered to be stopped.
 */
public class FeatureProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureProcessor.class);

  private static final EnumSet<FeaturesService.Option> NO_AUTO_REFRESH =
      EnumSet.of(FeaturesService.Option.NoAutoRefreshBundles);

  private final FeaturesService service;

  /**
   * Constructs a new feature processor.
   *
   * @param service the features service to use
   */
  public FeatureProcessor(FeaturesService service) {
    this.service = service;
  }

  /**
   * Gets all available features from memory.
   *
   * @param operation the operation for which we are retrieving the features
   * @return an array of all available features
   * @throws ServiceException if an error occurs while retrieving the features from memory
   */
  public Feature[] listFeatures(String operation) {
    try {
      final Feature[] features = service.listFeatures();

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Memory features: {}",
            Stream.of(features)
                .map(
                    f ->
                        String.format(
                            "%s (%s/%s)",
                            f,
                            service.getState(f.getId()),
                            (service.isRequired(f) ? "required" : "not required")))
                .collect(Collectors.joining(", ")));
      }
      return features;
    } catch (Exception e) {
      throw new ServiceException(
          operation + " error: failed to retrieve features; " + e.getMessage(), e);
    }
  }

  /**
   * Installs the specified set of features.
   *
   * @param report the report where to record errors if unable to install the features
   * @param features the features to install keyed by the region they should be installed in
   * @return <code>true</code> if the features were installed successfully; <code>false</code>
   *     otherwise
   */
  public boolean installFeatures(
      SnapshotReport report, Map<String, Set<FeatureSnapshot>> features) {
    return features
        .entrySet()
        .stream()
        .allMatch(e -> installFeatures(report, e.getKey(), e.getValue()));
  }

  /**
   * Installs the specified set of features in the specified region.
   *
   * @param report the report where to record errors if unable to install the features
   * @param region the region where to install the features
   * @param features the features to install
   * @return <code>true</code> if the features were installed successfully; <code>false</code>
   *     otherwise
   */
  public boolean installFeatures(
      SnapshotReport report, String region, Set<FeatureSnapshot> features) {
    final Set<String> ids =
        features.stream().map(FeatureSnapshot::getId).collect(Collectors.toSet());

    return run(
        report,
        region,
        ids.stream(),
        Operation.INSTALL,
        () -> service.installFeatures(ids, region, FeatureProcessor.NO_AUTO_REFRESH));
  }

  /**
   * Uninstalls the specified set of features.
   *
   * @param report the report where to record errors if unable to uninstall the features
   * @param features the features to install keyed by the region they should be uninstalled in
   * @return <code>true</code> if the features were uninstalled successfully; <code>false</code>
   *     otherwise
   */
  public boolean uninstallFeatures(SnapshotReport report, Map<String, Set<Feature>> features) {
    return features
        .entrySet()
        .stream()
        .allMatch(e -> uninstallFeatures(report, e.getKey(), e.getValue()));
  }

  /**
   * Uninstalls the specified set of features in the specified region.
   *
   * @param report the report where to record errors if unable to uninstall the features
   * @param region the region where to uninstall the features
   * @param features the features to uninstall
   * @return <code>true</code> if the features were uninstalled successfully; <code>false</code>
   *     otherwise
   */
  public boolean uninstallFeatures(SnapshotReport report, String region, Set<Feature> features) {
    // ----------------------------------------------------------------------------------------
    // to work around a bug in Karaf where for whatever reasons, it checks if a feature is
    // required to determine if it is installed and therefore can be uninstalled, we will first
    // go through and mark them all required
    //
    // see Karaf class: org.apache.karaf.features.internal.service.FeaturesServiceImpl.java
    // in the uninstallFeature() method where it checks the requirements table and if not found
    // in there, it later reports it is not installed even though it is actually installed
    //
    // Update: turns out that what Karaf means by install is "require this feature" and by uninstall
    // is "no longer require this feature". That being said, the uninstallFeature() does a bit more
    // than simply marking the feature no longer required since it actually also removes all traces
    // of the feature internally. So to properly remove a feature, it has to first be marked
    // required. which can technically be done either by updating the requirements for the feature
    // (as done below) or actually calling installFeature() again. We opted for the former since we
    // already deal with updating requirements to match the 'required' state of the feature from
    // the original system
    // ----------------------------------------------------------------------------------------
    final Set<String> ids = features.stream().map(Feature::getId).collect(Collectors.toSet());
    final Set<String> requirements =
        features.stream().map(FeatureSnapshot::toRequirement).collect(Collectors.toSet());

    return run(
        report,
        region,
        ids.stream(),
        Operation.UNINSTALL,
        () -> // first make sure to mark all of them required as uninstallFeatures() only works on
            // required features and goes beyond simply marking them not required
            service.addRequirements(
                ImmutableMap.of(region, requirements), FeatureProcessor.NO_AUTO_REFRESH),
        () -> service.uninstallFeatures(ids, region, FeatureProcessor.NO_AUTO_REFRESH));
  }

  /**
   * Updates the specified features requirements to mark them required or not.
   *
   * @param report the report where to record errors if unable to update the features
   * @param features the features to update keyed by the region they should be updated in
   * @return <code>true</code> if the features were updated successfully; <code>false</code>
   *     otherwise
   */
  public boolean updateFeaturesRequirements(
      SnapshotReport report, Map<String, Set<FeatureSnapshot>> features) {
    return features
        .entrySet()
        .stream()
        .allMatch(e -> updateFeaturesRequirements(report, e.getKey(), e.getValue()));
  }

  /**
   * Updates the specified features requirements to mark them required or not.
   *
   * @param report the report where to record errors if unable to update the features
   * @param region the region where to update the features
   * @param features the features to update
   * @return <code>true</code> if the features were updated successfully; <code>false</code>
   *     otherwise
   */
  public boolean updateFeaturesRequirements(
      SnapshotReport report, String region, Set<FeatureSnapshot> features) {
    return run(
        report,
        region,
        features.stream().map(FeatureSnapshot::getId),
        Operation.UPDATE,
        features
            .stream()
            .collect(
                Collectors.groupingBy(
                    FeatureSnapshot::isRequired,
                    Collectors.mapping(FeatureSnapshot::toRequirement, Collectors.toSet())))
            .entrySet()
            .stream()
            .map(requirementsToUpdate -> updateFeaturesRequirements(region, requirementsToUpdate))
            .toArray(ThrowingRunnable[]::new));
  }

  /**
   * Starts the specified feature.
   *
   * @param report the report where to record errors if unable to start the feature
   * @param feature the feature to start
   * @param region the region where the feature resides
   * @return <code>true</code> if the feature was started successfully; <code>false</code> otherwise
   */
  public boolean startFeature(SnapshotReport report, Feature feature, String region) {
    return run(
        report,
        feature,
        Operation.START,
        id ->
            service.updateFeaturesState(
                ImmutableMap.of(region, ImmutableMap.of(id, FeatureState.Started)),
                FeatureProcessor.NO_AUTO_REFRESH));
  }

  /**
   * Stops the specified feature by moving it back to the resolved state.
   *
   * @param report the report where to record errors if unable to stop the feature
   * @param feature the feature to stop
   * @param region the region where the feature resides
   * @return <code>true</code> if the feature was stopped successfully; <code>false</code> otherwise
   */
  public boolean stopFeature(SnapshotReport report, Feature feature, String region) {
    return run(
        report,
        feature,
        Operation.STOP,
        id ->
            service.updateFeaturesState(
                ImmutableMap.of(region, ImmutableMap.of(id, FeatureState.Resolved)),
                FeatureProcessor.NO_AUTO_REFRESH));
  }

  /**
   * Processes features by recording tasks to start, stop, install, or uninstall features that were
   * originally in the corresponding state.
   *
   * @param profile the profile where to retrieve the set of features from the snapshot
   * @param tasks the task list where to record tasks to be executed
   */
  public void processFeaturesAndPopulateTaskList(Profile profile, TaskList tasks) {
    LOGGER.trace("Processing features");
    final Map<String, Feature> leftover =
        processSnapshotFeaturesAndPopulateTaskList(profile, tasks);

    if (!profile.shouldOnlyProcessSnapshot()) {
      processLeftoverFeaturesAndPopulateTaskList(leftover, tasks);
    }
  }

  /**
   * Processes snapshot features by recording tasks to start, stop, install, or uninstall features
   * that were originally in the corresponding state.
   *
   * @param profile the profile where to retrieve the set of features from the snapshot
   * @param tasks the task list where to record tasks to be executed
   * @return a map of all features from memory not defined in the provided snapshot
   */
  public Map<String, Feature> processSnapshotFeaturesAndPopulateTaskList(
      Profile profile, TaskList tasks) {
    LOGGER.trace("Processing snapshot features");
    // map sorted to get latest versions first for a given feature name
    final Map<String, Feature> features = new TreeMap<>(Comparator.reverseOrder());

    Stream.of(listFeatures("Restore")).forEach(f -> features.put(f.getId(), f));
    profile
        .features()
        .forEach(
            f -> {
              Feature feature = null;

              if (!f.hasVersion()) { // we shall remove the latest version we find
                final String name = f.getName();

                for (final Iterator<Feature> i = features.values().iterator(); i.hasNext(); ) {
                  final Feature ifeature = i.next();

                  if (ifeature.getName().equals(name)) {
                    i.remove(); // remove from features when we find it first and break out
                    feature = ifeature;
                    break;
                  }
                }
              } else {
                feature = features.remove(f.getId()); // remove from features when we find it
              }
              processFeatureAndPopulateTaskList(f, feature, tasks);
            });
    return features;
  }

  /**
   * Processes features that were left over from memory after having dealt with what was snapshot.
   * The implementation will try to uninstall all of them.
   *
   * @param features the set of features in memory that were not snapshot
   * @param tasks the task list where to record tasks to be executed
   */
  public void processLeftoverFeaturesAndPopulateTaskList(
      Map<String, Feature> features, TaskList tasks) {
    LOGGER.trace("Processing leftover features");
    for (final Feature feature : features.values()) {
      if (!processUninstalledFeatureAndPopulateTaskList(feature, tasks)
          && LOGGER.isTraceEnabled()) {
        LOGGER.trace("Skipping feature '{}'; already uninstalled", feature.getId());
      }
    }
  }

  /**
   * Processes the specified feature by comparing its state in memory to the one from the snapshot
   * and determining if it needs to be uninstalled, installed, started, or stopped.
   *
   * @param snapshotFeature the original feature information
   * @param feature the current feature from memory or <code>null</code> if it is not installed
   * @param tasks the task list where to record tasks to be executed
   */
  public void processFeatureAndPopulateTaskList(
      FeatureSnapshot snapshotFeature, @Nullable Feature feature, TaskList tasks) {
    if (feature == null) {
      processMissingFeatureAndPopulateTaskList(snapshotFeature, tasks);
    } else {
      switch (snapshotFeature.getState()) {
        case Uninstalled:
          processUninstalledFeatureAndPopulateTaskList(feature, tasks);
          break;
        case Installed:
          processInstalledFeatureAndPopulateTaskList(snapshotFeature, feature, tasks);
          break;
        case Started:
          processStartedFeatureAndPopulateTaskList(snapshotFeature, feature, tasks);
          break;
        case Resolved:
        default: // assume any other states we don't know about is treated as if we should stop
          processResolvedFeatureAndPopulateTaskList(snapshotFeature, feature, tasks);
          break;
      }
    }
  }

  /**
   * Processes the specified feature for installation since it was missing from memory.
   *
   * @param feature the original feature information
   * @param tasks the task list where to record tasks to be executed
   */
  public void processMissingFeatureAndPopulateTaskList(FeatureSnapshot feature, TaskList tasks) {
    if (feature.getState() != FeatureState.Uninstalled) {
      addCompoundInstallTaskFor(feature, tasks);
    }
  }

  /**
   * Processes the specified feature for uninstallation if the feature in memory is not uninstalled.
   *
   * @param feature the current feature from memory
   * @param tasks the task list where to record tasks to be executed
   * @return <code>true</code> if processed; <code>false</code> otherwise
   */
  public boolean processUninstalledFeatureAndPopulateTaskList(Feature feature, TaskList tasks) {
    final String id = feature.getId();
    final FeatureState state = service.getState(id);

    if (state != FeatureState.Uninstalled) {
      addCompoundUninstallTaskFor(feature, tasks);
      return true;
    }
    return false;
  }

  /**
   * Processes the specified feature for installation if the feature in memory is not uninstalled.
   *
   * <p><i>Note:</i> A feature that is started in memory will need to be stopped in order to be in
   * the same resolved state it was snapshot.
   *
   * @param snapshotFeature the original feature information
   * @param feature the current feature from memory
   * @param tasks the task list where to record tasks to be executed
   */
  public void processInstalledFeatureAndPopulateTaskList(
      FeatureSnapshot snapshotFeature, Feature feature, TaskList tasks) {
    final String id = feature.getId();
    final FeatureState state = service.getState(id);

    if (state == FeatureState.Uninstalled) {
      addCompoundInstallTaskFor(snapshotFeature, tasks);
      return;
    } else if (state == FeatureState.Started) {
      // should use feature's region but we cannot figure out how to get it
      tasks.add(Operation.STOP, id, r -> stopFeature(r, feature, snapshotFeature.getRegion()));
    }
    final Boolean required = snapshotFeature.isRequired();

    if ((required != null) && (required != service.isRequired(feature))) {
      addCompoundUpdateTaskFor(snapshotFeature, feature, tasks);
    }
  }

  /**
   * Processes the specified feature for resolution.
   *
   * <p><i>Note:</i> If the feature was uninstalled, it will be installed and then later stopped to
   * get to the resolved state. The change from installed to stopped will require another process
   * attempt as we only want to deal with one state change at a time. The next processing round will
   * see the state of the feature in memory as installed instead of uninstalled like it is right now
   * such that it can then be finally stopped to be moved into the resolved state as it was on the
   * original system. Otherwise, if it is not in the resolved state, it will simply be stopped.
   *
   * @param snapshotFeature the original feature information
   * @param feature the current feature from memory
   * @param tasks the task list where to record tasks to be executed
   */
  public void processResolvedFeatureAndPopulateTaskList(
      FeatureSnapshot snapshotFeature, Feature feature, TaskList tasks) {
    final String id = feature.getId();
    final FeatureState state = service.getState(id);

    if (state == FeatureState.Uninstalled) {
      // we need to first install it and on the next round, stop it
      addCompoundInstallTaskFor(snapshotFeature, tasks);
      return;
    } else if (state != FeatureState.Resolved) {
      // should use feature's region but we cannot figure out how to get it
      tasks.add(Operation.STOP, id, r -> stopFeature(r, feature, snapshotFeature.getRegion()));
    }
    final Boolean required = snapshotFeature.isRequired();

    if ((required != null) && (required != service.isRequired(feature))) {
      addCompoundUpdateTaskFor(snapshotFeature, feature, tasks);
    }
  }

  /**
   * Processes the specified feature for starting.
   *
   * <p><i>Note:</i> If the feature was uninstalled, it will be installed and then later started to
   * get to the started state. The change from installed to started will require another process
   * attempt as we only want to deal with one state change at a time. The next processing round will
   * see the state of the feature in memory as installed instead of uninstalled like it is right
   * such that it can be finally started as it was on the original system. Otherwise, if it is not
   * in the started state, it will simply be started.
   *
   * @param snapshotFeature the original feature information
   * @param feature the current feature from memory
   * @param tasks the task list where to record tasks to be executed
   */
  public void processStartedFeatureAndPopulateTaskList(
      FeatureSnapshot snapshotFeature, Feature feature, TaskList tasks) {
    final String id = feature.getId();
    final FeatureState state = service.getState(id);

    if (state == FeatureState.Uninstalled) {
      // we need to first install it and on the next round, start it
      addCompoundInstallTaskFor(snapshotFeature, tasks);
      return;
    } else if (state != FeatureState.Started) {
      // should use feature's region but we cannot figure out how to get it
      tasks.add(Operation.START, id, r -> startFeature(r, feature, snapshotFeature.getRegion()));
    }
    final Boolean required = snapshotFeature.isRequired();

    if ((required != null) && (required != service.isRequired(feature))) {
      addCompoundUpdateTaskFor(snapshotFeature, feature, tasks);
    }
  }

  private void addCompoundInstallTaskFor(FeatureSnapshot feature, TaskList tasks) {
    // we shall verify if the installed feature is in the proper required state on a subsequent pass
    // and if not, update its requirements appropriately
    tasks
        .addIfAbsent(
            Operation.INSTALL,
            HashMap<String, Set<FeatureSnapshot>>::new,
            (features, r) -> installFeatures(r, features))
        .add(
            feature.getId(),
            features ->
                features.computeIfAbsent(feature.getRegion(), r -> new HashSet<>()).add(feature));
  }

  private void addCompoundUninstallTaskFor(Feature feature, TaskList tasks) {
    // since we do not know how to get the region for a feature yet, use the default one: ROOT
    tasks
        .addIfAbsent(
            Operation.UNINSTALL,
            HashMap<String, Set<Feature>>::new,
            (features, r) -> uninstallFeatures(r, features))
        .add(
            feature.getId(),
            features ->
                features
                    .computeIfAbsent(FeaturesService.ROOT_REGION, r -> new HashSet<>())
                    .add(feature));
  }

  @SuppressWarnings({
    "squid:S1172", /* currently unused until we can figure out how to retrieve the region from a feature */
    "PMD.UnusedFormalParameter" /* currently unused until we can figure out how to retrieve the region from a feature */
  })
  private void addCompoundUpdateTaskFor(
      FeatureSnapshot snapshotFeature, Feature feature, TaskList tasks) {
    // should use feature's region but we cannot figure out how to get it
    tasks
        .addIfAbsent(
            Operation.UPDATE,
            HashMap<String, Set<FeatureSnapshot>>::new,
            (features, r) -> updateFeaturesRequirements(r, features))
        .add(
            snapshotFeature.getId(),
            features ->
                features
                    .computeIfAbsent(snapshotFeature.getRegion(), r -> new HashSet<>())
                    .add(snapshotFeature));
  }

  private ThrowingRunnable<Exception> updateFeaturesRequirements(
      String region, Map.Entry<Boolean, Set<String>> requirements) {
    if (requirements.getKey()) {
      return () ->
          service.addRequirements(
              ImmutableMap.of(region, requirements.getValue()), FeatureProcessor.NO_AUTO_REFRESH);
    } else {
      return () ->
          service.removeRequirements(
              ImmutableMap.of(region, requirements.getValue()), FeatureProcessor.NO_AUTO_REFRESH);
    }
  }

  private boolean run(
      SnapshotReport report,
      Feature feature,
      Operation operation,
      ThrowingConsumer<String, Exception> task) {
    final String id = feature.getId();
    final String attempt = report.getFeatureAttemptString(operation, id);
    final String operating = operation.getOperatingName();

    LOGGER.info("{} Karaf feature '{}'{}", operating, id, attempt);
    try {
      task.accept(id);
    } catch (Exception e) {
      final String required = service.isRequired(feature) ? "required" : "not required";

      report.recordOnFinalAttempt(
          new ServiceException(
              "Task error: failed to "
                  + operation.name().toLowerCase()
                  + " feature ["
                  + id
                  + "] from state ["
                  + service.getState(id)
                  + "/"
                  + required
                  + "]; "
                  + e.getMessage(),
              e));
      return false;
    }
    return true;
  }

  private boolean run(
      SnapshotReport report,
      String region,
      Stream<String> ids,
      Operation operation,
      ThrowingRunnable<Exception>... tasks) {
    final String attempt = report.getFeatureAttemptString(operation, region);
    final String operating = operation.getOperatingName();

    ids.forEach(id -> LOGGER.info("{} Karaf feature '{}'{}", operating, id, attempt));
    try {
      for (final ThrowingRunnable<Exception> task : tasks) {
        task.run();
      }
    } catch (Exception e) {
      report.recordOnFinalAttempt(
          new ServiceException(
              "Task error: failed to "
                  + operation.name().toLowerCase()
                  + " features for region ["
                  + region
                  + "]; "
                  + e.getMessage(),
              e));
      return false;
    }
    return true;
  }
}
