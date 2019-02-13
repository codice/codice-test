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
package org.codice.dominion.pax.exam.invoker;

import org.ops4j.pax.exam.ProbeInvoker;
import org.ops4j.pax.exam.ProbeInvokerFactory;
import org.ops4j.pax.exam.TestContainerException;
import org.ops4j.pax.exam.util.Injector;
import org.ops4j.pax.swissbox.tracker.ServiceLookup;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DominionProbeInvokerFactory implements ProbeInvokerFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(DominionProbeInvokerFactory.class);

  @Override
  public ProbeInvoker createProbeInvoker(Object context, String encodedInstruction) {
    LOGGER.debug(
        "DominionProbeInvokerFactory::createProbeInvoker({}, {})", context, encodedInstruction);
    final BundleContext bundleContext = (BundleContext) context;
    final Injector injector = ServiceLookup.getService(bundleContext, Injector.class);
    // parse class and method out of expression:
    final String[] parts = encodedInstruction.split(";");
    final String clazz = parts[0];
    final String method = parts[1];

    try {
      return new DominionProbeInvoker(bundleContext.getBundle().loadClass(clazz), method, injector);
    } catch (ClassNotFoundException e) {
      LOGGER.error(
          "DominionProbeInvokerFactory::createProbeInvoker({}, {}) - failed to locate test class",
          context,
          encodedInstruction,
          e);
      throw new TestContainerException(e);
    }
  }
}
