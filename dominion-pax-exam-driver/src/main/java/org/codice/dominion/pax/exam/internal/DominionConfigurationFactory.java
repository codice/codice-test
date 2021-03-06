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
package org.codice.dominion.pax.exam.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.codice.dominion.Dominion;
import org.codice.dominion.DominionException;
import org.codice.dominion.interpolate.ContainerNotStagedException;
import org.codice.dominion.interpolate.InterpolationException;
import org.codice.dominion.options.Option;
import org.codice.dominion.options.OptionException;
import org.codice.dominion.options.karaf.KarafOptions;
import org.codice.dominion.pax.exam.internal.processors.KarafDistributionConfigurationFilePostOptionProcessor;
import org.codice.dominion.pax.exam.internal.processors.KarafSshCommandOptionProcessor;
import org.codice.dominion.pax.exam.options.PaxExamOption;
import org.codice.maven.MavenUrl;
import org.codice.test.commons.ReflectionUtils.AnnotationEntry;
import org.ops4j.pax.exam.ConfigurationFactory;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationSecurityOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionKitConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionKitConfigurationOption.Platform;
import org.ops4j.pax.exam.options.CompositeOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of PaxExam {@link ConfigurationFactory} capable of extracting all {@link
 * Option.Annotation} meta-annotations in order to configure the container.
 */
@KarafOptions.InstallFeature(
  repository =
      @MavenUrl(
        groupId = MavenUrl.AS_PROJECT,
        artifactId = "dominion-pax-exam-feature",
        version = MavenUrl.AS_PROJECT,
        type = "xml",
        classifier = "features"
      ),
  name = "dominion-pax-exam"
)
public class DominionConfigurationFactory implements ConfigurationFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionConfigurationFactory.class);

  // thread locals are the only way I can get the required information passed into this factory
  // from inside PaxExam driver and back
  private static final ThreadLocal<Object> THREAD_LOCAL_TEST_INSTANCE = new ThreadLocal<>();
  private static final ThreadLocal<PaxExamDriverInterpolator> THREAD_LOCAL_INTERPOLATOR =
      new ThreadLocal<>();
  private static final ThreadLocal<DominionConfigurationFactory> THREAD_LOCAL_CONFIG =
      new ThreadLocal<>();

  private static final String DISTRO = System.getProperty(Dominion.DISTRIBUTION_PROPERTY);

  private final PaxExamDriverInterpolator interpolator;

  private final Object testInstance;

  private final Class<?> testClass;

  private final List<? extends org.ops4j.pax.exam.Option> coreOptions;

  @Nullable private volatile AnnotationOptions options = null;

  public DominionConfigurationFactory() {
    this.testInstance = DominionConfigurationFactory.THREAD_LOCAL_TEST_INSTANCE.get();
    this.interpolator = DominionConfigurationFactory.THREAD_LOCAL_INTERPOLATOR.get();
    this.testClass = testInstance.getClass();
    LOGGER.debug("DominionConfigurationFactory({}, {})", testClass.getName(), testInstance);
    this.coreOptions =
        AnnotationUtils.annotationsByType(
                interpolator, DominionConfigurationFactory.class, Option.Annotation.class)
            .map(ExtensionOption::new)
            .flatMap(ExtensionOption::extensions)
            .map(ExtensionOption::getOptions)
            .flatMap(Stream::of)
            .collect(Collectors.toList());
  }

  @SuppressWarnings("squid:CommentedOutCodeLine" /* no code commented here */)
  @Override
  public org.ops4j.pax.exam.Option[] createConfiguration() {
    LOGGER.debug("{}::createConfiguration()", this);
    // save our instance back to the thread local to give a chance to the probe runner to retrieve
    // it after staging and continue extracting info from it
    DominionConfigurationFactory.setConfigInfo(this);
    // to delay expansion of options until PaxExam searches for them, we are forced to wrap our
    // logic inside a CompositeOption. In addition each extension will also be managed by its own
    // CompositeOption such that if we cannot interpolate everything (e.g. reference to {karaf.home}
    // before the container is staged) we will be able to re-attempt later once the container is
    // finally staged since PaxEam will always expands all composite options before searching
    //
    // conditions are also applied right away such that any failures while interpolating them will
    // be reported right away and abort the whole thing
    final Set<Class<?>> classes = new HashSet<>(8);

    for (final Iterator<Option.System> i = ServiceLoader.load(Option.System.class).iterator();
        i.hasNext(); ) {
      classes.add(i.next().getClass()); // we don't care about the actual object
    }
    classes.add(testClass);
    final AnnotationOptions opts =
        new AnnotationOptions(
            classes
                .stream()
                .flatMap(
                    c ->
                        AnnotationUtils.annotationsByType(
                            interpolator, c, Option.Annotation.class)));

    LOGGER.debug("{}::createConfiguration() - options = {}", this, opts);
    final KarafDistributionBaseConfigurationOption distro = opts.getDistribution();

    LOGGER.debug("{}::createConfiguration() - karaf distribution = {}", this, distro);
    interpolator.setDistribution(distro);
    this.options = opts;
    return new org.ops4j.pax.exam.Option[] {opts};
  }

  /**
   * Gets the interpolator associated with this configuration factory.
   *
   * @return the interpolator associated with this configuration factory
   */
  public PaxExamDriverInterpolator getInterpolator() {
    return interpolator;
  }

  @Override
  public String toString() {
    return "DominionConfigurationFactory["
        + testClass.getName()
        + '@'
        + Integer.toHexString(System.identityHashCode(testInstance))
        + ']';
  }

  // called when we are just about to start the container and all other options have been processed
  // by PaxExam
  void processPreStartOptions() {
    LOGGER.debug("{}::processPreStartOptions()", this);
    try {
      new KarafDistributionConfigurationFilePostOptionProcessor(options).process();
    } catch (DominionException e) {
      throw e;
    } catch (Exception e) {
      throw new DominionException("Problem starting container", e);
    }
  }

  // called when the container was started and the probe has registered back to the driver
  // note that it doesn't mean the startup script was completely process
  void processPostStartOptions() {
    LOGGER.debug("{}::processPostStartOptions()", this);
    try {
      new KarafSshCommandOptionProcessor(options).process();
    } catch (DominionException e) {
      throw e;
    } catch (Exception e) {
      throw new DominionException("Problem starting container", e);
    }
  }

  /**
   * Wraps all annotations defining options into one single composite option for a given container.
   */
  public class AnnotationOptions implements CompositeOption {
    private final List<ExtensionOption> options;
    private final KarafDistributionBaseConfigurationOption distribution;

    AnnotationOptions(Stream<AnnotationEntry<Option.Annotation>> annotations) {
      this.options =
          annotations
              .map(ExtensionOption::new)
              .flatMap(ExtensionOption::extensions)
              .collect(Collectors.toList());
      final KarafDistributionBaseConfigurationOption[] distros =
          options
              .stream()
              .map(ExtensionOption::getOptions)
              .flatMap(Stream::of)
              .filter(KarafDistributionBaseConfigurationOption.class::isInstance)
              .map(KarafDistributionBaseConfigurationOption.class::cast)
              .filter(this::isForSystemOS)
              .toArray(KarafDistributionBaseConfigurationOption[]::new);

      if (distros.length > 1) { // we don't support more than one yet
        throw new OptionException(
            "too many Karaf distributions configured for the same OS; only one is currently supported");
      } else if (distros.length == 0) {
        if (DominionConfigurationFactory.DISTRO == null) {
          throw new OptionException(
              "no configured Karaf distributions; annotate "
                  + testClass.getName()
                  + " with @"
                  + KarafOptions.DistributionConfiguration.class.getSimpleName());
        }
        throw new OptionException(
            "no configured Karaf '"
                + DominionConfigurationFactory.DISTRO
                + "' distributions; annotate "
                + testClass.getName()
                + " with @"
                + KarafOptions.DistributionConfiguration.class.getSimpleName());
      }
      final String name = distros[0].getName();

      if (StringUtils.isNotEmpty(name)) {
        LOGGER.info(
            "Setting up '{}' distribution for '{}' container", name, interpolator.getContainer());
      } else {
        LOGGER.info("Setting up distribution for '{}' container", interpolator.getContainer());
      }
      this.distribution = distros[0];
    }

    /**
     * Gets the distribution option that was configured for the current container and OS.
     *
     * @return the distribution option that was configured for the current container and OS
     */
    public KarafDistributionBaseConfigurationOption getDistribution() {
      return distribution;
    }

    /**
     * Gets the interpolator associated with this set of options.
     *
     * @return the interpolator associated with this set of options
     */
    public PaxExamDriverInterpolator getInterpolator() {
      return interpolator;
    }

    /**
     * Gets all configured options for the corresponding container.
     *
     * @return a stream of all configured options
     * @throws IllegalStateException if called before options have been created by PaxExam
     */
    public Stream<org.ops4j.pax.exam.Option> options() {
      return Stream.of(
              options.stream().map(ExtensionOption::getOptions).flatMap(Stream::of),
              Stream.of(interpolator.getOptions()),
              // make sure we add the core options after all other options that are specified by the
              // test class
              coreOptions.stream(),
              // the pre-hook options should be last
              preStartHookOptions())
          .flatMap(Function.identity());
    }

    /**
     * Gets all configured options of a given type for the corresponding container.
     *
     * @param <T> the type of options to retrieve
     * @param optionType the type of options to retrieve
     * @return a stream of all configured options of the specified type
     * @throws IllegalStateException if called before options have been created by PaxExam
     */
    public <T extends org.ops4j.pax.exam.Option> Stream<T> options(Class<T> optionType) {
      return options().filter(optionType::isInstance).map(optionType::cast);
    }

    /**
     * Gets all configured options of the given types for the corresponding container.
     *
     * @param optionTypes the types of options to retrieve
     * @return a stream of all configured options of the specified types
     * @throws IllegalStateException if called before options have been created by PaxExam
     */
    public Stream<org.ops4j.pax.exam.Option> options(
        Class<? extends org.ops4j.pax.exam.Option>... optionTypes) {
      return options().filter(o -> DominionConfigurationFactory.isInstance(optionTypes, o));
    }

    /**
     * Gets a single configured option of a given type for the corresponding container.
     *
     * @param <T> the type of option to retrieve
     * @param optionType the type of option to retrieve
     * @return a configured option of the specified type or empty if none configured
     * @throws IllegalStateException if called before options have been created by PaxExam
     */
    public <T extends org.ops4j.pax.exam.Option> Optional<T> getOption(Class<T> optionType) {
      return options(optionType).findFirst();
    }

    @Override
    public org.ops4j.pax.exam.Option[] getOptions() {
      return options().toArray(org.ops4j.pax.exam.Option[]::new);
    }

    @Override
    public String toString() {
      return "AnnotationOptions" + options;
    }

    private Stream<org.ops4j.pax.exam.Option> preStartHookOptions() {
      return Stream.of(
          // this option is only used to be called back just before the container is started
          // without affecting the security option itself
          new KarafDistributionConfigurationSecurityOption(null) {
            @Override
            @SuppressWarnings("squid:S2447" /* per PaxExam API definition for this class */)
            public Boolean getEnableKarafMBeanServerBuilder() {
              processPreStartOptions();
              return null; // make sure we don't affect the end result
            }
          });
    }

    private boolean isForSystemOS(KarafDistributionBaseConfigurationOption option) {
      if (option instanceof KarafDistributionKitConfigurationOption) {
        final KarafDistributionKitConfigurationOption kit =
            (KarafDistributionKitConfigurationOption) option;

        if (SystemUtils.IS_OS_WINDOWS) {
          return Platform.WINDOWS.equals(kit.getPlatform());
        }
        return !Platform.WINDOWS.equals(kit.getPlatform());
      }
      return true;
    }
  }

  /**
   * Corresponds to extensions and its annotation which allows for re-interpolation on subsequent
   * attempts to retrieve the extensions' options if an interpolation failure occurred due to
   * PaxExam not having staged the container yet occurred in the previous calls.
   */
  private class ExtensionOption implements CompositeOption {
    private final String debugId = Integer.toHexString(System.identityHashCode(this));
    private final AnnotationEntry<Option.Annotation> entry;
    private final AnnotationEntry<?> enclosingEntry;
    private final Annotation enclosingAnnotation;
    private final Annotation interpolatedEnclosingAnnotation;
    private final List<PaxExamOption.Extension<Annotation>> extensions;
    @Nullable private volatile List<org.ops4j.pax.exam.Option> options = null;
    @Nullable private volatile String optionsToString = null;

    ExtensionOption(AnnotationEntry<Option.Annotation> entry) {
      this(entry, interpolator);
    }

    private ExtensionOption(
        AnnotationEntry<Option.Annotation> entry, PaxExamDriverInterpolator interpolator) {
      this.entry = entry;
      // getEnclosingAnnotation() cannot be null since the Option.Annotation can only be added to
      // other annotations
      this.enclosingEntry = entry.getEnclosingAnnotation();
      this.enclosingAnnotation = enclosingEntry.getAnnotation();
      this.interpolatedEnclosingAnnotation = interpolator.interpolate(enclosingAnnotation);
      this.extensions =
          (List<PaxExamOption.Extension<Annotation>>)
              (List) Option.getExtensions(PaxExamOption.Extension.class, enclosingAnnotation);
    }

    /**
     * Interpolate and retrieve the options associated with this extension.
     *
     * @return an array of all options defined by this extension or an array containing this
     *     extension if currently unable to interpolate the associated annotation
     * @throws OptionException if a failure occurs while retrieving options
     */
    @Override
    public org.ops4j.pax.exam.Option[] getOptions() {
      List<org.ops4j.pax.exam.Option> opts = this.options;

      if (opts == null) {
        if (extensions.isEmpty()) {
          LOGGER.debug(
              "{}::ExtensionOption@{}::options() - ignoring option {} as no extension implementation could be located",
              DominionConfigurationFactory.this,
              debugId,
              enclosingAnnotation);
          opts = Collections.emptyList();
        } else {
          opts = computeOptionsFromExtension();
        }
      }
      // if we haven't been able to interpolate annotations yet, then return ourselves as the only
      // option. This will allow the next time there is a retrieval/expansion done by PaxExam to
      // trigger another attempt at interpolation
      return (opts != null)
          ? opts.toArray(new org.ops4j.pax.exam.Option[opts.size()])
          : new org.ops4j.pax.exam.Option[] {this};
    }

    @Override
    public String toString() {
      return "ExtensionOption@"
          + debugId
          + '['
          + enclosingAnnotation
          + ": "
          + optionsToString
          + ']';
    }

    /**
     * Gets all extension options that are found on the associated extension classes including this
     * one.
     *
     * @return a stream of all extension options related to this one and including this one
     */
    Stream<ExtensionOption> extensions() {
      final PaxExamDriverInterpolator annotationInterpolator =
          new AnnotationBasedPaxExamDriverInterpolator(
              interpolatedEnclosingAnnotation, interpolator);

      return Stream.concat(
          extensions
              .stream()
              .flatMap(
                  extension ->
                      AnnotationUtils.annotationsByType(
                              interpolator, extension.getClass(), Option.Annotation.class)
                          .map(e -> new ExtensionOption(e, annotationInterpolator))
                          .flatMap(ExtensionOption::extensions)),
          Stream.of(this));
    }

    @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
    private List<org.ops4j.pax.exam.Option> computeOptionsFromExtension() {
      final AnnotationResourceLoader entryLoader = new AnnotationResourceLoader(entry);
      final List<org.ops4j.pax.exam.Option> opts = new ArrayList<>();

      for (final PaxExamOption.Extension<Annotation> extension : extensions) {
        try {
          expandAndFilterAndEnhanceDistributionOptions(
                  extension.options(interpolatedEnclosingAnnotation, interpolator, entryLoader))
              .forEach(opts::add);
        } catch (VirtualMachineError e) {
          throw e;
        } catch (ContainerNotStagedException e) {
          // bail out with ourselves and wait for the next retrieval/expansion to try again
          LOGGER.debug(
              "{}::ExtensionOption@{}::options() - karaf is not staged; annotation = {}",
              DominionConfigurationFactory.this,
              debugId,
              enclosingAnnotation);
        } catch (InterpolationException e) {
          LOGGER.debug(
              "{}::ExtensionOption@{}::options() - failed to interpolate annotation; annotation = {}",
              DominionConfigurationFactory.this,
              debugId,
              enclosingAnnotation,
              e);
          throw e;
        } catch (Throwable t) {
          LOGGER.debug(
              "{}::ExtensionOption@{}::options() - failed to retrieved options; annotation = {}",
              DominionConfigurationFactory.this,
              debugId,
              enclosingAnnotation,
              t);
          throw new OptionException(
              "failed to retrieve options from extension '"
                  + extension.getClass().getName()
                  + "' for "
                  + testClass.getName()
                  + " and "
                  + enclosingAnnotation,
              t);
        }
      }
      this.optionsToString = DominionConfigurationFactory.toString(opts.stream());
      this.options = opts;
      LOGGER.debug(
          "{}::ExtensionOption@{}::options() - {}",
          DominionConfigurationFactory.this,
          debugId,
          this);
      return opts;
    }

    private Stream<org.ops4j.pax.exam.Option> expandAndFilterAndEnhanceDistributionOptions(
        @Nullable org.ops4j.pax.exam.Option[] options) {
      if (options == null) {
        return Stream.empty();
      }
      return Stream.of(options)
          .filter(Objects::nonNull)
          .flatMap(DominionConfigurationFactory::expand)
          .filter(this::filterDistributionOption)
          .map(this::enhanceDistributionOption);
    }

    private boolean filterDistributionOption(org.ops4j.pax.exam.Option option) {
      // accept all distribution options if the property was not defined or is blank
      if ((StringUtils.isEmpty(DominionConfigurationFactory.DISTRO))
          || !(option instanceof KarafDistributionBaseConfigurationOption)) {
        return true;
      }
      final String name = ((KarafDistributionBaseConfigurationOption) option).getName();

      // keep all distribution options if name was not specified; otherwise they mush match the
      // property
      return (name == null) || DominionConfigurationFactory.DISTRO.equals(name);
    }

    /**
     * Registers a hook with PaxExam by extending the distribution options to inject the container
     * id and the container name to the unpack directory and to monitor the point where the
     * container was laid down and is about to be started.
     *
     * @param option the option to enhance by registering a hook (a.k.a. extending them with our
     *     own)
     * @return <code>option</code> or a new one if a hook is registered
     */
    private org.ops4j.pax.exam.Option enhanceDistributionOption(org.ops4j.pax.exam.Option option) {
      if (option instanceof KarafDistributionKitConfigurationOption) {
        return new DominionKarafDistributionKitConfigurationOption(
            interpolator, (KarafDistributionKitConfigurationOption) option);
      } else if (option instanceof KarafDistributionConfigurationOption) {
        return new DominionKarafDistributionConfigurationOption(
            interpolator, (KarafDistributionConfigurationOption) option);
      } else if (option instanceof KarafDistributionBaseConfigurationOption) {
        return new DominionKarafDistributionBaseConfigurationOption(
            interpolator, (KarafDistributionBaseConfigurationOption) option);
      }
      return option;
    }
  }

  static void setTestInfo(PaxExamDriverInterpolator interpolator, Object testInstance) {
    DominionConfigurationFactory.THREAD_LOCAL_INTERPOLATOR.set(interpolator);
    DominionConfigurationFactory.THREAD_LOCAL_TEST_INSTANCE.set(testInstance);
  }

  static void setConfigInfo(DominionConfigurationFactory factory) {
    DominionConfigurationFactory.THREAD_LOCAL_CONFIG.set(factory);
  }

  static DominionConfigurationFactory getConfigInfo() {
    return DominionConfigurationFactory.THREAD_LOCAL_CONFIG.get();
  }

  static void clearTestInfo() {
    DominionConfigurationFactory.THREAD_LOCAL_INTERPOLATOR.remove();
    DominionConfigurationFactory.THREAD_LOCAL_TEST_INSTANCE.remove();
    DominionConfigurationFactory.THREAD_LOCAL_CONFIG.remove();
  }

  static String toString(Stream<? extends org.ops4j.pax.exam.Option> options) {
    return options
        .map(DominionConfigurationFactory::toString)
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private static Stream<org.ops4j.pax.exam.Option> expand(org.ops4j.pax.exam.Option option) {
    return (option instanceof CompositeOption)
        ? Stream.of(((CompositeOption) option).getOptions())
            .flatMap(DominionConfigurationFactory::expand)
        : Stream.of(option);
  }

  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  private static String toString(org.ops4j.pax.exam.Option option) {
    try {
      // first check if a <code>toString()</code> method is defined for the option
      final Method method = option.getClass().getMethod("toString");

      if (!Object.class.equals(method.getDeclaringClass())) { // skip the default one
        return (String) method.invoke(option);
      }
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) { // ignore and use reflection instead
    }
    try {
      return ReflectionToStringBuilder.toString(option, null, true);
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) { // ignore and fallback to default
    }
    return option.toString();
  }

  @SuppressWarnings("squid:S3398" /* cannot defined static methods inside inner classes */)
  private static boolean isInstance(
      Class<? extends org.ops4j.pax.exam.Option>[] optionTypes, org.ops4j.pax.exam.Option option) {
    return Stream.of(optionTypes).anyMatch(c -> c.isInstance(option));
  }
}
