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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.codice.dominion.conditions.Condition;
import org.codice.dominion.conditions.ConditionException;
import org.codice.dominion.interpolate.ContainerNotStagedException;
import org.codice.dominion.interpolate.InterpolationException;
import org.codice.dominion.options.Option;
import org.codice.dominion.options.OptionException;
import org.codice.dominion.options.karaf.KarafOptions;
import org.codice.dominion.pax.exam.options.PaxExamOption;
import org.codice.test.commons.ReflectionUtils;
import org.codice.test.commons.ReflectionUtils.AnnotationEntry;
import org.ops4j.pax.exam.ConfigurationFactory;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionKitConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionKitConfigurationOption.Platform;
import org.ops4j.pax.exam.options.CompositeOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of PaxExam {@link ConfigurationFactory} capable of extracting all {@link
 * Option.Annotation} meta-annotations in order to configure the container.
 */
public class DominionConfigurationFactory implements ConfigurationFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionConfigurationFactory.class);

  // thread locals are the only way I can get the required information passed into this factory
  // from inside PaxExam driver
  private static final ThreadLocal<Object> THREAD_LOCAL_TEST_INSTANCE = new ThreadLocal<>();
  private static final ThreadLocal<PaxExamDriverInterpolator> THREAD_LOCAL_INTERPOLATOR =
      new ThreadLocal<>();

  private final PaxExamDriverInterpolator interpolator;

  private final Object testInstance;

  private final Class<?> testClass;

  public DominionConfigurationFactory() {
    this.testInstance = DominionConfigurationFactory.THREAD_LOCAL_TEST_INSTANCE.get();
    this.interpolator = DominionConfigurationFactory.THREAD_LOCAL_INTERPOLATOR.get();
    this.testClass = testInstance.getClass();
    LOGGER.debug("DominionConfigurationFactory({}, {})", testClass.getName(), testInstance);
  }

  @SuppressWarnings("squid:CommentedOutCodeLine" /* no code commented here */)
  @Override
  public org.ops4j.pax.exam.Option[] createConfiguration() {
    LOGGER.debug("{}::createConfiguration()", this);
    // to delay expansion of options until PaxExam searches for them, we are forced to wrap our
    // logic inside a CompositeOption. In addition each extension wil also be managed by its own
    // CompositeOption such that if we cannot interpolate everything (e.g. reference to {karaf.home}
    // before the container is staged) we will be able to re-attempt later once the container is
    // finally staged since PaxEam will always expands all composite options before searching
    //
    // conditions are also applied right away such that any failures while interpolating them will
    // be reported right away and abort the whole thing
    final AnnotationOptions options =
        new AnnotationOptions(
            ReflectionUtils.annotationsByType(
                this::filterConditionAnnotations, testClass, Option.Annotation.class));

    LOGGER.debug("{}::createConfiguration() - options = {}", this, options);
    final KarafDistributionBaseConfigurationOption distro = options.getDistribution();

    LOGGER.debug("{}::createConfiguration() - karaf distro = {}", this, distro);
    interpolator.setDistribution(distro);
    return new org.ops4j.pax.exam.Option[] {options};
  }

  @Override
  public String toString() {
    return "DominionConfigurationFactory["
        + testClass.getName()
        + '@'
        + Integer.toHexString(System.identityHashCode(testInstance))
        + ']';
  }

  private boolean filterConditionAnnotations(AnnotationEntry<?> entry) {
    // check directly contained annotations, one by one for annotations that are only doing
    // conditions. Those are are doing both conditions and options are ignored at this level and
    // left to be evaluated when we get to deepest annotations
    return entry.annotations().allMatch(this::filterConditionAnnotation);
  }

  private boolean filterConditionAnnotation(AnnotationEntry<?> entry) {
    if (entry.isEnclosingAnInstanceOf(Option.Annotation.class)) {
      // if it encloses an option annotation, then we shall not look for recursive annotated
      // conditions as we will let the deepest annotations take care of them
      return true;
    }
    return entry
        .enclosingAnnotationsByType(Condition.Annotation.class)
        .allMatch(this::evaluateConditionAnnotation);
  }

  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  private boolean evaluateConditionAnnotation(AnnotationEntry<Condition.Annotation> entry) {
    // getEnclosingAnnotation() cannot be null since the Option.Annotation can only be added to
    // other annotations
    final AnnotationEntry<?> enclosingEntry = entry.getEnclosingAnnotation();
    final Annotation annotation = enclosingEntry.getAnnotation();

    try {
      final Condition.Extension<Annotation> extension =
          Condition.getExtension(Condition.Extension.class, annotation);

      if (extension == null) {
        LOGGER.debug(
            "{}::evaluateConditionAnnotation - ignoring condition {} as no extension implementation could be located",
            this,
            annotation);
        return true;
      }
      return extension.evaluate(
          interpolator.interpolate(annotation), testClass, new AnnotationResourceLoader(entry));
    } catch (VirtualMachineError | ConditionException e) {
      throw e;
    } catch (Throwable t) {
      throw new ConditionException(
          "failed to evaluate condition from "
              + annotation.annotationType().getSimpleName()
              + " extension for "
              + testClass.getName()
              + " and "
              + annotation,
          t);
    }
  }

  /** Wraps all annotations defining options into one single composite option. */
  private class AnnotationOptions implements CompositeOption {
    private final List<ExtensionOption> options;

    AnnotationOptions(Stream<AnnotationEntry<Option.Annotation>> annotations) {
      this.options =
          annotations
              .map(ExtensionOption::new)
              .flatMap(ExtensionOption::extensions)
              .collect(Collectors.toList());
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

    public KarafDistributionBaseConfigurationOption getDistribution() {
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
        throw new OptionException(
            "no configured Karaf distributions; annotate "
                + testClass.getName()
                + " with @"
                + KarafOptions.DistributionConfiguration.class.getSimpleName());
      }
      return distros[0];
    }

    @Override
    public org.ops4j.pax.exam.Option[] getOptions() {
      return Stream.concat(
              options.stream().map(ExtensionOption::getOptions).flatMap(Stream::of),
              Stream.of(interpolator.getOptions()))
          .toArray(org.ops4j.pax.exam.Option[]::new);
    }

    @Override
    public String toString() {
      return "AnnotationOptions" + options;
    }
  }

  /**
   * Corresponds to an extension and its annotation which allows for re-interpolation on subsequent
   * attempts to retrieved the extension's options if an interpolation failure due to PaxExam not
   * having staged the container yet occurred in the previous calls.
   */
  private class ExtensionOption implements CompositeOption {
    private final String debugId = Integer.toHexString(System.identityHashCode(this));
    private final AnnotationEntry<Option.Annotation> entry;
    private final AnnotationEntry<?> enclosingEntry;
    private final Annotation enclosingAnnotation;
    private final Annotation interpolatedEnclosingAnnotation;
    @Nullable private final PaxExamOption.Extension<Annotation> extension;
    @Nullable private volatile org.ops4j.pax.exam.Option[] options = null;
    @Nullable private volatile String optionsToString = null;

    ExtensionOption(AnnotationEntry<Option.Annotation> entry) {
      this.entry = entry;
      // getEnclosingAnnotation() cannot be null since the Option.Annotation can only be added to
      // other annotations
      this.enclosingEntry = entry.getEnclosingAnnotation();
      this.enclosingAnnotation = enclosingEntry.getAnnotation();
      this.interpolatedEnclosingAnnotation = interpolator.interpolate(enclosingAnnotation);
      this.extension = Option.getExtension(PaxExamOption.Extension.class, enclosingAnnotation);
    }

    /**
     * Interpolate and retrieved the options associated with this extension.
     *
     * @return an array of all options defined by this extension or an array containing this
     *     extension if currently unable to interpolate the associated annotation
     * @throws OptionException if a failure occurs while retrieving options
     */
    @Override
    public org.ops4j.pax.exam.Option[] getOptions() {
      org.ops4j.pax.exam.Option[] opts = this.options;

      if (opts == null) {
        if (extension == null) {
          LOGGER.debug(
              "{}::ExtensionOption@{}::options() - ignoring option {} as no extension implementation could be located",
              DominionConfigurationFactory.this,
              debugId,
              enclosingAnnotation);
          opts = new org.ops4j.pax.exam.Option[] {};
        } else {
          opts = computeOptionsFromExtension();
        }
      }
      // if we haven't been able to interpolate annotations yet, then return ourselves as the only
      // option. This will allow the next time there is a retrieval/expansion done by PaxExam to
      // trigger another attempt at interpolation
      return (opts != null) ? opts : new org.ops4j.pax.exam.Option[] {this};
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
     * Gets all extension options that are found on the associated extension class including this
     * one.
     *
     * @return a stream of all extension options related to this one and including this one
     */
    Stream<ExtensionOption> extensions() {
      if (extension == null) {
        return Stream.of(this);
      }
      return Stream.concat(
          ReflectionUtils.annotationsByType(
                  DominionConfigurationFactory.this::filterConditionAnnotations,
                  extension.getClass(),
                  Option.Annotation.class)
              .map(ExtensionOption::new)
              .flatMap(ExtensionOption::extensions),
          Stream.of(this));
    }

    @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
    private org.ops4j.pax.exam.Option[] computeOptionsFromExtension() {
      org.ops4j.pax.exam.Option[] opts = null;

      try {
        opts =
            expandAndEnhanceDistributionOptions(
                    extension.options(
                        interpolatedEnclosingAnnotation,
                        testClass,
                        new AnnotationResourceLoader(entry)))
                .toArray(org.ops4j.pax.exam.Option[]::new);
        this.optionsToString =
            Stream.of(opts).map(this::toString).collect(Collectors.joining(", ", "[", "]"));
        this.options = opts;
        LOGGER.debug(
            "{}::ExtensionOption@{}::options() - {}",
            DominionConfigurationFactory.this,
            debugId,
            this);
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
      return opts;
    }

    private Stream<org.ops4j.pax.exam.Option> expandAndEnhanceDistributionOptions(
        @Nullable org.ops4j.pax.exam.Option[] options) {
      if (options == null) {
        return Stream.empty();
      }
      return Stream.of(options)
          .filter(Objects::nonNull)
          .flatMap(DominionConfigurationFactory::expand)
          .map(this::enhanceDistributionOption);
    }

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

    @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
    private String toString(org.ops4j.pax.exam.Option option) {
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
  }

  static void setTestInfo(PaxExamDriverInterpolator interpolator, Object testInstance) {
    DominionConfigurationFactory.THREAD_LOCAL_INTERPOLATOR.set(interpolator);
    DominionConfigurationFactory.THREAD_LOCAL_TEST_INSTANCE.set(testInstance);
  }

  static void clearTestInfo() {
    DominionConfigurationFactory.THREAD_LOCAL_INTERPOLATOR.remove();
    DominionConfigurationFactory.THREAD_LOCAL_TEST_INSTANCE.remove();
  }

  private static Stream<org.ops4j.pax.exam.Option> expand(org.ops4j.pax.exam.Option option) {
    return (option instanceof CompositeOption)
        ? Stream.of(((CompositeOption) option).getOptions())
            .flatMap(DominionConfigurationFactory::expand)
        : Stream.of(option);
  }
}
