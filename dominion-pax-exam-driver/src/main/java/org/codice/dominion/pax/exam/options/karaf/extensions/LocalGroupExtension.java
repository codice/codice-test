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

import org.codice.dominion.options.karaf.KarafOptions.LocalGroup;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.KarafUserPropertiesFileUserPutOption;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link LocalGroup} option annotation. */
public class LocalGroupExtension implements Extension<LocalGroup> {
  @Override
  public Option[] options(
      LocalGroup annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader) {
    return new Option[] {
      new KarafUserPropertiesFileUserPutOption(annotation.groupId(), annotation.roles())
    };
  }
}
