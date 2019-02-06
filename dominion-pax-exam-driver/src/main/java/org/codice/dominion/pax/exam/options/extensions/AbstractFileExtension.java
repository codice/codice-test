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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.codice.dominion.options.OptionException;
import org.codice.dominion.pax.exam.options.KarafDistributionConfigurationFileRetractOption;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileReplacementOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;

/** Provides some common utility methods for dealing with file-based options. */
public abstract class AbstractFileExtension<A extends Annotation> implements Extension<A> {
  protected KarafDistributionConfigurationFileReplacementOption fileOption(
      String source, String target) {
    return fileOption(new File(FilenameUtils.separatorsToSystem(source)), target);
  }

  protected KarafDistributionConfigurationFileReplacementOption fileOption(
      File source, String target) {
    return new KarafDistributionConfigurationFileReplacementOption(
        // separators to Unix is on purpose as PaxExam will analyze the target based on it
        // containing / and not \ and then convert it properly
        FilenameUtils.separatorsToUnix(target), source);
  }

  protected KarafDistributionConfigurationFileReplacementOption urlOption(String url, String target)
      throws IOException {
    try (final InputStream is = new URL(url).openStream()) {
      return streamOption(is, target);
    }
  }

  protected KarafDistributionConfigurationFileReplacementOption contentOption(
      String content, String target) throws IOException {
    try (final InputStream is = new ByteArrayInputStream(content.getBytes("UTF-8"))) {
      return streamOption(is, target);
    }
  }

  protected KarafDistributionConfigurationFileReplacementOption resourceOption(
      String resource, String target, ResourceLoader resourceLoader) throws IOException {
    try (final InputStream is = resourceLoader.getResourceAsStream(resource)) {
      if (is == null) {
        throw new OptionException(
            "unable to locate resource "
                + resource
                + " in "
                + resourceLoader.getLocationClass().getName());
      }
      return streamOption(is, target);
    }
  }

  @SuppressWarnings({
    "squid:S4042" /* deleting a temp file and we don't care if it fails */,
    "squid:S899" /* deleting a temp file and we don't care if it fails */
  })
  protected KarafDistributionConfigurationFileReplacementOption streamOption(
      InputStream is, String target) throws IOException {
    final File temp = Files.createTempFile(getClass().getName(), ".tmp").toFile();

    temp.deleteOnExit();
    try {
      FileUtils.copyInputStreamToFile(is, temp);
      return fileOption(temp, target);
    } catch (IOException e) {
      temp.delete();
      throw e;
    }
  }

  protected Option setOption(String target, String key, String value) {
    return KarafDistributionOption.editConfigurationFilePut(
        // separators to Unix is on purpose as PaxExam will analyze the target based on it
        // containing / and not \ and then convert it properly
        FilenameUtils.separatorsToUnix(target), key, value);
  }

  protected Option setAbsolutePathOption(String target, String key, String path) {
    return setOption(
        target, key, new File(FilenameUtils.separatorsToSystem(path)).getAbsolutePath());
  }

  protected Option addOption(String target, String key, String value) {
    return KarafDistributionOption.editConfigurationFileExtend(
        // separators to Unix is on purpose as PaxExam will analyze the target based on it
        // containing / and not \ and then convert it properly
        FilenameUtils.separatorsToUnix(target), key, value);
  }

  protected Option removeOption(String target, String key, String value) {
    return new KarafDistributionConfigurationFileRetractOption(
        // separators to Unix is on purpose as our extensions (and then PaxExam) behaves the same as
        // PaxExam which analyzes the target based on it containing / and not \ and then convert it
        // properly
        FilenameUtils.separatorsToUnix(target), key, value);
  }
}
