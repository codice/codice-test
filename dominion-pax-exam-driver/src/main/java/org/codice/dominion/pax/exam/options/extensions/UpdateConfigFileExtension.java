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

import org.codice.dominion.options.Options.UpdateConfigFile;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link UpdateConfigFile} option annotation. */
public class UpdateConfigFileExtension extends AbstractFileExtension<UpdateConfigFile> {
  @Override
  public Option[] options(
      UpdateConfigFile annotation,
      PaxExamInterpolator interpolator,
      ResourceLoader resourceLoader) {
    final Option option;

    switch (annotation.operation()) {
      case ADD:
        option = addOption(annotation.target(), annotation.key(), annotation.value());
        break;
      case SET_ABSOLUTE_PATH:
        option = setAbsolutePathOption(annotation.target(), annotation.key(), annotation.value());
        break;
      case REMOVE:
        option = removeOption(annotation.target(), annotation.key(), annotation.value());
        break;
      case SET:
      default:
        option = setOption(annotation.target(), annotation.key(), annotation.value());
    }
    return new Option[] {option};
  }
}
