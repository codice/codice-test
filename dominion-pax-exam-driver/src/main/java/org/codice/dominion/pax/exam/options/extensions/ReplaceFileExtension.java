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
import org.codice.dominion.options.Options.ReplaceFile;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link ReplaceFile} option annotation. */
public class ReplaceFileExtension extends AbstractFileExtension<ReplaceFile> {
  @Override
  public Option[] options(
      ReplaceFile annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader)
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
    if (fileIsDefined) {
      return new Option[] {fileOption(file, annotation.target())};
    } else if (urlIsDefined) {
      return new Option[] {urlOption(url, annotation.target())};
    } else if (contentIsDefined) {
      return new Option[] {contentOption(content, annotation.target())};
    }
    return new Option[] {resourceOption(resource, annotation.target(), resourceLoader)};
  }
}
