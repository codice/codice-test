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
import org.apache.commons.io.FilenameUtils;
import org.codice.dominion.options.Options.UpdateFile;
import org.codice.dominion.options.Utilities;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.KarafDistributionConfigurationFileContentOption;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.pax.exam.options.SourceType;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link UpdateFile} option annotation. */
public class UpdateFileExtension implements Extension<UpdateFile> {
  @Override
  public Option[] options(
      UpdateFile annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader)
      throws IOException {
    final String file = annotation.file();
    final String url = annotation.url();
    final String[] content = annotation.content();
    final String resource = annotation.resource();
    final boolean fileIsDefined = org.codice.dominion.options.Utilities.isDefined(file);
    final boolean urlIsDefined = org.codice.dominion.options.Utilities.isDefined(url);
    final boolean contentIsDefined = Utilities.isDefined(content);
    final boolean resourceIsDefined = org.codice.dominion.options.Utilities.isDefined(resource);
    final long count =
        Stream.of(fileIsDefined, urlIsDefined, contentIsDefined, resourceIsDefined)
            .filter(Boolean.TRUE::equals)
            .count();

    if (count == 0L) {
      throw new IllegalArgumentException(
          "must specify one of file(), url(), content(), or resource() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName());
    } else if (count > 1L) {
      throw new IllegalArgumentException(
          "specify only one of file(), url(), content(), or resource() in "
              + annotation
              + " for "
              + resourceLoader.getLocationClass().getName());
    }
    if (fileIsDefined) {
      return new Option[] {
        new KarafDistributionConfigurationFileContentOption(
            interpolator,
            annotation.location(),
            // separators to Unix is on purpose as PaxExam will analyze the target based on it
            // containing / and not \ and then convert it properly
            FilenameUtils.separatorsToUnix(annotation.target()),
            SourceType.FILE,
            file)
      };
    } else if (urlIsDefined) {
      return new Option[] {
        new KarafDistributionConfigurationFileContentOption(
            interpolator,
            annotation.location(),
            // separators to Unix is on purpose as PaxExam will analyze the target
            // based on it containing / and not \ and then convert it properly
            FilenameUtils.separatorsToUnix(annotation.target()),
            SourceType.URL,
            url)
      };
    } else if (contentIsDefined) {
      return new Option[] {
        new KarafDistributionConfigurationFileContentOption(
            interpolator,
            annotation.location(),
            // separators to Unix is on purpose as PaxExam will analyze the target based on it
            // containing / and not \ and then convert it properly
            FilenameUtils.separatorsToUnix(annotation.target()),
            content)
      };
    }
    return new Option[] {
      new KarafDistributionConfigurationFileContentOption(
          interpolator,
          annotation.location(),
          // separators to Unix is on purpose as PaxExam will analyze the target based on it
          // containing / and not \ and then convert it properly
          FilenameUtils.separatorsToUnix(annotation.target()),
          resource,
          resourceLoader)
    };
  }
}
