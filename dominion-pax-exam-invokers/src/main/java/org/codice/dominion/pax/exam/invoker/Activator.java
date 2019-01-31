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

import java.util.Dictionary;
import java.util.Hashtable;
import org.ops4j.pax.exam.ProbeInvokerFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

public class Activator implements BundleActivator {
  @Override
  @SuppressWarnings("squid:S1149" /* Dictionary is part of OSGi's API requirement */)
  public void start(BundleContext context) {
    final ProbeInvokerFactory factory = new DominionProbeInvokerFactory();
    final Dictionary<String, Object> props = new Hashtable<>(6);

    props.put(Constants.SERVICE_DESCRIPTION, "Codice Test :: Dominion :: Pax Exam :: Invokers");
    props.put(Constants.SERVICE_VENDOR, "Codice Foundation");
    props.put("driver", "junit");
    context.registerService(ProbeInvokerFactory.class, factory, props);
  }

  @Override
  public void stop(BundleContext context) throws Exception { // nothing to do
  }
}
