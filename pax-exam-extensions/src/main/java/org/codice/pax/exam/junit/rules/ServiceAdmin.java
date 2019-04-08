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
package org.codice.pax.exam.junit.rules;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Deque;
import java.util.Dictionary;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.karaf.bundle.core.BundleService;
import org.apache.karaf.bundle.core.BundleState;
import org.apache.karaf.features.DeploymentListener;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeatureState;
import org.apache.karaf.features.FeaturesListener;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.features.Repository;
import org.codice.junit.rules.EmptyStatement;
import org.codice.junit.rules.MethodRuleChain;
import org.codice.maven.MavenUrlReference;
import org.codice.maven.ResourceLoader;
import org.codice.pax.exam.service.ServiceException;
import org.codice.pax.exam.service.ServiceTimeoutException;
import org.codice.pax.exam.service.internal.BundleProcessor;
import org.codice.pax.exam.service.internal.BundleSnapshot;
import org.codice.pax.exam.service.internal.FeatureProcessor;
import org.codice.pax.exam.service.internal.FeatureSnapshot;
import org.codice.pax.exam.service.internal.Profile;
import org.codice.pax.exam.service.internal.RepositoryProcessor;
import org.codice.pax.exam.service.internal.SnapshotReport;
import org.codice.pax.exam.service.internal.TaskList;
import org.codice.test.commons.ReflectionUtils;
import org.codice.test.commons.ReflectionUtils.AnnotationEntry;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JUnit method rules behave similarly to Pax Exam support for the <code>@Inject</code>
 * annotation for {@link FeaturesService}, {@link BundleService}, and {@link BundleContext} services
 * while also providing support for restoring all repositories, features, and bundles after each
 * tests to their initial state as obtained before the test started. It also support starting up
 * and/or stopping features based on provided annotations and ensuring everything is ready before
 * proceeding with a test.
 */
@SuppressWarnings(
    "squid:S2176" /* name was chosen to be a replacement for Karaf's features service */)
public class ServiceAdmin extends MethodRuleChain
    implements FeaturesService, BundleService, BundleContext {
  /**
   * Holds a retry count.
   *
   * <p>Because it was seen with Karaf that sometimes installing or starting a feature or a bundle
   * might have side effects and install or start others which were not supposed to in the original
   * system. As such, we are going to attempt multiple times for each operations such that we can
   * account for side effects of other operations. In addition, because we are unable to determine a
   * proper order to process all features or bundles, it is possible that we end up attempting to
   * install a feature for which the dependencies have not yet been installed. We are therefore
   * forced to attempt multiple times hoping that dependencies are properly being handled by the
   * previous pass. Failures will be reported only on the last attempt. Retries will automatically
   * stop when all features and bundles are determined to be as they were at snapshot time.
   */
  public static final int ATTEMPT_COUNT = 5;

  private static final Logger LOGGER = LoggerFactory.getLogger(ServiceAdmin.class);

  private static final Map<Integer, String> BUNDLE_STATES =
      new ImmutableMap.Builder<Integer, String>()
          .put(Bundle.UNINSTALLED, "UNINSTALLED")
          .put(Bundle.INSTALLED, "INSTALLED")
          .put(Bundle.RESOLVED, "RESOLVED")
          .put(Bundle.STARTING, "STARTING")
          .put(Bundle.STOPPING, "STOPPING")
          .put(Bundle.ACTIVE, "ACTIVE")
          .build();

  private static final Predicate<FeatureState> FEATURE_IS_NOT_UNINSTALLED =
      ((Predicate<FeatureState>) (FeatureState.Uninstalled::equals)).negate();

  private static final EnumSet<FeaturesService.Option> NO_AUTO_REFRESH =
      EnumSet.of(FeaturesService.Option.NoAutoRefreshBundles);

  /**
   * Sets of annotations that can be found on test classes, test methods, or recursively inside
   * other annotations found on test classes or test methods.
   */
  private static final Class<? extends Annotation>[] META_ANNOTATIONS =
      new Class[] {
        org.codice.pax.exam.service.Feature.Start.class,
        org.codice.pax.exam.service.Feature.Stop.class
      };

  private static Profile profile = null;

  private static List<AnnotationEntry<?>> classAnnotations = null;

  private final long stabilizeTimeout;

  /**
   * Injects the features and bundle services and bundle context while waiting for each of them for
   * a default amount of time defined by the {@link
   * org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_KEY} system properties (defaults to {@link
   * org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_DEFAULT} milliseconds). The service will also
   * wait for configuration to stabilize before a test for a default amount of time defined by the
   * {@link org.codice.pax.exam.junit.ServiceAdmin#EXAM_SERVICE_STABILIZE_TIMEOUT_KEY} system
   * property (defaults to {@link
   * org.codice.pax.exam.junit.ServiceAdmin#EXAM_SERVICE_STABILIZE_TIMEOUT_DEFAULT} milliseconds).
   */
  public ServiceAdmin() {
    super(
        new InjectedService<>(FeaturesService.class),
        new InjectedService<>(BundleService.class),
        new InjectedService<>(BundleContext.class));
    this.stabilizeTimeout = ServiceAdmin.getStabilizeTimeout();
  }

  /**
   * Injects the features and bundle services and bundle context while waiting for each of them for
   * the specified amount of time. The service will also wait for configuration to stabilize before
   * a test for a default amount of time defined by the {@link
   * org.codice.pax.exam.junit.ServiceAdmin#EXAM_SERVICE_STABILIZE_TIMEOUT_KEY} system property
   * (defaults to {@link
   * org.codice.pax.exam.junit.ServiceAdmin#EXAM_SERVICE_STABILIZE_TIMEOUT_DEFAULT} milliseconds).
   *
   * @param timeout the maximum number of milliseconds to wait for the service or <code>-1</code> to
   *     use the {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_KEY} system property value
   *     as described in {@link ServiceAdmin ()}
   */
  public ServiceAdmin(long timeout) {
    this(timeout, -1L);
  }

  /**
   * Injects the features and bundle services and bundle context while waiting for each of them for
   * the specified amount of time.
   *
   * @param timeout the maximum number of milliseconds to wait for the service or <code>-1</code> to
   *     use the {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_KEY} system property value
   *     as described in {@link ServiceAdmin ()}
   * @param stabilizeTimeout the maximum number of milliseconds to wait for features and bundles to
   *     stabilized before a test starts or <code>-1</code> to use the {@link
   *     org.codice.pax.exam.junit.ServiceAdmin#EXAM_SERVICE_STABILIZE_TIMEOUT_KEY} system property
   *     value as described in {@link #ServiceAdmin()}
   */
  public ServiceAdmin(long timeout, long stabilizeTimeout) {
    super(
        new InjectedService<>(FeaturesService.class, timeout),
        new InjectedService<>(BundleService.class, timeout),
        new InjectedService<>(BundleContext.class, timeout));
    this.stabilizeTimeout =
        (stabilizeTimeout != -1) ? stabilizeTimeout : ServiceAdmin.getStabilizeTimeout();
  }

  /**
   * This constructor is designed to be used by the {@link org.codice.pax.exam.junit.ServiceAdmin}
   * annotation in order to initialize this method rule automatically.
   *
   * @param annotation the annotation referencing this method rule
   */
  public ServiceAdmin(org.codice.pax.exam.junit.ServiceAdmin annotation) {
    this(annotation.timeout(), annotation.stabilizeTimeout());
  }

  // --- Karaf ServiceAdmin API

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void validateRepository(URI uri) {
    try {
      service(FeaturesService.class).validateRepository(uri);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  @Override
  public boolean isRepositoryUriBlacklisted(URI uri) {
    return service(FeaturesService.class).isRepositoryUriBlacklisted(uri);
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void addRepository(URI uri) {
    try {
      service(FeaturesService.class).addRepository(uri);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void addRepository(URI uri, boolean install) {
    try {
      service(FeaturesService.class).addRepository(uri, install);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void removeRepository(URI uri) {
    try {
      service(FeaturesService.class).removeRepository(uri);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void removeRepository(URI uri, boolean uninstall) {
    try {
      service(FeaturesService.class).removeRepository(uri, uninstall);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void restoreRepository(URI uri) {
    try {
      service(FeaturesService.class).removeRepository(uri);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Repository[] listRequiredRepositories() {
    try {
      return service(FeaturesService.class).listRequiredRepositories();
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Repository[] listRepositories() {
    try {
      return service(FeaturesService.class).listRepositories();
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Repository getRepository(String repoName) {
    try {
      return service(FeaturesService.class).getRepository(repoName);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Repository getRepository(URI uri) {
    try {
      return service(FeaturesService.class).getRepository(uri);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public String getRepositoryName(URI uri) {
    try {
      return service(FeaturesService.class).getRepositoryName(uri);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  @Override
  public void setResolutionOutputFile(String outputFile) {
    service(FeaturesService.class).setResolutionOutputFile(outputFile);
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void installFeature(String name) {
    try {
      service(FeaturesService.class).installFeature(name);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void installFeature(String name, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).installFeature(name, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void installFeature(String name, String version) {
    try {
      service(FeaturesService.class).installFeature(name, version);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void installFeature(String name, String version, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).installFeature(name, version, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void installFeature(Feature feature, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).installFeature(feature, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void installFeatures(Set<String> names, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).installFeatures(names, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void installFeatures(Set<String> names, String region, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).installFeatures(names, region, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void addRequirements(Map<String, Set<String>> requirements, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).addRequirements(requirements, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void uninstallFeature(String name, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).uninstallFeature(name, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void uninstallFeature(String name) {
    try {
      service(FeaturesService.class).uninstallFeature(name);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void uninstallFeature(String name, String version, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).uninstallFeature(name, version, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void uninstallFeature(String name, String version) {
    try {
      service(FeaturesService.class).uninstallFeature(name, version);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void uninstallFeatures(Set<String> names, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).uninstallFeatures(names, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void uninstallFeatures(Set<String> names, String region, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).uninstallFeatures(names, region, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void removeRequirements(Map<String, Set<String>> requirements, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).removeRequirements(requirements, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void updateFeaturesState(
      Map<String, Map<String, FeatureState>> stateChanges, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).updateFeaturesState(stateChanges, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void updateReposAndRequirements(
      Set<URI> repos, Map<String, Set<String>> requirements, EnumSet<Option> options) {
    try {
      service(FeaturesService.class).updateReposAndRequirements(repos, requirements, options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Repository createRepository(URI uri) {
    try {
      return service(FeaturesService.class).createRepository(uri);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Feature[] listFeatures() {
    try {
      return service(FeaturesService.class).listFeatures();
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Feature[] listRequiredFeatures() {
    try {
      return service(FeaturesService.class).listRequiredFeatures();
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Feature[] listInstalledFeatures() {
    try {
      return service(FeaturesService.class).listInstalledFeatures();
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  @Override
  public Map<String, Set<String>> listRequirements() {
    return service(FeaturesService.class).listRequirements();
  }

  @Override
  public boolean isRequired(Feature feature) {
    return service(FeaturesService.class).isRequired(feature);
  }

  @Override
  public boolean isInstalled(Feature feature) {
    return service(FeaturesService.class).isInstalled(feature);
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Feature getFeature(String name, String version) {
    try {
      return service(FeaturesService.class).getFeature(name, version);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Feature getFeature(String name) {
    try {
      return service(FeaturesService.class).getFeature(name);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Feature[] getFeatures(String name, String version) {
    try {
      return service(FeaturesService.class).getFeatures(name, version);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public Feature[] getFeatures(String name) {
    try {
      return service(FeaturesService.class).getFeatures(name);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void refreshRepositories(Set<URI> uris) {
    try {
      service(FeaturesService.class).refreshRepositories(uris);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void refreshRepository(URI uri) {
    try {
      service(FeaturesService.class).removeRepository(uri);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  @Override
  public URI getRepositoryUriFor(String name, String version) {
    return service(FeaturesService.class).getRepositoryUriFor(name, version);
  }

  @Override
  public String[] getRepositoryNames() {
    return service(FeaturesService.class).getRepositoryNames();
  }

  @Override
  public void registerListener(FeaturesListener listener) {
    service(FeaturesService.class).registerListener(listener);
  }

  @Override
  public void unregisterListener(FeaturesListener listener) {
    service(FeaturesService.class).unregisterListener(listener);
  }

  @Override
  public void registerListener(DeploymentListener listener) {
    service(FeaturesService.class).registerListener(listener);
  }

  @Override
  public void unregisterListener(DeploymentListener listener) {
    service(FeaturesService.class).unregisterListener(listener);
  }

  @Override
  public FeatureState getState(String featureId) {
    return service(FeaturesService.class).getState(featureId);
  }

  @Override
  public String getFeatureXml(Feature feature) {
    return service(FeaturesService.class).getFeatureXml(feature);
  }

  /**
   * {@inheritDoc}
   *
   * @throws ServiceException if a failure occurs
   */
  @Override
  public void refreshFeatures(EnumSet<Option> options) {
    try {
      service(FeaturesService.class).refreshFeatures(options);
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  // --- Karaf BundleService API

  @Override
  public org.apache.karaf.bundle.core.BundleInfo getInfo(Bundle bundle) {
    return service(BundleService.class).getInfo(bundle);
  }

  @Override
  public List<Bundle> selectBundles(List<String> ids, boolean defaultAllBundles) {
    return service(BundleService.class).selectBundles(ids, defaultAllBundles);
  }

  @Override
  public List<Bundle> selectBundles(String context, List<String> ids, boolean defaultAllBundles) {
    return service(BundleService.class).selectBundles(context, ids, defaultAllBundles);
  }

  @Override
  public Bundle getBundle(String id) {
    return service(BundleService.class).getBundle(id);
  }

  @Override
  public Bundle getBundle(String context, String id) {
    return service(BundleService.class).getBundle(context, id);
  }

  @Override
  public String getDiag(Bundle bundle) {
    return service(BundleService.class).getDiag(bundle);
  }

  @Override
  public List<BundleRequirement> getUnsatisfiedRequirements(Bundle bundle, String namespace) {
    return service(BundleService.class).getUnsatisfiedRequirements(bundle, namespace);
  }

  @Override
  public Map<String, Bundle> getWiredBundles(Bundle bundle) {
    return service(BundleService.class).getWiredBundles(bundle);
  }

  @Override
  public boolean isDynamicImport(Bundle bundle) {
    return service(BundleService.class).isDynamicImport(bundle);
  }

  @Override
  public void enableDynamicImports(Bundle bundle) {
    service(BundleService.class).enableDynamicImports(bundle);
  }

  @Override
  public void disableDynamicImports(Bundle bundle) {
    service(BundleService.class).disableDynamicImports(bundle);
  }

  @Override
  public int getSystemBundleThreshold() {
    return service(BundleService.class).getSystemBundleThreshold();
  }

  @Override
  public String getStatus(String id) {
    return service(BundleService.class).getStatus(id);
  }

  // --- Karaf BundleContext API

  @Override
  public String getProperty(String key) {
    return service(BundleContext.class).getProperty(key);
  }

  @Override
  public Bundle getBundle() {
    return service(BundleContext.class).getBundle();
  }

  @Override
  public Bundle installBundle(String location, InputStream input) throws BundleException {
    return service(BundleContext.class).installBundle(location, input);
  }

  @Override
  public Bundle installBundle(String location) throws BundleException {
    return service(BundleContext.class).installBundle(location);
  }

  @Override
  public Bundle getBundle(long id) {
    return service(BundleContext.class).getBundle(id);
  }

  @Override
  public Bundle[] getBundles() {
    return service(BundleContext.class).getBundles();
  }

  @Override
  public void addServiceListener(ServiceListener listener, String filter)
      throws InvalidSyntaxException {
    service(BundleContext.class).addServiceListener(listener, filter);
  }

  @Override
  public void addServiceListener(ServiceListener listener) {
    service(BundleContext.class).addServiceListener(listener);
  }

  @Override
  public void removeServiceListener(ServiceListener listener) {
    service(BundleContext.class).removeServiceListener(listener);
  }

  @Override
  public void addBundleListener(BundleListener listener) {
    service(BundleContext.class).addBundleListener(listener);
  }

  @Override
  public void removeBundleListener(BundleListener listener) {
    service(BundleContext.class).removeBundleListener(listener);
  }

  @Override
  public void addFrameworkListener(FrameworkListener listener) {
    service(BundleContext.class).addFrameworkListener(listener);
  }

  @Override
  public void removeFrameworkListener(FrameworkListener listener) {
    service(BundleContext.class).removeFrameworkListener(listener);
  }

  @Override
  public ServiceRegistration<?> registerService(
      String[] clazzes, Object service, Dictionary<String, ?> properties) {
    return service(BundleContext.class).registerService(clazzes, service, properties);
  }

  @Override
  public ServiceRegistration<?> registerService(
      String clazz, Object service, Dictionary<String, ?> properties) {
    return service(BundleContext.class).registerService(clazz, service, properties);
  }

  @Override
  public <S> ServiceRegistration<S> registerService(
      Class<S> clazz, S service, Dictionary<String, ?> properties) {
    return service(BundleContext.class).registerService(clazz, service, properties);
  }

  @Override
  public ServiceReference<?>[] getServiceReferences(String clazz, String filter)
      throws InvalidSyntaxException {
    return service(BundleContext.class).getServiceReferences(clazz, filter);
  }

  @Override
  public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter)
      throws InvalidSyntaxException {
    return service(BundleContext.class).getAllServiceReferences(clazz, filter);
  }

  @Override
  public ServiceReference<?> getServiceReference(String clazz) {
    return service(BundleContext.class).getServiceReference(clazz);
  }

  @Override
  public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
    return service(BundleContext.class).getServiceReference(clazz);
  }

  @Override
  public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
      throws InvalidSyntaxException {
    return service(BundleContext.class).getServiceReferences(clazz, filter);
  }

  @Override
  public <S> S getService(ServiceReference<S> reference) {
    return service(BundleContext.class).getService(reference);
  }

  @Override
  public boolean ungetService(ServiceReference<?> reference) {
    return service(BundleContext.class).ungetService(reference);
  }

  @Override
  public File getDataFile(String filename) {
    return service(BundleContext.class).getDataFile(filename);
  }

  @Override
  public Filter createFilter(String filter) throws InvalidSyntaxException {
    return service(BundleContext.class).createFilter(filter);
  }

  // --- Extended ServiceAdmin API

  /**
   * Installs the specified feature and wait for it to stabilize.
   *
   * @param name the name of the feature to be installed
   * @param timeout the time in milliseconds to wait for the feature to be installed
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void installFeatureAndWait(String name, long timeout) throws InterruptedException {
    installFeature(name);
    waitForFeature(name, ServiceAdmin.FEATURE_IS_NOT_UNINSTALLED, timeout);
  }

  /**
   * Installs the specified feature and wait for it to stabilize.
   *
   * @param name the name of the feature to be installed
   * @param options the options for the feature
   * @param timeout the time in milliseconds to wait for the feature to be installed
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void installFeatureAndWait(String name, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    installFeature(name, options);
    waitForFeature(name, ServiceAdmin.FEATURE_IS_NOT_UNINSTALLED, timeout);
  }

  /**
   * Installs the specified feature and wait for it to stabilize.
   *
   * @param name the name of the feature to be installed
   * @param version the version for the feature
   * @param timeout the time in milliseconds to wait for the feature to be installed
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void installFeatureAndWait(String name, String version, long timeout)
      throws InterruptedException {
    installFeature(name, version);
    waitForFeature(name, version, ServiceAdmin.FEATURE_IS_NOT_UNINSTALLED, timeout);
  }

  /**
   * Installs the specified feature and wait for it to stabilize.
   *
   * @param name the name of the feature to be installed
   * @param version the version for the feature
   * @param options the options for the feature
   * @param timeout the time in milliseconds to wait for the feature to be installed
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void installFeatureAndWait(
      String name, String version, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    installFeature(name, version, options);
    waitForFeature(name, version, ServiceAdmin.FEATURE_IS_NOT_UNINSTALLED, timeout);
  }

  /**
   * Installs the specified feature and wait for it to stabilize.
   *
   * @param feature the feature to be installed
   * @param options the options for the feature
   * @param timeout the time in milliseconds to wait for the feature to be installed
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void installFeatureAndWait(Feature feature, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    installFeature(feature, options);
    waitForFeature(
        feature.getName(), feature.getVersion(), ServiceAdmin.FEATURE_IS_NOT_UNINSTALLED, timeout);
  }

  /**
   * Installs the specified features and wait for them to stabilize.
   *
   * @param names the names of the features to be installed
   * @param options the options for the features
   * @param timeout the time in milliseconds to wait for the features to be installed
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the features to
   *     stabilize
   */
  public void installFeaturesAndWait(Set<String> names, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    installFeatures(names, options);
    waitForFeatures(names, ServiceAdmin.FEATURE_IS_NOT_UNINSTALLED, timeout);
  }

  /**
   * Installs the specified features and wait for them to stabilize.
   *
   * @param names the names of the features to be installed
   * @param region the region for the features
   * @param options the options for the features
   * @param timeout the time in milliseconds to wait for the features to be installed
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the features to
   *     stabilize
   */
  public void installFeaturesAndWait(
      Set<String> names, String region, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    installFeatures(names, region, options);
    waitForFeatures(names, ServiceAdmin.FEATURE_IS_NOT_UNINSTALLED, timeout);
  }

  /**
   * Uninstalls the specified feature and wait for it to stabilize.
   *
   * @param name the name of the feature to be installed
   * @param options the options for the feature
   * @param timeout the time in milliseconds to wait for the feature to be uninstalled
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void uninstallFeatureAndWait(String name, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    uninstallFeature(name, options);
    waitForFeature(name, FeatureState.Uninstalled::equals, timeout);
  }

  /**
   * Uninstalls the specified feature and wait for it to stabilize.
   *
   * @param name the name of the feature to be installed
   * @param timeout the time in milliseconds to wait for the feature to be uninstalled
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void uninstallFeatureAndWait(String name, long timeout) throws InterruptedException {
    uninstallFeature(name);
    waitForFeature(name, FeatureState.Uninstalled::equals, timeout);
  }

  /**
   * Uninstalls the specified feature and wait for it to stabilize.
   *
   * @param name the name of the feature to be installed
   * @param version the version for the feature
   * @param options the options for the feature
   * @param timeout the time in milliseconds to wait for the feature to be uninstalled
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void uninstallFeatureAndWait(
      String name, String version, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    uninstallFeature(name, version, options);
    waitForFeature(name, version, FeatureState.Uninstalled::equals, timeout);
  }

  /**
   * Uninstalls the specified feature and wait for it to stabilize.
   *
   * @param name the name of the feature to be installed
   * @param version the version for the feature
   * @param timeout the time in milliseconds to wait for the feature to be uninstalled
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the feature to
   *     stabilize
   */
  public void uninstallFeatureAndWait(String name, String version, long timeout)
      throws InterruptedException {
    uninstallFeature(name, version);
    waitForFeature(name, version, FeatureState.Uninstalled::equals, timeout);
  }

  /**
   * Uninstalls the specified features and wait for them to stabilize.
   *
   * @param names the names of the features to be uninstalled
   * @param options the options for the features
   * @param timeout the time in milliseconds to wait for the features to be uninstalled
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the features to
   *     stabilize
   */
  public void uninstallFeaturesAndWait(Set<String> names, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    uninstallFeatures(names, options);
    waitForFeatures(names, FeatureState.Uninstalled::equals, timeout);
  }

  /**
   * Uninstalls the specified features and wait for them to stabilize.
   *
   * @param names the names of the features to be uninstalled
   * @param region the region for the features
   * @param options the options for the features
   * @param timeout the time in milliseconds to wait for the features to be uninstalled
   * @throws ServiceException if a failure occurs
   * @throws InterruptedException if the thread was interrupted while waiting for the features to
   *     stabilize
   */
  public void uninstallFeaturesAndWait(
      Set<String> names, String region, EnumSet<Option> options, long timeout)
      throws InterruptedException {
    uninstallFeatures(names, region, options);
    waitForFeatures(names, FeatureState.Uninstalled::equals, timeout);
  }

  /**
   * Waits a maximum amount of time for all bundles to become active and stabilize. This methods
   * attempts to ensure that all bundles are up and started.
   *
   * @param timeout the maximum amount of time in milliseconds to wait for the bundles to stabilize
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ServiceException if a bundle is discovered to have failed
   * @throws ServiceTimeoutException if we timed out before we were able to stabilize
   */
  public void stabilize(long timeout) throws InterruptedException {
    LOGGER.info("Stabilizing Karaf features and bundles");
    // we should only need to wait for bundles as this would automatically account for all features
    // after a restore
    waitForBundles("", timeout);
  }

  /**
   * Waits a maximum of time for the bundles matching the specified symbolic name prefix to become
   * active and stabilize.
   *
   * @param prefix the symbolic name prefix for the bundles to check and wait for
   * @param timeout the maximum amount of time in milliseconds to wait for the matching bundles to
   *     stabilize
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ServiceException if a bundle is discovered to have failed
   * @throws ServiceTimeoutException if we timed out before the matching bundles were able to
   *     stabilize
   */
  public void waitForBundles(String prefix, long timeout) throws InterruptedException {
    final long end = System.currentTimeMillis() + timeout;
    boolean ready = false;

    while (!ready) {
      ready = true;
      for (final Bundle bundle : service(BundleContext.class).getBundles()) {
        if (bundle.getSymbolicName().startsWith(prefix)) {
          final String bundleName = bundle.getHeaders().get(Constants.BUNDLE_NAME);
          final org.apache.karaf.bundle.core.BundleInfo bundleInfo =
              service(BundleService.class).getInfo(bundle);
          final BundleState bundleState = bundleInfo.getState();

          if (bundleInfo.isFragment()) {
            if (!BundleState.Resolved.equals(bundleState)) {
              LOGGER.info("{} bundle not ready yet", bundleName);
              ready = false;
            }
          } else if (bundleState != null) {
            if (BundleState.Failure.equals(bundleState)) {
              logInactiveBundles(LOGGER::error, LOGGER::error);
              throw new ServiceException("bundle " + bundleName + " failed");
            } else if (!BundleState.Active.equals(bundleState)) {
              LOGGER.info("{} bundle not ready with state {}", bundleName, bundleState);
              ready = false;
            }
          }
        }
      }
      if (!ready) {
        if (System.currentTimeMillis() > end) {
          logInactiveBundles(LOGGER::error, LOGGER::error);
          throw new ServiceTimeoutException(
              "timed out waiting for features and bundles to stabilize within "
                  + TimeUnit.MILLISECONDS.toSeconds(timeout)
                  + " seconds");
        }
        Thread.sleep(1000L);
      }
    }
  }

  /**
   * Waits a maximum of time for a specific feature to reach the specified state and stabilize.
   *
   * @param name the name for the feature to check and wait for
   * @param state the state to wait for the feature have reached
   * @param timeout the maximum amount of time in milliseconds to wait for the specified feature to
   *     stabilize
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ServiceTimeoutException if we timed out before the feature was able to stabilize to the
   *     specified state
   */
  public void waitForFeature(String name, FeatureState state, long timeout)
      throws InterruptedException {
    waitForFeature(name, state::equals, timeout);
  }

  /**
   * Waits a maximum of time for a specific feature to have it state matched the specified predicate
   * and stabilize.
   *
   * @param name the name for the feature to check and wait for
   * @param predicate the state predicate to wait for testing <code>true</code>
   * @param timeout the maximum amount of time in milliseconds to wait for the specified feature to
   *     stabilize
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ServiceTimeoutException if we timed out before the feature was able to stabilize to the
   *     specified state
   */
  public void waitForFeature(String name, Predicate<FeatureState> predicate, long timeout)
      throws InterruptedException {
    final Feature feature = getFeature(name);
    waitForFeature(feature.getName(), feature.getVersion(), predicate, timeout);
  }

  /**
   * Waits a maximum of time for a specific feature to have it state matched the specified predicate
   * and stabilize.
   *
   * @param name the name for the feature to check and wait for
   * @param version the version for the feature to wait for
   * @param predicate the state predicate to wait for testing <code>true</code>
   * @param timeout the maximum amount of time in milliseconds to wait for the specified feature to
   *     stabilize
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ServiceTimeoutException if we timed out before the feature was able to stabilize to the
   *     specified state
   */
  public void waitForFeature(
      String name, String version, Predicate<FeatureState> predicate, long timeout)
      throws InterruptedException {
    final long end = System.currentTimeMillis() + timeout;

    while (true) {
      final FeatureState state = getState(name + "/" + version);

      if (predicate.test(state)) {
        return;
      }
      if (System.currentTimeMillis() > end) {
        logInactiveBundles(LOGGER::error, LOGGER::error);
        throw new ServiceTimeoutException(
            "timed out waiting for feature '"
                + name
                + "' to stabilize to state '"
                + predicate
                + "' within "
                + TimeUnit.MILLISECONDS.toSeconds(timeout)
                + " seconds");
      }
      Thread.sleep(1000L);
    }
  }

  /**
   * Waits a maximum of time for a set of features to have it state matched the specified predicate
   * and stabilize.
   *
   * @param names the name for the features to check and wait for
   * @param predicate the state predicate to wait for testing <code>true</code>
   * @param timeout the maximum amount of time in milliseconds to wait for the specified features to
   *     stabilize
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ServiceTimeoutException if we timed out before the features were able to stabilize to
   *     the specified state
   */
  public void waitForFeatures(Set<String> names, Predicate<FeatureState> predicate, long timeout)
      throws InterruptedException {
    final long end = System.currentTimeMillis() + timeout;
    final Deque<Feature> toWaitFor =
        names.stream().map(this::getFeature).collect(Collectors.toCollection(LinkedList::new));

    while (!toWaitFor.isEmpty()) {
      final Feature feature = toWaitFor.peek();
      final FeatureState state = getState(feature.getName() + "/" + feature.getVersion());

      if (predicate.test(state)) {
        toWaitFor.pop();
        continue;
      }
      if (System.currentTimeMillis() > end) {
        logInactiveBundles(LOGGER::error, LOGGER::error);
        throw new ServiceTimeoutException(
            "timed out waiting for features '"
                + toWaitFor.stream().map(Feature::getName).collect(Collectors.joining(", "))
                + "' to stabilize to state '"
                + predicate
                + "' within "
                + TimeUnit.MILLISECONDS.toSeconds(timeout)
                + " seconds");
      }
      Thread.sleep(1000L);
    }
  }

  /**
   * Gets all installed repositories.
   *
   * @return a stream of all installed repositories
   * @throws ServiceException if a failure occurs while retrieving all installed repositories
   */
  public Stream<Repository> repositories() {
    return Stream.of(listRepositories());
  }

  // - JUnit Rule API (not to be called directly)

  @Override
  public void snapshot(FrameworkMethod method, Object target) {
    // we know that the only rules added to the base MethodRuleChain class are InjectedService
    // rules so there which pre-injects their required services when their snapshot() is called
    super.snapshot(method, target);

    // take the snapshot outside of the statements to make sure it gets taken before any changes
    // to the system is performed by any rules
    takeSnapshot();
  }

  @Override
  public Statement applyAfterSnapshot(Statement statement, FrameworkMethod method, Object target) {
    // we know that the only rules added to the base MethodRuleChain class are InjectedService
    // rules so there which pre-injects their required services when their applyAfterSnapshot() is
    // called and returns the passed statement intact which would eventually be returned by the
    // MethodRuleChain class as its statement so we must first call super.apply() with an empty
    // statement and be done with it
    super.applyAfterSnapshot(EmptyStatement.EMPTY, method, target);

    // take the snapshot (in case it wasn't taken in snapshot()) outside of the statements to make
    // sure it gets taken before any changes to the system is performed by any rules
    takeSnapshot();

    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          final Profile toProcess = new Profile(true);

          processClassAnnotations(toProcess, target);
          processMethodAnnotations(toProcess, method, target);

          if (!toProcess.isEmpty()) {
            restoreSnapshot(toProcess);
            // stabilize to let the features and bundles initialization happens
            stabilize(stabilizeTimeout);
          }

          // proceed with the test method
          statement.evaluate();
        } finally {
          restoreSnapshot(ServiceAdmin.profile);
        }
      }
    };
  }

  // --- Snapshots

  private void takeSnapshot() {
    // only take a snapshot the first time around
    synchronized (ServiceAdmin.class) {
      if (ServiceAdmin.profile == null) {
        try { // stabilize the system before taking a snapshot
          stabilize(stabilizeTimeout);
        } catch (InterruptedException e) { // propagate interruption
          Thread.currentThread().interrupt();
        }
        LOGGER.info("Snapshooting Karaf repositories, features, and bundles");
        ServiceAdmin.profile =
            new Profile(snapshotRepositories(), snapshotFeatures(), snapshotBundles());
      }
    }
  }

  private void restoreSnapshot(Profile profile) throws InterruptedException {
    synchronized (ServiceAdmin.class) {
      if (!profile.shouldOnlyProcessSnapshot()) {
        LOGGER.info("Restoring Karaf repositories, features, and bundles");
      }
      final SnapshotReport report = new SnapshotReport();

      for (int i = 1; i < ServiceAdmin.ATTEMPT_COUNT; i++) {
        LOGGER.trace("restoring profile (attempt {} out of {})", i, ServiceAdmin.ATTEMPT_COUNT);
        if (restoreSnapshot(profile, report.reset(false))) {
          // finally, make sure everything is stable before continuing
          stabilize(stabilizeTimeout);
          return;
        }
      }
      LOGGER.trace("verifying profile");
      if (restoreSnapshot(profile, report.reset(true))) {
        // finally, make sure everything is stable before continuing
        stabilize(stabilizeTimeout);
        return;
      }
      // if we get here then we had more tasks to execute after having tried so many times!!!
      if (!profile.shouldOnlyProcessSnapshot()) {
        throw new ServiceException("too many attempts to restore snapshot");
      }
      throw new ServiceException("too many attempts to process annotations");
    }
  }

  /**
   * Performs a restore attempt of the specified profile.
   *
   * @param profile the snapshot profile to restore from
   * @param report the snapshot report where to record errors errors
   * @return <code>true</code> if the restore was successful and didn't need to perform any
   *     operations; <code>false</code> if we didn't fail but we had to perform some operations
   *     which typically would mean we need to retry
   * @throws ServiceException if a failure occurred preventing anymore attempts to be made
   */
  private boolean restoreSnapshot(Profile profile, SnapshotReport report) {
    if (restoreRepositories(profile, report) && restoreBundles(profile, report)) {
      restoreFeatures(profile, report);
    }
    report.failIfErrorsWereRecorded();
    if (report.hasSuppressedErrors() || report.hasRecordedTasks()) {
      // either we got some suppressed errors recorded or more tasks had to be executed
      // in any case, we got to either loop back or fail the final attempt
      return false;
    }
    // no tasks were recorded and no suppressed errors on this pass which means the verification
    // was perfect!
    return true;
  }

  /**
   * Snapshots all repositories in memory.
   *
   * @return a stream of the snapshot repositories
   */
  private Stream<Repository> snapshotRepositories() {
    return repositories()
        .peek(
            r ->
                LOGGER.debug("snapshooting: repository[name={}, uri={}]", r.getName(), r.getURI()));
  }

  /**
   * Restores the snapshot repositories.
   *
   * <p>The implementation will loop until it fails to find a repository in memory or it fails to
   * change the state of a repository or again until the state of the repositories in memory matches
   * the state of the repositories as the repositories when they were snapshot.
   *
   * @param profile the snapshot profile to restore from
   * @param report the report where to record errors
   * @return <code>true</code> if all were restored successfully; <code>false</code> otherwise
   */
  private boolean restoreRepositories(Profile profile, SnapshotReport report) {
    final TaskList tasks = new TaskList("repository", report);
    final RepositoryProcessor processor = new RepositoryProcessor(service(FeaturesService.class));

    // loop until we can determine that all repositories that should be installed or uninstalled are
    // or until we get an error or exceeds the max number of attempts
    while (true) {
      processor.processRepositoriesAndPopulateTaskList(profile, tasks);
      if (tasks.isEmpty()) {
        LOGGER.trace("No (or no more) repositories to restore");
        return true;
      } else if (!tasks.execute()) {
        LOGGER.trace("Failed to execute some repository restore tasks");
        // errors would already have been recorded
        return false;
      }
    }
  }

  /**
   * Snapshots all features in memory.
   *
   * @return a stream of the snapshot features
   */
  private Stream<FeatureSnapshot> snapshotFeatures() {
    final FeaturesService service = service(FeaturesService.class);
    final FeatureProcessor processor = new FeatureProcessor(service);

    return Stream.of(processor.listFeatures("Snapshot"))
        .map(f -> new FeatureSnapshot(f, service))
        .peek(f -> LOGGER.debug("snapshooting: {}", f));
  }

  /**
   * Restores the snapshot features.
   *
   * <p>The implementation will loop until it fails to find a feature in memory or it fails to
   * change the state of a feature or again until the state of the features in memory matches the
   * state of the features when they were snapshot
   *
   * @param profile the snapshot profile to restore from
   * @param report the report where to record errors
   * @return <code>true</code> if all were restored successfully; <code>false</code> otherwise
   */
  private boolean restoreFeatures(Profile profile, SnapshotReport report) {
    final FeatureProcessor processor = new FeatureProcessor(service(FeaturesService.class));
    final TaskList tasks = new TaskList("feature", report);

    // loop until we can determine that all features that should be started, stopped, installed, or
    // uninstalled are or until we get an error or exceeds the max number of attempts
    while (true) {
      processor.processFeaturesAndPopulateTaskList(profile, tasks);
      if (tasks.isEmpty()) {
        LOGGER.trace("No (or no more) features to restore");
        return true;
      } else if (!tasks.execute()) {
        LOGGER.trace("Failed to execute some feature restore tasks");
        // errors would already have been recorded
        return false;
      }
    }
  }

  /**
   * Snapshots all bundles in memory.
   *
   * @return a stream of the snapshot bundles
   */
  private Stream<BundleSnapshot> snapshotBundles() {
    final BundleProcessor processor = new BundleProcessor();

    return Stream.of(processor.listBundles(service(BundleContext.class)))
        .map(BundleSnapshot::new)
        .peek(b -> LOGGER.debug("snapshooting: {}", b));
  }

  /**
   * Restores the snapshot bundles.
   *
   * <p>The implementation will loop until it fails to find a bundle in memory or it fails to change
   * the state of a bundle or again until the state of the bundles in memory matches the state of
   * the bundles as the bundles when they were snapshot.
   *
   * @param profile the snapshot profile to restore from
   * @param report the report where to record errors
   * @return <code>true</code> if all were restored successfully; <code>false</code> otherwise
   */
  private boolean restoreBundles(Profile profile, SnapshotReport report) {
    final BundleProcessor processor = new BundleProcessor();
    final TaskList tasks = new TaskList("bundle", report);

    // loop until we can determine that all bundles that should be started. stopped, installed, or
    // uninstalled are or until we get an error or exceeds the max number of attempts
    while (true) {
      processor.processBundlesAndPopulateTaskList(service(BundleContext.class), profile, tasks);
      if (tasks.isEmpty()) {
        LOGGER.trace("No (or no more) bundles to restore");
        return true;
      } else if (!tasks.execute()) {
        LOGGER.trace("Failed to execute some bundle restore tasks");
        // errors would already have been recorded
        return false;
      }
    }
  }

  // --- class and method annotations

  private void processClassAnnotations(Profile toProcess, Object target) {
    // find class annotations only the first time around and cache them
    synchronized (ServiceAdmin.class) {
      if (ServiceAdmin.classAnnotations == null) {
        ServiceAdmin.classAnnotations =
            ReflectionUtils.annotationsByTypes(target.getClass(), ServiceAdmin.META_ANNOTATIONS)
                .collect(Collectors.toList());
      }
    }
    ServiceAdmin.classAnnotations.forEach(a -> processAnnotation(toProcess, target, a));
  }

  private void processMethodAnnotations(Profile toProcess, FrameworkMethod method, Object target) {
    ReflectionUtils.annotationsByTypes(method.getMethod(), ServiceAdmin.META_ANNOTATIONS)
        .forEach(a -> processAnnotation(toProcess, target, a));
  }

  private void processAnnotation(Profile toProcess, Object target, AnnotationEntry<?> entry) {
    final Annotation a = entry.getAnnotation();

    if (a instanceof org.codice.pax.exam.service.Feature.Start) {
      final org.codice.pax.exam.service.Feature.Start feature =
          (org.codice.pax.exam.service.Feature.Start) a;
      final boolean groupIsDefined = !feature.repository().groupId().isEmpty();
      final boolean urlIsDefined = !feature.repositoryUrl().isEmpty();

      if (groupIsDefined) {
        if (urlIsDefined) {
          throw new IllegalArgumentException(
              "specify only one of Feature.Start.repository() or Feature.Start.repositoryUrl() in "
                  + a
                  + " for "
                  + target.getClass().getName());
        }
        toProcess.add(
            MavenUrlReference.resolve(feature.repository(), a, new AnnotationResourceLoader(entry))
                .getURL());
      } else if (urlIsDefined) {
        toProcess.add(feature.repositoryUrl());
      }
      toProcess.add(new FeatureSnapshot(feature));
    } else { // instanceof of org.codice.pax.exam.service.Feature.Stop
      toProcess.add(new FeatureSnapshot((org.codice.pax.exam.service.Feature.Stop) a));
    }
  }

  // --- miscellaneous

  @Nullable
  private <T> T service(Class<T> clazz) {
    return rules()
        .map(InjectedService.class::cast)
        .map(InjectedService::getService)
        .filter(clazz::isInstance)
        .map(clazz::cast)
        .findFirst()
        .orElse(null);
  }

  private void logInactiveBundles(
      Consumer<String> headerConsumer, BiConsumer<String, Object[]> logConsumer) {
    headerConsumer.accept("Listing inactive bundles");
    for (final Bundle bundle : service(BundleContext.class).getBundles()) {
      if (bundle.getState() != Bundle.ACTIVE) {
        final org.apache.karaf.bundle.core.BundleInfo bundleInfo =
            service(BundleService.class).getInfo(bundle);
        final Dictionary<String, String> headers = bundle.getHeaders();

        if (bundleInfo.isFragment()) {
          continue;
        }
        final StringBuilder sb = new StringBuilder();

        sb.append("[ ");
        for (final Enumeration<String> e = headers.keys(); e.hasMoreElements(); ) {
          final String key = e.nextElement();

          sb.append(key).append("=").append(headers.get(key)).append(", ");
        }
        sb.append(" ]");
        logConsumer.accept(
            "\n\tBundle: {}_v{} | {}\n\tHeaders: {}",
            new Object[] {
              bundle.getSymbolicName(),
              bundle.getVersion(),
              ServiceAdmin.BUNDLE_STATES.getOrDefault(bundle.getState(), "UNKNOWN"),
              sb
            });
      }
    }
  }

  private static final long getStabilizeTimeout() {
    final String str =
        System.getProperty(
            org.codice.pax.exam.junit.ServiceAdmin.EXAM_SERVICE_STABILIZE_TIMEOUT_KEY);
    long timeout = -1L;

    if (str != null) {
      try {
        timeout = Long.parseLong(str);
      } catch (NumberFormatException e) { // ignore and continue with default
      }
    }
    return (timeout >= 0L)
        ? timeout
        : org.codice.pax.exam.junit.ServiceAdmin.EXAM_SERVICE_STABILIZE_TIMEOUT_DEFAULT;
  }

  static class AnnotationResourceLoader implements ResourceLoader {
    private final AnnotationEntry<?> entry;

    AnnotationResourceLoader(AnnotationEntry<?> entry) {
      this.entry = entry;
    }

    @Override
    public Class<?> getLocationClass() {
      return entry.getEnclosingElementClass();
    }

    @SuppressWarnings("squid:CommentedOutCodeLine" /* no code commented here */)
    @Nullable
    @Override
    public InputStream getResourceAsStream(String name) {
      // here we need to load the resource from where the annotation that includes the @MavenUrl
      // is defined
      //
      // For example:
      //    @Feature.Start(repository=@MavenUrl(...), ...)
      // In this case:
      //    'entry.getAnnotation()' == @Feature.Start
      //    'entry.getEnclosingResourceAsStream()` would load from @Feature.Start
      return AccessController.doPrivileged(
          (PrivilegedAction<InputStream>) () -> entry.getEnclosingResourceAsStream(name));
    }

    @Override
    public String toString() {
      return getLocationClass().getName();
    }
  }
}
