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

import org.codice.dominion.options.Options.SetSystemProperty;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link SetSystemProperty} option annotation. */
public class SetSystemPropertyExtension implements Extension<SetSystemProperty> {
  @Override
  public Option[] options(
      SetSystemProperty annotation, Class<?> testClass, ResourceLoader resourceLoader) {
    final String key = annotation.key();
    final String value = annotation.value();

    return new Option[] {CoreOptions.systemProperty(key).value(value)};
  }
}
