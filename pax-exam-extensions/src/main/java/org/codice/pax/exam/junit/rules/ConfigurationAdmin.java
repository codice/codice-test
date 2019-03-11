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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.felix.cm.file.ConfigurationHandler;
import org.codice.pax.exam.config.ConfigException;
import org.codice.pax.exam.config.ConfigTimeoutException;
import org.codice.pax.exam.config.Configuration;
import org.codice.pax.exam.config.internal.ConfigurationSnapshot;
import org.codice.test.commons.ReflectionUtils;
import org.codice.test.commons.ReflectionUtils.AnnotationEntry;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This JUnit method rule behaves similarly to Pax Exam support for the <code>@Inject</code>
 * annotation for {@link org.osgi.service.cm.ConfigurationAdmin} while also providing support for
 * restoring all configurations objects after each tests to their initial state and pre-initializing
 * configuration objects based on provided annotations (see the {@link Configuration} class for a
 * set of available annotations).
 */
@SuppressWarnings("squid:S2176" /* name was chosen to be a replacement for OSGi's config admin */)
public class ConfigurationAdmin extends InjectedService<org.osgi.service.cm.ConfigurationAdmin>
    implements org.osgi.service.cm.ConfigurationAdmin {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationAdmin.class);

  /**
   * Sets of annotations that can be found on test classes, test methods, or recursively inside
   * other annotations found on test classes or test methods.
   */
  private static final Class<? extends Annotation>[] META_ANNOTATIONS =
      new Class[] {
        Configuration.Annotation.class,
        Configuration.Resources.class,
        Configuration.Property.Remove.class,
        Configuration.Property.SetString.class,
        Configuration.Property.SetLong.class,
        Configuration.Property.SetInteger.class,
        Configuration.Property.SetShort.class,
        Configuration.Property.SetCharacter.class,
        Configuration.Property.SetByte.class,
        Configuration.Property.SetDouble.class,
        Configuration.Property.SetFloat.class,
        Configuration.Property.SetBoolean.class
      };

  private static final String THREAD_GROUP = "Configuration Admin Service";

  private static final String INTERNAL_PID = ConfigurationAdmin.class.getName();

  private static final String TIME_KEY = "time";

  // config admin creates 2 threads in this group: CM Configuration Updater, and CM Event Dispatcher
  private static final int EXPECTED_THREADS = 2;

  private static final long STABILIZE_TIMEOUT = TimeUnit.MINUTES.toMillis(2L);

  private static volatile ThreadGroup threadGroup = null;

  private static final Map<String, ConfigurationSnapshot> snapshotConfigs =
      new ConcurrentHashMap<>();

  private static List<AnnotationEntry<?>> classAnnotations = null;

  private static final Map<String, Resource> resources = new ConcurrentHashMap<>();

  private volatile Configuration internalConfig = null;

  /**
   * Injects the configuration admin service while waiting for it for a default amount of time
   * defined by the {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_KEY} system properties
   * (defaults to {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_DEFAULT} milliseconds).
   */
  public ConfigurationAdmin() {
    super(org.osgi.service.cm.ConfigurationAdmin.class);
  }

  /**
   * Injects the configuration admin service while waiting for it for the specified amount of time.
   *
   * @param timeout the maximum number of milliseconds to wait for the service or <code>-1</code> to
   *     use the {@link org.ops4j.pax.exam.Constants#EXAM_SERVICE_TIMEOUT_KEY} system property value
   *     as described in {@link #ConfigurationAdmin(long)}
   */
  public ConfigurationAdmin(long timeout) {
    super(org.osgi.service.cm.ConfigurationAdmin.class, timeout);
  }

  /**
   * This constructor is designed to be used by the {@link org.codice.pax.exam.config.Configuration}
   * annotation in order to initialize this method rule automatically.
   *
   * @param annotation the annotation referencing this method rule
   */
  public ConfigurationAdmin(org.codice.pax.exam.junit.ConfigurationAdmin annotation) {
    this(annotation.timeout());
  }

  // --- OSGi ConfigurationAdmin API

  @Override
  public org.osgi.service.cm.Configuration createFactoryConfiguration(String factoryPid)
      throws IOException {
    return new Configuration(
        this, ConfigurationAdmin::stabilize0, getService().createFactoryConfiguration(factoryPid));
  }

  @Override
  public org.osgi.service.cm.Configuration createFactoryConfiguration(
      String factoryPid, String location) throws IOException {
    return new Configuration(
        this,
        ConfigurationAdmin::stabilize0,
        getService().createFactoryConfiguration(factoryPid, location));
  }

  @Override
  public org.osgi.service.cm.Configuration getConfiguration(String pid, String location)
      throws IOException {
    return new Configuration(
        this, ConfigurationAdmin::stabilize0, getService().getConfiguration(pid, location));
  }

  @Override
  public org.osgi.service.cm.Configuration getConfiguration(String pid) throws IOException {
    return new Configuration(
        this, ConfigurationAdmin::stabilize0, getService().getConfiguration(pid));
  }

  @Override
  public org.osgi.service.cm.Configuration getFactoryConfiguration(
      String factoryPid, String name, String location) throws IOException {
    return new Configuration(
        this,
        ConfigurationAdmin::stabilize0,
        getService().getFactoryConfiguration(factoryPid, name, location));
  }

  @Override
  public org.osgi.service.cm.Configuration getFactoryConfiguration(String factoryPid, String name)
      throws IOException {
    return new Configuration(
        this,
        ConfigurationAdmin::stabilize0,
        getService().getFactoryConfiguration(factoryPid, name));
  }

  @Override
  public org.osgi.service.cm.Configuration[] listConfigurations(String filter)
      throws IOException, InvalidSyntaxException {
    final org.osgi.service.cm.Configuration[] cfgs = getService().listConfigurations(filter);

    if (cfgs != null) {
      for (int i = 0; i < cfgs.length; i++) {
        cfgs[i] = new Configuration(this, ConfigurationAdmin::stabilize0, cfgs[i]);
      }
    }
    return cfgs;
  }

  // --- Extended ConfigurationAdmin API

  /**
   * Waits a maximum of time for the configuration admin to stabilize. This methods attempts to
   * ensure that the configuration admin service is done dispatching all configuration events.
   *
   * @param timeout the maximum amount of time in milliseconds to wait for the config admin to
   *     stabilize
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ConfigTimeoutException if we timed out before the config admin was able to stabilize
   */
  public void stabilize(long timeout) throws InterruptedException {
    LOGGER.info("Stabilizing OSGi configuration");
    final long end = System.currentTimeMillis() + timeout;
    final Dictionary<String, Object> props = new Hashtable<>(6);

    props.put(ConfigurationAdmin.TIME_KEY, System.currentTimeMillis());
    try {
      internalConfig.updateAndWait(props, end - System.currentTimeMillis());
    } catch (IOException e) {
      throw new ConfigException(
          "unable to update internal config object: " + ConfigurationAdmin.INTERNAL_PID, e);
    }
  }

  /**
   * Get an existing current configuration object from the persistent store if it exist. Only
   * configuration objects with non-<code>null</code> properties are considered current. That is,
   * <code>Configuration.getProperties()</code> is guaranteed not to return <code>null</code> for
   * the returned configuration object.
   *
   * <p>Normally only configuration objects that are bound to the location of the calling bundle are
   * returned, or all if the caller has <code>ConfigurationPermission[*,CONFIGURE]</code>.
   *
   * @param pid the persistent id of the config object to retrieved
   * @return the corresponding config object or empty if does not exist
   * @throws ConfigException if a failure occur while attempting to retrieve the config object
   */
  public Optional<Configuration> configuration(String pid) {
    try {
      // we use listConfigurations() to not bind the config object to our bundle if it was not bound
      // yet as we want to make sure that it will be bound to its corresponding service
      final String filter = String.format("(%s=%s)", Constants.SERVICE_PID, pid);
      final org.osgi.service.cm.Configuration[] configs = listConfigurations(filter);

      return ((configs != null) && (configs.length > 0))
          ? Optional.of(configs[0]).map(Configuration.class::cast)
          : Optional.empty();
    } catch (IOException | InvalidSyntaxException e) {
      // InvalidSyntaxException should not happen as we are hardcoding the filter
      throw new ConfigException("failed to retrieved existing configuration: " + pid, e);
    }
  }

  /**
   * Gets all current configuration objects. Only configuration objects with non-<code>null</code>
   * properties are considered current. That is, <code>Configuration.getProperties()</code> is
   * guaranteed not to return <code>null</code> for each of the returned configuration objects.
   *
   * <p>Normally only configuration objects that are bound to the location of the calling bundle are
   * returned, or all if the caller has <code>ConfigurationPermission[*,CONFIGURE]</code>.
   *
   * @return a stream of all current configuration objects
   */
  public Stream<Configuration> configurations() {
    return configurations(null);
  }

  /**
   * Gets all current factory configuration objects for the given factory PID. Only configuration
   * objects with non-<code>null</code> properties are considered current. That is, <code>
   * Configuration.getProperties()</code> is guaranteed not to return <code>null</code> for each of
   * the returned configuration objects.
   *
   * <p>Normally only configuration objects that are bound to the location of the calling bundle are
   * returned, or all if the caller has <code>ConfigurationPermission[*,CONFIGURE]</code>.
   *
   * @param factoryPid the factory persistent id for which to retrieve all its configuration objects
   * @return a stream of all current factory configuration objects for the specified factory PID
   */
  public Stream<Configuration> factoryConfigurations(String factoryPid) {
    return factoryConfigurations(factoryPid, null);
  }

  /**
   * Gets all current factory configuration objects for the given factory PID which matches the
   * specified filter. Only configuration objects with non-<code>null</code> properties are
   * considered current. That is, <code>Configuration.getProperties()</code> is guaranteed not to
   * return <code>null</code> for each of the returned configuration objects.
   *
   * <p>Normally only configuration objects that are bound to the location of the calling bundle are
   * returned, or all if the caller has <code>ConfigurationPermission[*,CONFIGURE]</code>.
   *
   * @param factoryPid the factory persistent id for which to retrieve all its configuration objects
   * @param filter an optional filter string to further restrict the factory configuration objects
   *     to retrieve
   * @return a stream of all current factory configuration objects for the specified factory PID
   *     that matches the optional filter
   */
  public Stream<Configuration> factoryConfigurations(String factoryPid, @Nullable String filter) {
    final StringBuilder sb = new StringBuilder(80);

    sb.append("(&(")
        .append(org.osgi.service.cm.ConfigurationAdmin.SERVICE_FACTORYPID)
        .append("=")
        .append(factoryPid)
        .append(')');
    if (filter != null) {
      sb.append(filter);
    }
    sb.append(')');
    return configurations(sb.toString());
  }

  /**
   * Gets all current configuration objects which match the filter. Only configuration objects with
   * non-<code>null</code> properties are considered current. That is, <code>
   * Configuration.getProperties()</code> is guaranteed not to return <code>null</code> for each of
   * the returned configuration objects.
   *
   * <p>Normally only configuration objects that are bound to the location of the calling bundle are
   * returned, or all if the caller has <code>ConfigurationPermission[*,CONFIGURE]</code>.
   *
   * <p>The syntax of the filter string is as defined in the Filter class. The filter can test any
   * configuration properties including the following:
   *
   * <ul>
   *   <li><code>service.pid=String</code> - the PID under which this is registered
   *   <li><code>service.factoryPid=String</code> - the factory PID if applicable
   *   <li><code>service.bundleLocation=String</code> - the bundle location
   * </ul>
   *
   * <p>The filter can also be <code>null</code>, meaning that all configuration objects should be
   * returned.
   *
   * @param filter a filter string, or <code>null</code> to retrieve all config objects
   * @return a stream of all current config objects matching the given filter
   * @throws ConfigException if a failure occur while to retrieve the config objects
   */
  public Stream<Configuration> configurations(@Nullable String filter) {
    try {
      final org.osgi.service.cm.Configuration[] configurations = listConfigurations(filter);

      return (configurations != null)
          ? Stream.of(configurations).map(Configuration.class::cast)
          : Stream.empty();
    } catch (IOException | InvalidSyntaxException e) {
      throw new ConfigException("failed to retrieved existing configurations", e);
    }
  }

  // - JUnit Rule API (not to be called directly)

  @Override
  public Statement apply(Statement statement, FrameworkMethod method, Object target) {
    // super.apply() will make sure to inject the config admin service before calling our statement
    return super.apply(
        new Statement() {
          @Override
          public void evaluate() throws Throwable {
            try {
              findConfigurationAdminThreadGroup();
              ConfigurationAdmin.this.internalConfig =
                  ((Configuration) getConfiguration(ConfigurationAdmin.INTERNAL_PID));

              takeSnapshot();

              final Map<String, ToUpdate> toUpdate = new LinkedHashMap<>();

              processClassAnnotations(toUpdate, target);
              processConfigurationBefores(toUpdate, method, target);
              processMethodAnnotations(toUpdate, method, target);

              if (!toUpdate.isEmpty()) {
                toUpdate.values().forEach(ToUpdate::execute);
                // stabilize to let the configuration initialization update happens
                stabilize(ConfigurationAdmin.STABILIZE_TIMEOUT);
              }

              // proceed with the test method
              statement.evaluate();
            } finally {
              restoreSnapshot();
            }
          }
        },
        method,
        target);
  }

  private void findConfigurationAdminThreadGroup() {
    if (ConfigurationAdmin.threadGroup != null) {
      return;
    }
    final ThreadGroup top = getTopParentThreadGroup();

    while (true) {
      // use a bigger array to make sure we get all in case it changes in between calls
      final ThreadGroup[] groups = new ThreadGroup[top.activeCount() * 2];
      final int length = top.enumerate(groups, true);
      final ThreadGroup group =
          Stream.of(groups)
              .limit(length)
              .filter(tg -> tg.getName().equals(ConfigurationAdmin.THREAD_GROUP))
              .findAny()
              .orElse(null);

      if (group != null) {
        ConfigurationAdmin.threadGroup = group;
        return;
      } else if (length <= groups.length) {
        throw new ConfigException("unable to find configuration admin thread group");
      } // else - try again as it would look like a lot more thread groups appeared in between calls
    }
  }

  private ThreadGroup getTopParentThreadGroup() {
    ThreadGroup group = Thread.currentThread().getThreadGroup();

    while (true) {
      final ThreadGroup parent = group.getParent();

      if (parent == null) {
        return group;
      }
      group = parent;
    }
  }

  // --- Snapshots

  private void takeSnapshot() throws InterruptedException {
    // only take a snapshot the first time around
    synchronized (ConfigurationAdmin.snapshotConfigs) {
      if (ConfigurationAdmin.snapshotConfigs.isEmpty()) {
        // stabilize the system before taking a snapshot
        stabilize(ConfigurationAdmin.STABILIZE_TIMEOUT);
        LOGGER.info("Snapshooting OSGi configuration");
        configurations()
            .map(ConfigurationSnapshot::new)
            .forEach(c -> ConfigurationAdmin.snapshotConfigs.put(c.getPid(), c));
      }
    }
  }

  private void restoreSnapshot() throws InterruptedException {
    final Map<String, Configuration> currentConfigs =
        configurations().collect(Collectors.toMap(Configuration::getPid, Function.identity()));

    LOGGER.info("Resetting OSGi configuration");
    // start by deleting config objects that shouldn't be there and updating those that changes
    currentConfigs.forEach(
        (pid, current) -> {
          final ConfigurationSnapshot snapshot = ConfigurationAdmin.snapshotConfigs.get(pid);

          if (snapshot == null) {
            delete(current);
          } else {
            update(current, snapshot.getBundleLocation(), current.getProperties(), "restore");
          }
        });
    // recreate all configs that are no longer there
    ConfigurationAdmin.snapshotConfigs
        .entrySet()
        .stream()
        .filter(e -> !currentConfigs.containsKey(e.getKey()))
        .forEach(this::recreate);
    // finally, make sure everything is stable before continuing
    stabilize(ConfigurationAdmin.STABILIZE_TIMEOUT);
  }

  // --- class and method annotations

  private void processClassAnnotations(Map<String, ToUpdate> toUpdate, Object target) {
    // find class annotations only the first time around and cache them
    synchronized (ConfigurationAdmin.class) {
      if (ConfigurationAdmin.classAnnotations == null) {
        ConfigurationAdmin.classAnnotations =
            ReflectionUtils.annotationsByTypes(
                    target.getClass(), ConfigurationAdmin.META_ANNOTATIONS)
                .collect(Collectors.toList());
      }
    }
    ConfigurationAdmin.classAnnotations.forEach(a -> processAnnotation(toUpdate, target, a));
  }

  private void processMethodAnnotations(
      Map<String, ToUpdate> toUpdate, FrameworkMethod method, Object target) {
    ReflectionUtils.annotationsByTypes(method.getMethod(), ConfigurationAdmin.META_ANNOTATIONS)
        .forEach(a -> processAnnotation(toUpdate, target, a));
  }

  private void processAnnotation(
      Map<String, ToUpdate> toUpdate, Object target, AnnotationEntry<?> entry) {
    final Annotation a = entry.getAnnotation();

    if (a instanceof Configuration.Annotation) {
      processConfigurationAnnotation(toUpdate, (AnnotationEntry<Configuration.Annotation>) entry);
    } else if (a instanceof Configuration.Resources) {
      processConfigurationResources(
          toUpdate, target, (AnnotationEntry<Configuration.Resources>) entry);
    } else if (a instanceof Configuration.Property.Remove) {
      processConfigurationPropertyRemove(toUpdate, (Configuration.Property.Remove) a);
    } else { // instanceof of Configuration.Property.SetXXXX
      new SetInfo(a).process(toUpdate);
    }
  }

  // --- @Configuration.Resources

  private void processConfigurationResources(
      Map<String, ToUpdate> toUpdate,
      Object target,
      AnnotationEntry<Configuration.Resources> entry) {
    final Configuration.Resources cr = entry.getAnnotation();
    final ClassLoader entryClassloader = entry.getEnclosingClassLoader();
    final ClassLoader classloader =
        (entryClassloader != null) ? entryClassloader : target.getClass().getClassLoader();

    for (final String name : cr.value()) {
      // load the resource files only the first time around and cache it but process it each time
      ConfigurationAdmin.resources
          .computeIfAbsent(name, n -> loadConfigurationResource(classloader, n))
          .process(toUpdate);
    }
  }

  private Resource loadConfigurationResource(ClassLoader classloader, String name) {
    try (final InputStream is = classloader.getResourceAsStream(name)) {
      final Dictionary<String, Object> properties = ConfigurationHandler.read(is);

      return new Resource(name, properties);
    } catch (IOException e) {
      throw new ConfigException("failed to read resource configuration: " + name, e);
    }
  }

  /**
   * Used to represent a resource loaded from a configuration resource file with its properties and
   * its object id via a fake {@link Configuration.Id} annotation.
   */
  private class Resource implements Configuration.Id {
    private final String name;
    private final String pid;
    private final String fpid;
    private final String filter;
    private final boolean overwrite;
    private final Map<String, Object> properties;

    Resource(String name, Dictionary<String, Object> properties) {
      this.name = name;
      this.pid = removeAndValidateString(properties, Constants.SERVICE_PID);
      this.fpid =
          removeAndValidateString(
              properties, org.osgi.service.cm.ConfigurationAdmin.SERVICE_FACTORYPID);
      this.filter = removeAndValidateString(properties, Configuration.SERVICE_FACTORY_FILTER);
      this.overwrite = removeAndValidateBoolean(properties, Configuration.SERVICE_OVERWRITE);
      ConfigurationAdmin.validate(this, name);
      if (properties instanceof Map) {
        this.properties = new HashMap<>((Map<String, Object>) properties);
      } else {
        this.properties = new HashMap<>(properties.size() * 3 / 2);
        for (Enumeration<String> e = properties.keys(); e.hasMoreElements(); ) {
          final String key = e.nextElement();

          this.properties.put(key, properties.get(key));
        }
      }
    }

    @Override // required for annotation instances
    public Class<? extends Annotation> annotationType() {
      return Configuration.Id.class;
    }

    @Override
    public String pid() {
      return pid;
    }

    @Override
    public String factoryPid() {
      return fpid;
    }

    @Override
    public String filter() {
      return filter;
    }

    public void process(Map<String, ToUpdate> toUpdate) {
      processProperties(toUpdate, this, overwrite, new HashMap<>(properties));
    }

    @Override
    public int hashCode() {
      return Objects.hash(pid, fpid, filter);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof Configuration.Id) {
        final Configuration.Id id = (Configuration.Id) obj;

        return Objects.equals(pid, id.pid())
            && Objects.equals(fpid, id.factoryPid())
            && Objects.equals(filter, id.filter());
      }
      return false;
    }

    @Override
    public String toString() {
      return String.format(
          "%s(%s=%s, %s=%s, %s=%s)",
          name,
          Constants.SERVICE_PID,
          pid,
          org.osgi.service.cm.ConfigurationAdmin.SERVICE_FACTORYPID,
          fpid,
          Configuration.SERVICE_FACTORY_FILTER,
          filter);
    }

    private String removeAndValidateString(Dictionary<String, Object> properties, String key) {
      final Object obj = properties.remove(key);

      if (obj == null) {
        return "";
      } else if (obj instanceof String) {
        return (String) obj;
      }
      throw new ConfigException(
          "invalid '"
              + key
              + "' defined in configuration resource '"
              + name
              + "'; expecting a string: "
              + obj);
    }

    private boolean removeAndValidateBoolean(Dictionary<String, Object> properties, String key) {
      final Object obj = properties.remove(key);

      if (obj == null) {
        return true;
      } else if (obj instanceof Boolean) {
        return (Boolean) obj;
      }
      throw new ConfigException(
          "invalid '"
              + key
              + "' defined in configuration resource '"
              + name
              + "'; expecting a boolean: "
              + obj);
    }
  }

  // --- @Configuration.Annotation

  private void processConfigurationAnnotation(
      Map<String, ToUpdate> toUpdate, AnnotationEntry<Configuration.Annotation> entry) {
    final Configuration.Annotation ca = entry.getAnnotation();
    final Configuration.Id id = ca.object();
    final Map<String, Object> props =
        getPropertiesFromExtension(ca.value(), id, entry.getEnclosingAnnotation().getAnnotation());

    processProperties(toUpdate, id, ca.overwrite(), props);
  }

  @Nullable
  private Map<String, Object> getPropertiesFromExtension(
      Class<? extends Configuration.Extension> clazz, Configuration.Id id, Annotation annotation) {
    try {
      return clazz.newInstance().configuration(id, annotation);
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) {
      throw new ConfigException(
          "failed to retrieve properties from configuration extension '"
              + clazz.getName()
              + "' for "
              + id
              + " and "
              + annotation,
          t);
    }
  }

  // --- @Configuration.Property.Remove

  private void processConfigurationPropertyRemove(
      Map<String, ToUpdate> toUpdate, Configuration.Property.Remove cpr) {
    get(ConfigurationAdmin.validate(cpr.object(), cpr))
        .ifPresent(
            cfg -> {
              final String pid = cfg.getPid();
              final ToUpdate cached = toUpdate.get(pid);
              final Dictionary<String, Object> properties =
                  (cached != null) ? cached.properties : cfg.getProperties();

              properties.remove(cpr.key());
              toUpdate.put(pid, new ToUpdate(cfg, properties));
            });
  }

  // --- @Configuration.Property.SetXXXX

  /** used to reconcile the fact that we need so many different SetXXXX annotations. */
  private class SetInfo {
    private final Annotation annotation;
    private final Configuration.Id object;
    private final String key;
    private final Configuration.Property.Cardinality cardinality;
    private final Object value;

    SetInfo(Annotation a) {
      this.annotation = a;
      // calling a.value() returns a clone so no change will propagate to annotation
      if (a instanceof Configuration.Property.SetString) {
        final Configuration.Property.SetString cps = (Configuration.Property.SetString) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else if (a instanceof Configuration.Property.SetLong) {
        final Configuration.Property.SetLong cps = (Configuration.Property.SetLong) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else if (a instanceof Configuration.Property.SetInteger) {
        final Configuration.Property.SetInteger cps = (Configuration.Property.SetInteger) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else if (a instanceof Configuration.Property.SetShort) {
        final Configuration.Property.SetShort cps = (Configuration.Property.SetShort) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else if (a instanceof Configuration.Property.SetCharacter) {
        final Configuration.Property.SetCharacter cps = (Configuration.Property.SetCharacter) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else if (a instanceof Configuration.Property.SetByte) {
        final Configuration.Property.SetByte cps = (Configuration.Property.SetByte) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else if (a instanceof Configuration.Property.SetDouble) {
        final Configuration.Property.SetDouble cps = (Configuration.Property.SetDouble) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else if (a instanceof Configuration.Property.SetFloat) {
        final Configuration.Property.SetFloat cps = (Configuration.Property.SetFloat) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else if (a instanceof Configuration.Property.SetBoolean) {
        final Configuration.Property.SetBoolean cps = (Configuration.Property.SetBoolean) a;

        this.object = cps.object();
        this.key = cps.key();
        this.cardinality = cps.cardinality();
        this.value = cps.value();
      } else {
        throw new ConfigException(
            "unknown @Configuration.Property.SetXXXX annotation: "
                + a
                + "; ("
                + a.getClass().getName()
                + ')');
      }
      ConfigurationAdmin.validate(object, a);
    }

    void process(Map<String, ToUpdate> toUpdate) {
      final Configuration cfg = getOrCreate(object);
      final String pid = cfg.getPid();
      final ToUpdate cached = toUpdate.get(pid);
      final Dictionary<String, Object> properties =
          (cached != null) ? cached.properties : cfg.getProperties();
      final int length = Array.getLength(value);

      switch (cardinality) {
        case LIST: // Felix creates those as ArrayList and not as Vector
          properties.put(
              key,
              IntStream.range(0, length)
                  .mapToObj(i -> Array.get(value, i))
                  .collect(Collectors.toList()));
          break;
        case VALUE_OR_ARRAY:
          if (length == 1) {
            final Object val = Array.get(value, 0);

            properties.put(key, val);
            break;
          } // else - fallthrough and deal with it as an array (length 0 or more than 1)
        case ARRAY:
          properties.put(key, value);
      }
      toUpdate.put(pid, new ToUpdate(cfg, properties));
    }
  }

  // --- @Configuration.Before

  private void processConfigurationBefores(
      Map<String, ToUpdate> toUpdate, FrameworkMethod method, Object target) {
    ReflectionUtils.getAllAnnotationsForMethodsAnnotatedWith(
            target.getClass(), Configuration.Before.class, true)
        .forEach(
            (m, cbs) ->
                processConfigurationBefores(
                    toUpdate, m, cbs[0], // not repeatable so only one can exist
                    method, target));
  }

  private void processConfigurationBefores(
      Map<String, ToUpdate> toUpdate,
      Method m,
      Configuration.Before cb,
      FrameworkMethod method,
      Object target) {
    if (!ArrayUtils.isEmpty(cb.method()) && !ArrayUtils.contains(cb.method(), method.getName())) {
      return;
    }
    verifyConfigurationBeforeMethodReturnType(m);
    m.setAccessible(true);
    try {
      final Map<String, Object> props = (Map<String, Object>) m.invoke(target);

      processProperties(toUpdate, cb.object(), cb.overwrite(), props);
    } catch (VirtualMachineError e) {
      throw e;
    } catch (InvocationTargetException e) {
      throw new ConfigException(
          "failed to invoke @Configuration.Before method: " + ConfigurationAdmin.toString(m),
          e.getTargetException());
    } catch (Throwable t) { // should not happen
      throw new ConfigException(
          "failed to invoke @Configuration.Before method: " + ConfigurationAdmin.toString(m), t);
    }
  }

  private void verifyConfigurationBeforeMethodReturnType(Method m) {
    final Type type = m.getGenericReturnType();

    if (type instanceof ParameterizedType) {
      final ParameterizedType ptype = (ParameterizedType) type;
      final Type[] atypes = ptype.getActualTypeArguments();

      if ((ptype.getRawType() == Map.class)
          && (atypes.length == 2)
          && (atypes[0] == String.class)
          && (atypes[1] == Object.class)) {
        return;
      }
    }
    throw new ConfigException(
        "invalid method annotated with @Configuration.Before; doesn't return a Map<String, Object>: "
            + ConfigurationAdmin.toString(m));
  }

  // --- miscellaneous

  private Optional<Configuration> get(Configuration.Id cid) {
    final String fpid = cid.factoryPid();

    return (!fpid.isEmpty()
        ? factoryConfigurations(fpid, cid.filter()).findFirst()
        : configuration(cid.pid()));
  }

  private Configuration getOrCreate(Configuration.Id cid) {
    return get(cid).orElseGet(() -> create(cid));
  }

  private Configuration create(Configuration.Id cid) {
    final String fpid = cid.factoryPid();

    if (!fpid.isEmpty()) {
      try {
        return (Configuration) createFactoryConfiguration(fpid, null);
      } catch (IOException e) {
        throw new ConfigException("failed to create factory configuration: " + fpid, e);
      }
    }
    final String pid = cid.pid();

    try {
      return (Configuration) getConfiguration(pid, null);
    } catch (IOException e) {
      throw new ConfigException("failed to create configuration: " + pid, e);
    }
  }

  private void recreate(Map.Entry<String, ConfigurationSnapshot> entry) {
    final ConfigurationSnapshot snapshot = entry.getValue();
    final String pid = entry.getKey();
    final String fpid = snapshot.getFactoryPid();
    final String bundleLocation = snapshot.getBundleLocation();
    final Dictionary<String, Object> properties = snapshot.getProperties();
    final org.osgi.service.cm.Configuration cfg;

    if (fpid != null) {
      try {
        cfg = getService().createFactoryConfiguration(fpid, bundleLocation);
      } catch (IOException e) {
        throw new ConfigException("failed to recreate factory configuration: " + fpid, e);
      }
    } else {
      try {
        cfg = getService().getConfiguration(pid, bundleLocation);
      } catch (IOException e) {
        throw new ConfigException("failed to recreate configuration: " + pid, e);
      }
    }
    if (properties != null) {
      try {
        cfg.update(properties);
      } catch (IOException e) {
        throw new ConfigException(
            "failed to update properties for recreated configuration: " + pid, e);
      }
    }
  }

  private void delete(Configuration cfg) {
    try {
      cfg.delete();
    } catch (IOException ioe) {
      throw new ConfigException("failed to delete configuration: " + cfg.getPid(), ioe);
    }
  }

  private void update(
      Configuration current,
      @Nullable String bundleLocation,
      Dictionary<String, Object> properties,
      String what) {
    if (!Objects.equals(bundleLocation, current.getBundleLocation())) {
      current.setBundleLocation(bundleLocation);
    }
    if (!ConfigurationAdmin.equals(properties, current.getProperties())) {
      try {
        current.update(properties);
      } catch (IOException e) {
        throw new ConfigException("failed to " + what + " configuration: " + current.getPid(), e);
      }
    }
  }

  private void processProperties(
      Map<String, ToUpdate> toUpdate,
      Configuration.Id id,
      boolean overwrite,
      @Nullable Map<String, Object> props) {
    if (overwrite) {
      if ((props == null) || props.isEmpty()) { // don't create the object if it exist
        get(id)
            .ifPresent(
                cfg -> {
                  // set it with an empty dictionary to trigger delete or empty slate
                  toUpdate.put(cfg.getPid(), new ToUpdate(cfg, new Hashtable<>()));
                });
      } else {
        final Configuration cfg = getOrCreate(id);

        toUpdate.put(cfg.getPid(), new ToUpdate(cfg, new Hashtable<>(props)));
      }
    } else if ((props != null) && !props.isEmpty()) {
      final Configuration cfg = getOrCreate(id);
      final String pid = cfg.getPid();
      final ToUpdate cached = toUpdate.get(pid);

      toUpdate.put(
          pid,
          new ToUpdate(
              cfg,
              ConfigurationAdmin.merge(
                  props, (cached != null) ? cached.properties : cfg.getProperties())));
    } // else - no properties to merge so do nothing
  }

  /** Records an update for a particular config object to be done before a test method is invoked */
  private class ToUpdate {
    private final Configuration cfg;
    final Dictionary<String, Object> properties;

    ToUpdate(Configuration cfg, Dictionary<String, Object> properties) {
      this.cfg = cfg;
      this.properties = properties;
    }

    void execute() {
      if (properties.isEmpty()) { // we should delete the configuration object
        ConfigurationAdmin.this.delete(cfg);
      } else {
        ConfigurationAdmin.this.update(
            cfg, null, properties, (cfg.getProperties() == null) ? "created" : "updated");
      }
    }
  }

  private static void stabilize0(long timeout) throws InterruptedException {
    // TODO: we need to figure out a way to detect that config admin is done calling all registered
    // listeners and those are done (like for Blueprint which creates a separate thread from the
    // listener callback) processing the update
    //    final long end = System.currentTimeMillis() + timeout;
    //    int found = 0;
    //
    //    while ((found = ConfigurationAdmin.checkConfigurationAdminThreadGroup())
    //        != ConfigurationAdmin.EXPECTED_THREADS) {
    //      System.out.println(
    //          "*** CONFIG ADMIN THREADS EXPECTED: "
    //              + found
    //              + "/"
    //              + ConfigurationAdmin.EXPECTED_THREADS);
    //      return;
    //      final long remaining = end - System.currentTimeMillis();
    //
    //      if (remaining <= 0L) {
    //        throw new ConfigTimeoutException(
    //            "timed out waiting for config admin to stabilize ("
    //                + found
    //                + "/"
    //                + ConfigurationAdmin.EXPECTED_THREADS
    //                + ")");
    //      }
    //      Thread.sleep(Math.min(remaining, 1000L));
    //      System.out.println("*** CONFIG ADMIN STABILIZING ***");
    //    }
  }

  //  private static int checkConfigurationAdminThreadGroup() {
  //    final int length = ConfigurationAdmin.threadGroup.activeCount();
  //    // use a bigger array to make sure we get all in case it changes in between calls
  //    final Thread[] threads = new Thread[length * 2];
  //    final int found = ConfigurationAdmin.threadGroup.enumerate(threads, true);
  //
  //    System.out.println("*** CONFIG ADMIN THREADS FOUND: " + found);
  //    Stream.of(threads)
  //        .limit(found)
  //        .forEach(t -> System.out.println("*** CONFIG ADMIN THREAD: " + t.getName()));
  //    return found;
  //  }

  private static boolean equals(Dictionary<String, Object> x, Dictionary<String, Object> y) {
    if (x.size() != y.size()) {
      return false;
    }
    for (final Enumeration<String> e = x.keys(); e.hasMoreElements(); ) {
      final String key = e.nextElement();

      if (!Objects.deepEquals(x.get(key), y.get(key))) {
        return false;
      }
    }
    return true;
  }

  private static Configuration.Id validate(Configuration.Id id, Object where) {
    if (!id.factoryPid().isEmpty()) {
      if (!id.pid().isEmpty()) {
        throw new ConfigException(
            "only one of 'factoryPid' or `pid` can be specified in ["
                + id
                + "] defined in ["
                + where
                + ']');
      }
    } else if (!id.pid().isEmpty()) {
      if (!id.filter().isEmpty()) {
        throw new ConfigException(
            "'filter' can only be used with `factoryPid` in ["
                + id
                + "] defined in ["
                + where
                + ']');
      }
    } else {
      throw new ConfigException(
          "must specify at least one of 'pid' or `factoryPid` in ["
              + id
              + "] defined in ["
              + where
              + ']');
    }
    return id;
  }

  private static String toString(Method m) {
    return m.getDeclaringClass().getName() + '.' + m.getName() + "()";
  }

  private static Dictionary<String, Object> merge(
      Map<String, Object> properties, Dictionary<String, Object> destination) {
    properties.forEach(
        (k, v) -> {
          if (v != null) {
            destination.put(k, v);
          } else {
            destination.remove(k);
          }
        });
    return destination;
  }
}
