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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.codice.dominion.interpolate.ContainerNotStagedException;
import org.codice.dominion.interpolate.InterpolationException;
import org.codice.dominion.interpolate.Interpolator;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.ops4j.pax.exam.options.SystemPropertyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pax Exam interpolation service specific to the driver. The implementation here will attempt to
 * locate Karaf's home directory once it knows where the kernel will be unpacked.
 */
public class PaxExamDriverInterpolator extends PaxExamInterpolator {
  private static final Logger LOGGER = LoggerFactory.getLogger(PaxExamDriverInterpolator.class);

  private static final String UNABLE_TO_DETERMINE_EXAM_DIR_ERROR =
      "Unable to determine current exam directory";

  // only used to represent karaf.home in the driver; ignored in the container
  private volatile Path karafHome = null;

  private volatile KarafDistributionBaseConfigurationOption distribution = null;

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param uuid a unique id for this the corresponding test run
   */
  public PaxExamDriverInterpolator(String uuid) {
    this(uuid, PaxExamDriverInterpolator.DEFAULT_CONTAINER);
  }

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param uuid a unique id for this the corresponding test run
   * @param container the name of the container for which to create an interpolator
   */
  public PaxExamDriverInterpolator(String uuid, String container) {
    super(uuid, container);
    LOGGER.debug("PaxExamDriverInterpolator({}, {})", uuid, container);
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

  /**
   * This method should be called as soon as the Karaf distribution configuration has been
   * discovered by the PaxExam driver.
   *
   * @param distro the Karaf distribution configuration which will be used to stage the container
   */
  public void setDistribution(KarafDistributionBaseConfigurationOption distro) {
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

      LOGGER.info("Target folder for '{}' container: {}", container, targetFolder);
      LOGGER.info("{karaf.home} for '{}' container: {}", container, home);
      initPath("karaf.bin", path.resolve("bin")); // bin location cannot be customized
      initPath("karaf.data", path.resolve(distro.getKarafData()));
      initPath("karaf.etc", path.resolve(distro.getKarafEtc()));
      this.karafHome = path;
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
}
