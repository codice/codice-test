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

import java.io.IOException;
import java.util.stream.Stream;
import org.codice.dominion.options.Options.UpdateFile;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFileReplacementOption;

/** Extension point for the {@link UpdateFile} option annotation. */
public class UpdateFileExtension extends AbstractFileExtension<UpdateFile> {
  @Override
  public Option[] options(
      UpdateFile annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader)
      throws IOException {
    final String file = annotation.file();
    final String url = annotation.url();
    final String content = annotation.content();
    final String resource = annotation.resource();
    final boolean fileIsDefined = Utilities.isDefined(file);
    final boolean urlIsDefined = Utilities.isDefined(url);
    final boolean contentIsDefined = Utilities.isDefined(content);
    final boolean resourceIsDefined = Utilities.isDefined(resource);
    final long count =
        Stream.of(fileIsDefined, urlIsDefined, contentIsDefined, resourceIsDefined)
            .filter(Boolean.TRUE::equals)
            .count();

    if (count == 0L) {
      throw new IllegalArgumentException(
          "must specify one of ReplaceFile.file(), ReplaceFile.url(), ReplaceFile.content(), or ReplaceFile.resource() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName());
    } else if (count > 1L) {
      throw new IllegalArgumentException(
          "specify only one of ReplaceFile.file(), ReplaceFile.url(), ReplaceFile.content(), or ReplaceFile.resource() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName());
    }
    final KarafDistributionConfigurationFileReplacementOption option;

    if (fileIsDefined) {
      option = fileOption(file, annotation.target());
    } else if (urlIsDefined) {
      option = urlOption(url, annotation.target());
    } else if (contentIsDefined) {
      option = contentOption(content, annotation.target());
    } else {
      option = resourceOption(resource, annotation.target(), resourceLoader);
    }
    return new Option[] {
      new KarafDistributionConfigurationFileRemoveOption(
          interpolator,
          annotation.location(),
          option.getConfigurationFilePath(),
          option.getSource())
    };
  }
}
