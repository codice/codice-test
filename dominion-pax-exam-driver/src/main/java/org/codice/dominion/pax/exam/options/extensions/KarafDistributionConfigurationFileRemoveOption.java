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
package org.codice.dominion.pax.exam.options.extensions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.codice.dominion.DominionException;
import org.codice.dominion.options.Options.UpdateFile.Location;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileReplacementOption;

/**
 * Provides an extension to PaxExam's KarafDistributionConfigurationFileReplacementOption which
 * supports removing entries from configuration properties.
 */
public class KarafDistributionConfigurationFileRemoveOption
    extends KarafDistributionConfigurationFileReplacementOption {
  private final PaxExamInterpolator interpolator;
  private final Location location;

  public KarafDistributionConfigurationFileRemoveOption(
      PaxExamInterpolator interpolator,
      Location location,
      String configurationFilePath,
      File source) {
    super(configurationFilePath, source);
    this.interpolator = interpolator;
    this.location = location;
  }

  @Override
  @SuppressWarnings("squid:S2095" /* closing of streams is handled by FileUtils.copy */)
  public File getSource() {
    // find the original file so we can update it
    final String path = getConfigurationFilePath();
    // see KarafTestContainer.updateUserSetProperties() for logic on how to find the location
    // of a file
    File original =
        KarafDistributionConfigurationFileRemoveOption.toFile(interpolator.getKarafHome(), path);

    if (!original.exists()) {
      File customFile = null;

      if (path.startsWith("data/")) {
        customFile =
            KarafDistributionConfigurationFileRemoveOption.toFile(
                interpolator.getKarafData(), path.substring(5));
      } else if (path.startsWith("etc/")) {
        customFile =
            KarafDistributionConfigurationFileRemoveOption.toFile(
                interpolator.getKarafEtc(), path.substring(4));
      }
      if ((customFile != null) && customFile.exists()) {
        original = customFile;
      }
    }
    final File source = super.getSource();

    if (!original.exists()) { // nothing to append/prepend to
      return source;
    }
    File temp = null;

    try {
      temp = Files.createTempFile(UpdateFileExtension.class.getName(), ".tmp").toFile();
      temp.deleteOnExit();
      final InputStream is;

      switch (location) {
        case PREPEND:
          is = new SequenceInputStream(new FileInputStream(source), new FileInputStream(original));
          break;
        case APPEND:
        default:
          is = new SequenceInputStream(new FileInputStream(original), new FileInputStream(source));
          break;
      }
      FileUtils.copyInputStreamToFile(is, temp);
    } catch (IOException e) {
      FileUtils.deleteQuietly(temp);
      throw new DominionException("error occurred while updating file: " + original, e);
    }
    return temp;
  }

  private static File toFile(Path base, String location) {
    if (location.startsWith("/")) {
      return base.resolve(location.substring(1)).toFile();
    }
    return base.resolve(location).toFile();
  }
}
