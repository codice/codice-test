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
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;

/** Provides some common utility methods for dealing with file-based options. */
public abstract class AbstractFileExtension<A extends Annotation> implements Extension<A> {
  protected Option[] fileOptions(String source, String target) {
    return fileOptions(new File(FilenameUtils.separatorsToSystem(source)), target);
  }

  protected Option[] fileOptions(File source, String target) {
    return new Option[] {
      KarafDistributionOption.replaceConfigurationFile(
          FilenameUtils.separatorsToSystem(target), source)
    };
  }

  protected Option[] urlOptions(String url, String target) throws IOException {
    try (final InputStream is = new URL(url).openStream()) {
      return streamOptions(is, target);
    }
  }

  protected Option[] contentOptions(String content, String target) throws IOException {
    try (final InputStream is = new ByteArrayInputStream(content.getBytes("UTF-8"))) {
      return streamOptions(is, target);
    }
  }

  protected Option[] resourceOptions(String resource, String target, ResourceLoader resourceLoader)
      throws IOException {
    try (final InputStream is = resourceLoader.getResourceAsStream(resource)) {
      if (is == null) {
        throw new OptionException(
            "unable to locate resource "
                + resource
                + " in "
                + resourceLoader.getLocationClass().getName());
      }
      return streamOptions(is, target);
    }
  }

  @SuppressWarnings({
    "squid:S4042" /* deleting a temp file and we don't care if it fails */,
    "squid:S899" /* deleting a temp file and we don't care if it fails */
  })
  protected Option[] streamOptions(InputStream is, String target) throws IOException {
    final File temp = Files.createTempFile(getClass().getName(), ".tmp").toFile();

    temp.deleteOnExit();
    try {
      FileUtils.copyInputStreamToFile(is, temp);
      return fileOptions(temp, target);
    } catch (IOException e) {
      temp.delete();
      throw e;
    }
  }

  protected Option[] setOptions(String target, String key, String value) {
    return new Option[] {
      KarafDistributionOption.editConfigurationFilePut(
          FilenameUtils.separatorsToSystem(target), key, value)
    };
  }

  protected Option[] setAbsolutePathOptions(String target, String key, String path) {
    return setOptions(
        FilenameUtils.separatorsToSystem(target),
        key,
        new File(FilenameUtils.separatorsToSystem(path)).getAbsolutePath());
  }

  protected Option[] addOptions(String target, String key, String value) {
    return new Option[] {
      KarafDistributionOption.editConfigurationFileExtend(
          FilenameUtils.separatorsToSystem(target), key, value)
    };
  }
}
