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
package org.codice.pax.exam.config;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Dictionary;
import java.util.Map;
import java.util.Set;
import org.codice.pax.exam.config.internal.InternalConfigListener;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationListener;

/**
 * Defines an extension to OSGi {@link org.osgi.service.cm.Configuration} which provides additional
 * capabilities useful when testing OSGi applications.
 */
public class Configuration implements org.osgi.service.cm.Configuration {
  /**
   * Configuration property naming an optional filter to use to find the factory configuration
   * object. This filter is added to the filter created from the factory PID specified using the
   * {@link ConfigurationAdmin#SERVICE_FACTORYPID} property when searching for a matching
   * configuration object.
   */
  public static final String SERVICE_FACTORY_FILTER = "service.factoryFilter";

  /**
   * Configuration property naming an optional boolean flag to specify whether the properties
   * provided in the resource configuration file should overwrite the properties defined in any
   * pre-existing configuration object or simply be merged with. By default properties are
   * overwritten with those provided in the resource configuration file.
   *
   * @return <code>true</code> if the properties provided by the config resource file should
   *     correspond to the new config object properties or <code>false</code> if they should be
   *     merged with any existing properties
   */
  public static final String SERVICE_OVERWRITE = "service.overwrite";

  private final org.codice.pax.exam.junit.rules.ConfigurationAdmin configAdmin;

  private final ConfigStabilizer stabilizer;

  private final org.osgi.service.cm.Configuration delegate;

  private final String pid;

  /**
   * Creates a configuration proxy for the specified configuration object.
   *
   * @param configAdmin the configuration admin for which we are creating this config object
   * @param stabilizer the stabilizer utility to use when performing <code>xxxxAndWait()</code>
   *     methods to wait for the config admin to stabilize after a config change has been applied
   * @param delegate the configuration object to delegate to
   */
  public Configuration(
      org.codice.pax.exam.junit.rules.ConfigurationAdmin configAdmin,
      ConfigStabilizer stabilizer,
      org.osgi.service.cm.Configuration delegate) {
    this.configAdmin = configAdmin;
    this.stabilizer = stabilizer;
    this.delegate = delegate;
    this.pid = delegate.getPid();
  }

  @Override
  public String getPid() {
    return delegate.getPid();
  }

  @Override
  public String getFactoryPid() {
    return delegate.getFactoryPid();
  }

  @Override
  public Dictionary<String, java.lang.Object> getProperties() {
    return delegate.getProperties();
  }

  @Override
  public void setBundleLocation(String location) {
    delegate.setBundleLocation(location);
  }

  @Override
  public String getBundleLocation() {
    return delegate.getBundleLocation();
  }

  @Override
  public long getChangeCount() {
    return delegate.getChangeCount();
  }

  @Override
  public void update() throws IOException {
    delegate.update();
  }

  @Override
  public void update(Dictionary<String, ?> properties) throws IOException {
    delegate.update(properties);
  }

  @Override
  public void delete() throws IOException {
    delegate.delete();
  }

  @Override
  public Dictionary<String, Object> getProcessedProperties(ServiceReference<?> ref) {
    return delegate.getProcessedProperties(ref);
  }

  @Override
  public boolean updateIfDifferent(Dictionary<String, ?> properties) throws IOException {
    return delegate.updateIfDifferent(properties);
  }

  @Override
  public void addAttributes(ConfigurationAttribute... attributes) throws IOException {
    delegate.addAttributes(attributes);
  }

  @Override
  public Set<ConfigurationAttribute> getAttributes() {
    return delegate.getAttributes();
  }

  @Override
  public void removeAttributes(ConfigurationAttribute... attributes) throws IOException {
    delegate.removeAttributes(attributes);
  }

  /**
   * Provides an enhancement over the regular {@link #update()} where the configuration admin will
   * wait for the configuration changes to stabilize in the system for a specified amount of time.
   *
   * @param timeout the maximum amount of time in milliseconds to wait for the configuration change
   *     to stabilize
   * @throws IOException if update cannot be made persistent
   * @throws IllegalStateException If this configuration has been deleted
   * @throws InterruptedException if the thread was interrupted while waiting for the config changes
   *     to stabilize
   */
  public void updateAndWait(long timeout) throws IOException, InterruptedException {
    final long end = System.currentTimeMillis() + timeout;
    final InternalConfigListener listener = new InternalConfigListener(pid);
    final ServiceRegistration<ConfigurationListener> registration =
        configAdmin.getBundleContext().registerService(ConfigurationListener.class, listener, null);

    try {
      update();
      // wait for the asynchronous event related to the update before stabilizing config admin
      listener.waitForNotification(end - System.currentTimeMillis());
    } finally {
      registration.unregister();
    }
    stabilizer.stabilize(end - System.currentTimeMillis());
  }

  /**
   * Provides an enhancement over the regular {@link #update(Dictionary)} where the configuration
   * admin will wait for the configuration changes to stabilize in the system for a specified amount
   * of time.
   *
   * @param properties the new set of properties for this configuration
   * @param timeout the maximum amount of time in milliseconds to wait for the configuration change
   *     to stabilize
   * @throws IOException if update cannot be made persistent
   * @throws IllegalArgumentException if the dictionary object contains invalid configuration types
   *     or contains case variants of the same key name
   * @throws IllegalStateException If this configuration has been deleted
   * @throws InterruptedException if the thread was interrupted while waiting for the config changes
   *     to stabilize
   */
  public void updateAndWait(Dictionary<String, ?> properties, long timeout)
      throws IOException, InterruptedException {
    final long end = System.currentTimeMillis() + timeout;
    final InternalConfigListener listener = new InternalConfigListener(pid);
    final ServiceRegistration<ConfigurationListener> registration =
        configAdmin.getBundleContext().registerService(ConfigurationListener.class, listener, null);

    try {
      update(properties);
      // wait for the asynchronous event related to the update before stabilizing config admin
      listener.waitForNotification(end - System.currentTimeMillis());
    } finally {
      registration.unregister();
    }
    stabilizer.stabilize(end - System.currentTimeMillis());
  }

  /**
   * Provides an enhancement over the regular {@link #delete()} where the configuration admin will
   * wait for the configuration changes to stabilize in the system for a specified amount of time.
   *
   * @param timeout the maximum amount of time in milliseconds to wait for the configuration change
   *     to stabilize
   * @throws IOException if delete fails
   * @throws IllegalStateException If this configuration has been deleted
   * @throws InterruptedException if the thread was interrupted while waiting for the config changes
   *     to stabilize
   */
  public void deleteAndWait(long timeout) throws IOException, InterruptedException {
    final long end = System.currentTimeMillis() + timeout;
    final InternalConfigListener listener = new InternalConfigListener(pid);
    final ServiceRegistration<ConfigurationListener> registration =
        configAdmin.getBundleContext().registerService(ConfigurationListener.class, listener, null);

    try {
      delete();
      // wait for the asynchronous event related to the delete before stabilizing config admin
      listener.waitForNotification(end - System.currentTimeMillis());
    } finally {
      registration.unregister();
    }
    stabilizer.stabilize(end - System.currentTimeMillis());
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(java.lang.Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof Configuration) {
      return delegate.equals(((Configuration) obj).delegate);
    }
    return delegate.equals(obj);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  /**
   * This annotation is used inside other configuration annotations to identify a configuration
   * object.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface Id {
    /**
     * Specifies the persistent identifier of a configuration object.
     *
     * <p><i>Note:</i> Only one of {@link #pid} or {@link #factoryPid} must be specified.
     *
     * @return the PID of a config object
     */
    String pid() default "";

    /**
     * Specifies the factory persistent identifier of a factory configuration object. If at least
     * one configuration object for the specified factory exist, the first one found, unless a
     * filter is specified then the first one that matches the filter, will be referenced.
     *
     * <p><i>Note:</i> Only one of {@link #pid} or {@link #factoryPid} must be specified.
     *
     * @return the factory PID of a factory config object
     */
    String factoryPid() default "";

    /**
     * Specifies an optional filter to use to find the factory configuration object. This filter
     * will be added to the filter created from the factory PID specified with {@link #factoryPid()}
     * when searching for a matching configuration object.
     *
     * @return an optional filter to use to find the factory config object
     */
    String filter() default "";
  }

  /**
   * Defines a meta annotation which enables the developer to create a new configuration annotation
   * which can be used to configure test cases.
   */
  @Target(ElementType.ANNOTATION_TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface Annotation {
    /**
     * Specifies the configuration object to update or create. If at least one matching
     * configuration object exist, the first one found will be updated. If none matches, a new one
     * will be created.
     *
     * @return the config object to update or create
     */
    Id object();

    /**
     * Specifies whether the properties provided by the configuration extension should overwrite the
     * properties defined in any pre-existing configuration object or simply be merged with.
     *
     * <p><i>Note:</i> When merging, entries defined with a <code>null</code> value will result in
     * the removal of the corresponding properties from the updated config object.
     *
     * @return <code>true</code> if the properties provided by the config extension should
     *     correspond to the new config object properties or <code>false</code> if they should be
     *     merged with any existing properties
     */
    boolean overwrite() default true;

    /**
     * Specifies a configuration extension class which will be consulted to provide configurations
     * to be applied for the specified configuration object.
     *
     * @return a configuration extension class
     */
    Class<? extends Configuration.Extension> value();
  }

  /**
   * Extension point for user-defined configuration annotations. This class must defined a public
   * default constructor which will be invoked whenever configuration properties are to be retrieved
   * before a test starts.
   *
   * @param <T> the type of annotation this extension is used with
   */
  public interface Extension<T extends java.lang.annotation.Annotation> {
    /**
     * Called to provide configuration properties to be updated. Any entries returned with a <code>
     * null</code> value will result in the removal of the corresponding property from the
     * corresponding configuration object.
     *
     * <p>Returning <code>null</code> or an empty map when the {@link Annotation} annotation is set
     * to overwrite will result in either the configuration object being deleted if one exist or
     * none created if none exist.
     *
     * @param object the config object for which to provide configuration properties
     * @param annotation the annotation where this extension was specified
     * @return a map of configuration properties to be updated or created for the specified config
     *     object
     */
    public Map<String, Object> configuration(Id object, T annotation);
  }

  /**
   * This annotation can be used to update existing or create new configuration objects in
   * configuration admin using properties defined in resource configuration files.
   *
   * <p>This annotation can be placed either on a test class such that it applies to all test
   * methods or on specific test methods where the configuration objects should be created and/or
   * updated.
   *
   * <p>All updates and removals for a given configuration object provided via annotations will be
   * processed in a single update to the configuration admin service.
   */
  @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface Resources {
    /**
     * Specifies a set of configuration resources representing configuration objects that should be
     * updated and/or created. The resource files are loaded relative to the test class where these
     * annotations appear. The resource files are expected to be in Karaf's <code>.cfg
     * </code> format. The same restrictions as described in {@link Id} are used to identify the
     * configuration object to be updated or created. The PID is specified using the {@link
     * Constants#SERVICE_PID} property. The factory PID is specified using the {@link
     * ConfigurationAdmin#SERVICE_FACTORYPID} property. An optional filter can be specified using
     * the {@link #SERVICE_FACTORY_FILTER} property to find the factory configuration to be updated.
     * An optional boolean flag can be specified using the {@link #SERVICE_OVERWRITE} property to
     * indicate if the properties provided in the resource configuration file should overwrite the
     * properties defined in any pre-existing configuration object or simply be merged with. By
     * default properties are overwritten with those provided in the resource configuration file.
     *
     * <p>Only one of {@link Constants#SERVICE_PID} or {@link ConfigurationAdmin#SERVICE_FACTORYPID}
     * should be included in the configuration file.
     *
     * <p>All 4 properties will be removed from the dictionary before the configuration object is
     * updated.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     *
     * <p>When no additional properties are defined beyond the above ones, either the configuration
     * object will be deleted if one exist or none created if none exist.
     *
     * @return a set of configuration resource names representing configuration objects that should
     *     be updated or created before a test is executed
     */
    String[] value();
  }

  /** This interface is defined purely to provide scoping. */
  public interface Property {
    /** Defines possible cardinalities when set configuration properties. */
    public enum Cardinality {
      /** Used to indicate the property value will be set as an array of the corresponding type. */
      ARRAY,

      /** Used to indicate the property value will be set as a list of the corresponding type. */
      LIST,

      /**
       * Used to indicate the property value will be set as an array of the corresponding type if
       * the annotation contains zero or more than one element; otherwise it will be set as a single
       * value.
       */
      VALUE_OR_ARRAY
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a configuration object property that should first be removed from a
     * configuration object before the test method is invoked. A configuration object will
     * automatically be created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     *
     * <p><i>Note:</i> If only {@link Remove} annotations are defined for a given configuration
     * object and that object doesn't exist then none will be created.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.Remove.class)
    @Documented
    public @interface Remove {
      /**
       * Specifies the configuration object to update. If at least one matching configuration object
       * exist, the first one found will be updated.
       *
       * @return the persistent identifier of the config object to update
       */
      Id object();

      /**
       * The property key to remove in the corresponding configuration object.
       *
       * @return the property key to remove in the corresponding object
       */
      String key();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetString.class)
    @Documented
    public @interface SetString {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      String[] value();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetLong.class)
    @Documented
    public @interface SetLong {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      long[] value();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetInteger.class)
    @Documented
    public @interface SetInteger {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      int[] value();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetShort.class)
    @Documented
    public @interface SetShort {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      short[] value();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetCharacter.class)
    @Documented
    public @interface SetCharacter {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      char[] value();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetByte.class)
    @Documented
    public @interface SetByte {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      byte[] value();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetDouble.class)
    @Documented
    public @interface SetDouble {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      double[] value();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetFloat.class)
    @Documented
    public @interface SetFloat {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      float[] value();
    }

    /**
     * This annotation can be used on the test class to apply to all test methods or on specific
     * test methods to indicate a property that should first be updated or added to a configuration
     * object before the test method is invoked. A configuration object will automatically be
     * created if none exist.
     *
     * <p>All updates and removals for a given configuration object provided via annotations will be
     * processed in a single update to the configuration admin service.
     */
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Repeatable(Properties.SetBoolean.class)
    @Documented
    public @interface SetBoolean {
      /**
       * Specifies the configuration object to update or create. If at least one matching
       * configuration object exist, the first one found will be updated. If none matches, a new one
       * will be created.
       *
       * @return the config object to update or create
       */
      Id object();

      /**
       * The property key to update or add in the corresponding configuration object.
       *
       * @return the property key to update or add in the corresponding object
       */
      String key();

      /**
       * Specifies the cardinality for the property value.
       *
       * @return the cardinality for the property value
       */
      Cardinality cardinality() default Cardinality.VALUE_OR_ARRAY;

      /**
       * The property value(s) to update or add in the corresponding object.
       *
       * @return the property value(s) to update or add in the corresponding object
       */
      boolean[] value();
    }
  }

  /**
   * This annotation can be used to identify a method that will be invoked before the test methods
   * in order to update an existing or create a new configuration object in configuration admin.
   *
   * <p>The annotated method must be defined to return <code>Map&lt;String, Object&gt;</code>.
   *
   * <p>Returning <code>null</code> or an empty map when the annotation is set to overwrite will
   * result in either the configuration object being deleted if one exist or none created if none
   * exist.
   */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface Before {
    /**
     * Specifies the configuration object to update or create. If at least one matching
     * configuration object exist, the first one found will be updated. If none matches, a new one
     * will be created.
     *
     * @return the config object to update or create
     */
    Id object();

    /**
     * Specifies whether the properties provided by the annotated method should overwrite the
     * properties defined in any pre-existing configuration object or simply be merged with.
     *
     * <p><i>Note:</i> When merging, entries defined with a <code>null</code> value will result in
     * the removal of the corresponding properties from the updated config object.
     *
     * @return <code>true</code> if the properties provided by the annotated method should
     *     correspond to the new config object properties or <code>false</code> if they should be
     *     merged with any existing properties
     */
    boolean overwrite() default true;

    /**
     * Specifies the names of the only test methods for which to invoke the annotated method. If
     * left blank or omitted then the annotated method is called for all test methods.
     *
     * @return the names of optional test methods for which to call the annotated method
     */
    String[] method() default {};
  }

  /** This interface is defined purely to provide scoping. */
  public interface Properties {
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.Remove} annotations. */
    public @interface Remove {
      /**
       * List of all {@link Property.Remove} annotations.
       *
       * @return the list of all {@link Property.Remove} annotations
       */
      Property.Remove[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetString} annotations. */
    public @interface SetString {
      /**
       * List of all {@link Property.SetString} annotations.
       *
       * @return the list of all {@link Property.SetString} annotations
       */
      Property.SetString[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetLong} annotations. */
    public @interface SetLong {
      /**
       * List of all {@link Property.SetLong} annotations.
       *
       * @return the list of all {@link Property.SetLong} annotations
       */
      Property.SetLong[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetInteger} annotations. */
    public @interface SetInteger {
      /**
       * List of all {@link Property.SetInteger} annotations.
       *
       * @return the list of all {@link Property.SetInteger} annotations
       */
      Property.SetInteger[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetShort} annotations. */
    public @interface SetShort {
      /**
       * List of all {@link Property.SetShort} annotations.
       *
       * @return the list of all {@link Property.SetShort} annotations
       */
      Property.SetShort[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetCharacter} annotations. */
    public @interface SetCharacter {
      /**
       * List of all {@link Property.SetCharacter} annotations.
       *
       * @return the list of all {@link Property.SetCharacter} annotations
       */
      Property.SetCharacter[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetByte} annotations. */
    public @interface SetByte {
      /**
       * List of all {@link Property.SetByte} annotations.
       *
       * @return the list of all {@link Property.SetByte} annotations
       */
      Property.SetByte[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetDouble} annotations. */
    public @interface SetDouble {
      /**
       * List of all {@link Property.SetDouble} annotations.
       *
       * @return the list of all {@link Property.SetDouble} annotations
       */
      Property.SetDouble[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetFloat} annotations. */
    public @interface SetFloat {
      /**
       * List of all {@link Property.SetFloat} annotations.
       *
       * @return the list of all {@link Property.SetFloat} annotations
       */
      Property.SetFloat[] value();
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Property.SetBoolean} annotations. */
    public @interface SetBoolean {
      /**
       * List of all {@link Property.SetBoolean} annotations.
       *
       * @return the list of all {@link Property.SetBoolean} annotations
       */
      Property.SetBoolean[] value();
    }
  }
}
