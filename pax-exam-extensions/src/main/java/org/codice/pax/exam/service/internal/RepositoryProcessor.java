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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.features.Repository;
import org.codice.pax.exam.service.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility classes used to process repositories by comparing the snapshot version with the memory
 * one and determining if it should be added or removed.
 */
public class RepositoryProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryProcessor.class);

  private final FeaturesService service;

  /**
   * Constructs a new repository processor.
   *
   * @param service the features service to use
   */
  public RepositoryProcessor(FeaturesService service) {
    this.service = service;
  }

  /**
   * Gets all installed repositories.
   *
   * @param operation the operation for which we are retrieving the repositories
   * @return tan array of all available repositories
   */
  public Repository[] listRepositories(String operation) {
    try {
      final Repository[] repositories = service.listRepositories();

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Memory repositories: {}",
            Stream.of(repositories)
                .map(r -> String.format("%s: %s", r.getName(), r.getURI()))
                .collect(Collectors.joining(", ")));
      }
      return repositories;
    } catch (Exception e) {
      throw new ServiceException(
          operation + " error: failed to retrieve repositories; " + e.getMessage(), e);
    }
  }

  /**
   * Installs the specified repository.
   *
   * @param report the report where to record errors if unable to install the repository
   * @param uri the URI of the repository to install
   * @return <code>true</code> if the repository was installed successfully; <code>false</code>
   *     otherwise
   */
  public boolean installRepository(SnapshotReport report, URI uri) {
    // do not have features installed; this will be handled separately
    return run(report, uri, Operation.INSTALL, () -> service.addRepository(uri, false));
  }

  /**
   * Uninstalls the specified repository.
   *
   * @param report the report where to record errors if unable to uninstall the repository
   * @param uri the URI of the repository to uninstall
   * @return <code>true</code> if the repository was uninstalled successfully; <code>false</code>
   *     otherwise
   */
  public boolean uninstallRepository(SnapshotReport report, URI uri) {
    return run(report, uri, Operation.UNINSTALL, () -> service.removeRepository(uri));
  }

  /**
   * Processes repositories by recording tasks to add or remove repositories that were originally in
   * the corresponding state.
   *
   * @param profile the profile where to retrieve the set of repositories from the snapshot system
   * @param tasks the task list where to record tasks to be executed
   */
  public void processRepositoriesAndPopulateTaskList(Profile profile, TaskList tasks) {
    LOGGER.trace("Processing repositories");
    final Map<URI, Repository> leftover =
        processSnapshotRepositoriesAndPopulateTaskList(profile, tasks);

    if (!profile.shouldOnlyProcessSnapshot()) {
      processLeftoverRepositoriesAndPopulateTaskList(leftover, tasks);
    }
  }

  /**
   * Processes snapshot repositories by recording tasks to add or remove repositories that were
   * originally in the corresponding state.
   *
   * <p><i>Note:</i> Any repositories found in memory are removed from the provided map since they
   * have been processed.
   *
   * @param profile the profile where to retrieve the set of features from the snapshot
   * @param tasks the task list where to record tasks to be executed
   * @return the set of repositories in memory not defined in the provided snapshot
   */
  public Map<URI, Repository> processSnapshotRepositoriesAndPopulateTaskList(
      Profile profile, TaskList tasks) {
    LOGGER.trace("Processing snapshot repositories");
    final Map<URI, Repository> repositories =
        Stream.of(listRepositories("Restore"))
            .collect(Collectors.toMap(Repository::getURI, Function.identity()));

    profile
        .repositories()
        .forEach(
            uri ->
                processRepositoryAndPopulateTaskList(
                    uri,
                    repositories.remove(uri), // remove from repositories when we find it
                    tasks));
    return repositories;
  }

  /**
   * Processes repositories that were left over after having dealt with what was snapshot. The
   * implementation will try to uninstall all of them.
   *
   * @param repositories the repositories in memory that were not snapshot
   * @param tasks the task list where to record tasks to be executed
   */
  public void processLeftoverRepositoriesAndPopulateTaskList(
      Map<URI, Repository> repositories, TaskList tasks) {
    LOGGER.trace("Processing leftover repositories");
    for (final URI uri : repositories.keySet()) {
      processUninstalledRepositoryAndPopulateTaskList(uri, tasks);
    }
  }

  /**
   * Processes the specified repository by comparing its state in memory to the one from the
   * snapshot and determining if it needs to be added.
   *
   * @param snapshotRepository the original repository information
   * @param repository the current repository from memory or <code>null</code> if it is not
   *     installed
   * @param tasks the task list where to record tasks to be executed
   */
  public void processRepositoryAndPopulateTaskList(
      URI snapshotRepository, @Nullable Repository repository, TaskList tasks) {
    if (repository == null) {
      processMissingRepositoryAndPopulateTaskList(snapshotRepository, tasks);
    } // else - nothing to do as it seems to be still installed
  }

  /**
   * Processes the specified repository for installation since it was missing from memory.
   *
   * @param uri the original repository URI
   * @param tasks the task list where to record tasks to be executed
   */
  public void processMissingRepositoryAndPopulateTaskList(URI uri, TaskList tasks) {
    tasks.add(Operation.INSTALL, uri.toString(), r -> installRepository(r, uri));
  }

  /**
   * Processes the specified repository for uninstallation.
   *
   * @param uri the current repository URI from memory
   * @param tasks the task list where to record tasks to be executed
   */
  public void processUninstalledRepositoryAndPopulateTaskList(URI uri, TaskList tasks) {
    tasks.add(Operation.UNINSTALL, uri.toString(), r -> uninstallRepository(r, uri));
  }

  private boolean run(
      SnapshotReport report, URI uri, Operation operation, ThrowingRunnable<Exception> task) {
    final String attempt = report.getRepositoryAttemptString(operation, uri);
    final String operating = operation.getOperatingName();

    LOGGER.info("{} Karaf repository: '{}'{}", operating, uri, attempt);
    try {
      task.run();
    } catch (Exception e) {
      report.recordOnFinalAttempt(
          new ServiceException(
              "Reset error: failed to "
                  + operation.name().toLowerCase()
                  + " repository ["
                  + uri
                  + "]; "
                  + e.getMessage(),
              e));
      return false;
    }
    return true;
  }
}
