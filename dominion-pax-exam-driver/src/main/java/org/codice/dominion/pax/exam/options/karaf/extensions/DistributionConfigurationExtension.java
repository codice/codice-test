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
package org.codice.dominion.pax.exam.options.karaf.extensions;

import java.io.File;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.codice.dominion.options.Options.MavenUrl;
import org.codice.dominion.options.karaf.KarafOptions.DistributionConfiguration;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.pax.exam.options.Utilities;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionKitConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionKitConfigurationOption.Platform;

/** Extension point for the {@link DistributionConfiguration} option annotation. */
public class DistributionConfigurationExtension implements Extension<DistributionConfiguration> {
  @Override
  public Option[] options(
      DistributionConfiguration annotation,
      PaxExamInterpolator interpolator,
      ResourceLoader resourceLoader) {
    final Platform platform = DistributionConfigurationExtension.getKarafPlatform(annotation);

    if (SystemUtils.IS_OS_WINDOWS) {
      if (platform != Platform.WINDOWS) {
        return new Option[0];
      }
    } else {
      if (platform != Platform.NIX) {
        return new Option[0];
      }
    }
    final MavenUrl mavenUrl = annotation.framework();
    final String url = annotation.frameworkUrl();
    final boolean groupIsDefined =
        org.codice.dominion.options.Utilities.isDefined(mavenUrl.groupId());
    final boolean urlIsDefined = org.codice.dominion.options.Utilities.isDefined(url);

    if (!groupIsDefined && !urlIsDefined) {
      throw new IllegalArgumentException(
          "must specify either DistributionConfiguration.framework() or DistributionConfiguration.frameworkUrl() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName());
    }
    final String name = org.codice.dominion.options.Utilities.resolve(annotation.name(), null);
    final String version =
        org.codice.dominion.options.Utilities.resolve(annotation.version(), null);
    KarafDistributionKitConfigurationOption cfg;

    if (groupIsDefined) {
      if (urlIsDefined) {
        throw new IllegalArgumentException(
            "specify only one of DistributionConfiguration.framework() or DistributionConfiguration.frameworkUrl() in "
                + annotation
                + " for "
                + resourceLoader.getLocationClass().getName());
      }
      cfg =
          new KarafDistributionKitConfigurationOption(
              Utilities.toReference(annotation, mavenUrl, resourceLoader), name, version, platform);
    } else {
      cfg = new KarafDistributionKitConfigurationOption(url, name, version, platform);
    }
    cfg =
        org.codice.dominion.options.Utilities.applyIfDefined(
            annotation.main(), cfg, cfg::karafMain);
    cfg =
        org.codice.dominion.options.Utilities.applyIfDefined(
            FilenameUtils.separatorsToSystem(annotation.data()), cfg, cfg::karafData);
    cfg =
        org.codice.dominion.options.Utilities.applyIfDefined(
            FilenameUtils.separatorsToSystem(annotation.etc()), cfg, cfg::karafEtc);
    cfg =
        org.codice.dominion.options.Utilities.applyIfDefined(
            FilenameUtils.separatorsToSystem(annotation.start()), cfg, cfg::executable);
    cfg =
        cfg.filesToMakeExecutable(
            Stream.of(annotation.executables())
                .map(FilenameUtils::separatorsToSystem)
                .toArray(String[]::new));
    return new Option[] {
      cfg.unpackDirectory(new File(FilenameUtils.separatorsToSystem(annotation.unpack())))
          .useDeployFolder(annotation.deploy())
          .runEmbedded(annotation.embed())
    };
  }

  private static Platform getKarafPlatform(DistributionConfiguration annotation) {
    switch (annotation.platform()) {
      case WINDOWS:
        return Platform.WINDOWS;
      case UNIX:
        return Platform.NIX;
      case DEFAULT:
      default:
        return SystemUtils.IS_OS_WINDOWS ? Platform.WINDOWS : Platform.NIX;
    }
  }
}
