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

import org.codice.dominion.options.Options;
import org.codice.dominion.options.Options.EnableRemoteDebugging;
import org.codice.dominion.options.Options.Environment;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link EnableRemoteDebugging} option annotation. */
@Environment({
  "KARAF_DEBUG=true",
  "JAVA_DEBUG_OPTS=-Xrunjdwp:transport=dt_socket,server=y,suspend={{"
      + Options.EnableRemoteDebugging.PROPERTY_KEY
      + ":-false}?y:n},address={port.debug}"
})
public class EnableRemoteDebuggingExtension implements Extension<EnableRemoteDebugging> {
  @Override
  public Option[] options(
      EnableRemoteDebugging annotation, Class<?> testClass, ResourceLoader resourceLoader)
      throws Throwable {
    return new Option[0];
  }
}
