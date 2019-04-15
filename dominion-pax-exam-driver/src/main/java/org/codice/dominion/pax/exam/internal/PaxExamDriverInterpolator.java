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

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.codice.dominion.interpolate.ContainerNotStagedException;
import org.codice.dominion.interpolate.InterpolationException;
import org.codice.dominion.interpolate.Interpolator;
import org.codice.dominion.options.OptionException;
import org.codice.dominion.options.Options;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.PaxExamOption;
import org.codice.maven.Utilities;
import org.codice.test.commons.ReflectionUtils.AnnotationEntry;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.ops4j.pax.exam.options.SystemPropertyOption;
import org.ops4j.pax.url.mvn.ServiceConstants;
import org.ops4j.pax.url.mvn.internal.config.MavenConfigurationImpl;
import org.ops4j.pax.url.mvn.internal.config.MavenRepositoryURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shaded.org.apache.maven.settings.Profile;
import shaded.org.apache.maven.settings.Settings;
import shaded.org.ops4j.util.property.DictionaryPropertyResolver;
import shaded.org.ops4j.util.property.PropertiesPropertyResolver;

/**
 * Pax Exam interpolation service specific to the driver. The implementation here will attempt to
 * locate Karaf's home directory once it knows where the kernel will be unpacked.
 */
public class PaxExamDriverInterpolator extends PaxExamInterpolator {
  private static final Logger LOGGER = LoggerFactory.getLogger(PaxExamDriverInterpolator.class);

  private static final String UNABLE_TO_DETERMINE_EXAM_DIR_ERROR =
      "Unable to determine current exam directory";

  private volatile KarafDistributionBaseConfigurationOption distribution = null;

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param testClass the current test class
   * @param id a unique id for this the corresponding test run
   */
  public PaxExamDriverInterpolator(Class<?> testClass, String id) {
    this(testClass, id, PaxExamDriverInterpolator.DEFAULT_CONTAINER);
  }

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param testClass the current test class
   * @param id a unique id for this the corresponding test run
   * @param container the name of the container for which to create an interpolator
   */
  public PaxExamDriverInterpolator(Class<?> testClass, String id, String container) {
    super(testClass, id, container);
    LOGGER.debug("PaxExamDriverInterpolator({}, {}, {})", testClass, id, container);
    initMaven(); // only do this in the driver
  }

  /**
   * Initializes a new interpolator with all information from the provided interpolator.
   *
   * @param interpolator the interpolator to proxy
   */
  protected PaxExamDriverInterpolator(PaxExamDriverInterpolator interpolator) {
    super(interpolator);
    LOGGER.debug("PaxExamDriverInterpolator({})", interpolator);
  }

  @Override
  @Nullable
  public String lookup(String key) {
    String value = super.lookup(key);

    if ((value == null) && key.startsWith("karaf.")) {
      initKaraf();
      return replacements.get(key);
    }
    return value;
  }

  /**
   * Gets PaxExam options to configure the interpolator inside the container.
   *
   * @return PaxExam options to configure the interpolator inside the container
   */
  public Option[] getOptions() {
    return new Option[] {
      // create a subclass of the system property option to delay the evaluation of the replacement
      // map in order to collect all registered values
      new SystemPropertyOption(Interpolator.REPLACEMENTS_KEY) {
        @Override
        public String getValue() {
          initKaraf(); // make sure {karaf.XXXX} were initialized
          return getReplacementsInfo();
        }
      },
      // create a subclass of the system property option to delay the evaluation of the system
      // port information to the very last minute. This will allow us to catch all port allocated
      // via interpolation of all annotations as these would be resolved and evaluated before
      // PaxExam gets this information
      new SystemPropertyOption(Interpolator.PORTS_KEY) {
        @Override
        public String getValue() {
          return getPortsInfo();
        }
      }
    };
  }

  @Override
  public Path getKarafHome() {
    initKaraf();
    return super.getKarafHome();
  }

  /**
   * This method should be called as soon as the Karaf distribution configuration has been
   * discovered by the PaxExam driver.
   *
   * @param distro the Karaf distribution configuration which will be used to stage the container
   */
  void setDistribution(KarafDistributionBaseConfigurationOption distro) {
    if (distribution == null) {
      this.distribution = distro;
      LOGGER.info(
          "Target folder for containers: {}",
          distro.getUnpackDirectory().toPath().toAbsolutePath());
    }
  }

  private FileTime creationTime(Path path) {
    try {
      return Files.readAttributes(path, BasicFileAttributes.class).creationTime();
    } catch (IOException e) {
      throw new InterpolationException(
          PaxExamDriverInterpolator.UNABLE_TO_DETERMINE_EXAM_DIR_ERROR, e);
    }
  }

  private void initKaraf() {
    if (karafHome == null) {
      final KarafDistributionBaseConfigurationOption distro = distribution;

      if (distro == null) {
        throw new ContainerNotStagedException(
            PaxExamDriverInterpolator.UNABLE_TO_DETERMINE_EXAM_DIR_ERROR
                + "; karaf configuration is unknown");
      }
      final File targetFolder = retrieveFinalTargetFolder(distro);
      final Path path = searchKarafBase(targetFolder).toPath();
      final String home = initPath("karaf.home", path);
      final Path bin = path.resolve("bin"); // bin location cannot be customized
      final Path data = path.resolve(distro.getKarafData());
      final Path etc = path.resolve(distro.getKarafEtc());

      LOGGER.info("Target folder for '{}' container: {}", container, targetFolder);
      LOGGER.info("{karaf.home} for '{}' container: {}", container, home);
      initPath("karaf.bin", bin); // bin location cannot be customized
      initPath("karaf.data", data);
      initPath("karaf.etc", etc);
      super.karafHome = path;
      super.karafBin = bin;
      super.karafData = data;
      super.karafEtc = etc;
    }
  }

  // see org.ops4j.pax.exam.karaf.container.internal.KarafTestContainer.retrieveFinalTargetFolder()
  private File retrieveFinalTargetFolder(KarafDistributionBaseConfigurationOption distro) {
    final File unpackDir = distro.getUnpackDirectory();

    if (unpackDir == null) {
      throw new InterpolationException("no Karaf unpack directory defined");
    }
    final Path root = unpackDir.toPath().toAbsolutePath();

    // we try to find the latest folder inside the unpack directory
    // however, with the ExtensionOption.enhanceDistributionOption() logic now, there will never
    // be more than one directory in there
    try (final Stream<Path> dirContents = Files.list(root)) {
      return dirContents
          .max(Comparator.comparing(this::creationTime)) // to get the latest one
          .orElseThrow(
              () ->
                  new ContainerNotStagedException(
                      PaxExamDriverInterpolator.UNABLE_TO_DETERMINE_EXAM_DIR_ERROR
                          + "; no target folder found in: "
                          + root))
          .toFile();
    } catch (IOException e) {
      throw new ContainerNotStagedException(
          PaxExamDriverInterpolator.UNABLE_TO_DETERMINE_EXAM_DIR_ERROR, e);
    }
  }

  // see org.ops4j.pax.exam.karaf.container.internal.KarafTestContainer.searchKarafBase()
  // Since we might get quite deep use a simple breath first search algorithm
  @SuppressWarnings("squid:S3776" /* copied from PaxExam */)
  private File searchKarafBase(File target) {
    final Queue<File> searchNext = new LinkedList<>();

    searchNext.add(target);
    while (!searchNext.isEmpty()) {
      final File head = searchNext.poll();

      if (!head.isDirectory()) {
        continue;
      }
      boolean isSystem = false;
      boolean etc = false;

      for (final File file : head.listFiles()) {
        if (file.isDirectory()) {
          final String name = file.getName();

          if (name.equals("system")) {
            isSystem = true;
            if (etc) {
              return head;
            }
          } else if (name.equals("etc")) {
            etc = true;
            if (isSystem) {
              return head;
            }
          }
        }
      }
      searchNext.addAll(Arrays.asList(head.listFiles()));
    }
    throw new ContainerNotStagedException(
        PaxExamDriverInterpolator.UNABLE_TO_DETERMINE_EXAM_DIR_ERROR
            + "; no karaf home found in extracted distribution");
  }

  private String initPath(String key, Path path) {
    final String value = path.toString().replace('\\', '/').replace("/bin/..", "/");

    replacements.put(key, value);
    return value;
  }

  private void initMaven() {
    initMavenRepositories(initMavenSettings());
    // finally, initialize the mvn protocol handler
    Utilities.initMavenUrlHandler();
  }

  private MavenConfigurationImpl initMavenSettings() {
    // check if we have any OverrideAndPropagateMavenSettings annotations on the test
    // class as this would be a source of customization for the maven settings for Aether
    final String[] settingsLocations =
        annotationsByType(Options.OverrideAndPropagateMavenSettings.class)
            .map(AnnotationEntry::getAnnotation)
            .map(this::interpolate)
            .map(Options.OverrideAndPropagateMavenSettings::value)
            .filter(StringUtils::isNotEmpty)
            .distinct()
            .map(FilenameUtils::separatorsToSystem)
            .toArray(String[]::new);

    if (settingsLocations.length > 1) {
      throw new OptionException(
          "too many overridden Maven settings configured; only one is supported");
    } else if (settingsLocations.length == 1) {
      LOGGER.info("Overridden Maven settings: {}", settingsLocations[0]);
      System.setProperty(
          ServiceConstants.PID + '.' + ServiceConstants.PROPERTY_SETTINGS_FILE,
          settingsLocations[0]);
    }
    // let's extract Maven settings' properties from all active profiles so we can complement
    // the interpolator's replacements
    final MavenConfigurationImpl config =
        new MavenConfigurationImpl(
            new DictionaryPropertyResolver(
                null, new PropertiesPropertyResolver(System.getProperties())),
            ServiceConstants.PID);
    final Settings settings = config.getSettings();
    final URL url = config.getSettingsFileUrl();

    if (url != null) {
      LOGGER.info("Processing Maven settings: {}", url.getPath());
      final Map<String, Profile> profiles = settings.getProfilesAsMap();

      Stream.concat(
              settings.getActiveProfiles().stream().map(profiles::get).filter(Objects::nonNull),
              profiles.values().stream().filter(PaxExamDriverInterpolator::isActiveByDefault))
          .map(Profile::getProperties)
          .map(Properties::entrySet)
          .flatMap(Set::stream)
          .forEach(this::addReplacementFromMavenProfiles);
    }
    return config;
  }

  private void initMavenRepositories(MavenConfigurationImpl config) {
    // check if any Maven repositories are configured using the AddMavenRepository and
    // PropagateMavenRepositoriesFromActiveProfiles annotations and pass those onto Aether
    String repositories = "";

    if (annotationsByType(Options.PropagateMavenRepositoriesFromActiveProfiles.class).count() > 0) {
      repositories += '+';
    }
    repositories +=
        annotationsByType(Options.AddMavenRepository.class)
            .map(AnnotationEntry::getAnnotation)
            .map(this::interpolate)
            .map(Options.AddMavenRepository::value)
            .flatMap(Stream::of)
            .filter(PaxExamDriverInterpolator::isRepositoryUrlDefined)
            .distinct()
            .collect(Collectors.joining(","));
    if (!repositories.isEmpty()) {
      System.setProperty(
          ServiceConstants.PID + '.' + ServiceConstants.PROPERTY_REPOSITORIES, repositories);
      if (LOGGER.isInfoEnabled()) {
        try {
          config
              .getRepositories()
              .stream()
              .map(MavenRepositoryURL::toString)
              .distinct()
              .forEach(r -> LOGGER.info("Maven repository: {}", r));
          config
              .getSettings()
              .getMirrors()
              .forEach(m -> LOGGER.info("Maven mirror of \"{}\": {}", m.getMirrorOf(), m.getUrl()));
        } catch (MalformedURLException e) { // ignore
        }
      }
    }
    // check if we have any OverrideAndPropagateMavenLocalRepository annotations on the test
    // class as this would be a source of customization for the local repository for Aether
    final String[] locations =
        annotationsByType(Options.OverrideAndPropagateMavenLocalRepository.class)
            .map(AnnotationEntry::getAnnotation)
            .map(this::interpolate)
            .map(Options.OverrideAndPropagateMavenLocalRepository::value)
            .map(FilenameUtils::separatorsToSystem)
            .filter(StringUtils::isNotEmpty)
            .toArray(String[]::new);

    if (locations.length > 1) {
      throw new OptionException(
          "too many overridden Maven local repositories configured; only one is supported");
    } else if (locations.length == 1) {
      LOGGER.info("Overridden Maven local repository: {}", locations[0]);
      System.setProperty(
          ServiceConstants.PID + '.' + ServiceConstants.PROPERTY_LOCAL_REPOSITORY, locations[0]);
    }
  }

  private void addReplacementFromMavenProfiles(Map.Entry<?, ?> e) {
    final Object key = e.getKey();

    if (key != null) {
      String value = Objects.toString(e.getValue(), null);

      if (value != null) {
        // use a different substitutor here sine we need to support ${} expansions that are
        // supported my Maven settings files
        value = new StringSubstitutor(this).setEnableSubstitutionInVariables(true).replace(value);
      }
      final String k = key.toString();
      final String old = replacements.get(k);

      if (old == null) {
        LOGGER.info("Maven profile property: {}=<{}>", k, value);
        replacements.put(k, value);
      } else if (!old.equals(value)) {
        LOGGER.warn(
            "Maven profile property: {}=<{}> (ignored; already defined as <{}>)", k, value, old);
      }
    }
  }

  private <A extends Annotation> Stream<AnnotationEntry<A>> annotationsByType(Class<A> clazz) {
    // we need to find recursively all those annotations not only on the test class but also on all
    // the extensions classes as well
    // getEnclosingAnnotation() cannot be null since the Option.Annotation can only be added to
    // other annotations
    return Stream.concat(
            AnnotationUtils.annotationsByType(
                    this, testClass, org.codice.dominion.options.Option.Annotation.class)
                .flatMap(this::extensions)
                .map(Object::getClass),
            Stream.of(testClass))
        .flatMap(c -> AnnotationUtils.annotationsByType(this, c, clazz));
  }

  private static boolean isRepositoryUrlDefined(String url) {
    if (StringUtils.isEmpty(url)) {
      return false;
    }
    // ignore the markings portion of the URL
    final int i = url.indexOf('@');

    return (i == -1) || (i != 0);
  }

  private Stream<PaxExamOption.Extension<Annotation>> extensions(
      AnnotationEntry<org.codice.dominion.options.Option.Annotation> entry) {
    final AnnotationEntry<?> enclosingEntry = entry.getEnclosingAnnotation();
    final Annotation enclosingAnnotation = enclosingEntry.getAnnotation();

    return org.codice.dominion.options.Option.getExtensions(
            PaxExamOption.Extension.class, enclosingAnnotation)
        .stream()
        .map(PaxExamOption.Extension.class::cast);
  }

  private static boolean isActiveByDefault(Profile profile) {
    return (profile.getActivation() != null) && profile.getActivation().isActiveByDefault();
  }
}
