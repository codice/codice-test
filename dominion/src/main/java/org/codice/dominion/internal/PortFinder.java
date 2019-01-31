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
package org.codice.dominion.internal;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import org.codice.dominion.interpolate.InterpolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class used to find and reserve a range of ports, and assign ports within that range by name. For
 * instance, creating a new instance of {@link PortFinder} will reserve a block of ports, calling
 * {@link #getPort(String)} the first time will assign a specific port in that range with the name
 * provided, and calling one of those two methods with that name will return the same port number
 * afterwards.
 */
public class PortFinder implements Closeable {
  private static final Logger LOGGER = LoggerFactory.getLogger(PortFinder.class);

  @SuppressWarnings("squid:S2065" /* transient used by Gson to skip the field */)
  private final transient ServerSocket placeHolderSocket;

  private final int basePort;

  private final int blockSize;

  private volatile int nextPort;

  private final Map<String, Integer> registeredPorts = new HashMap<>();

  @SuppressWarnings("unused" /* used by Gson */)
  public PortFinder() {
    this.blockSize = -1;
    this.placeHolderSocket = null;
    this.basePort = -1;
    this.nextPort = -1;
  }

  /**
   * Default constructor. Finds and reserves a range of <code>blockSize</code> ports starting at or
   * after <code>basePort</code>.
   */
  public PortFinder(int basePort, int blockSize) {
    this.blockSize = blockSize;
    this.placeHolderSocket = findServerSocket(basePort, blockSize);
    this.basePort = placeHolderSocket.getLocalPort();
    this.nextPort = basePort + 1;
  }

  /**
   * Gets the port number associated with the key provided. Assigns a new port number if the key
   * couldn't be found.
   *
   * @param portKey key of the port to look up
   * @return port number associated with the key provided
   * @throws InterpolationException if the number of ports in the reserved range was exceeded
   */
  public int getPort(String portKey) {
    synchronized (registeredPorts) {
      final Integer port = registeredPorts.get(portKey);

      if (port != null) {
        return port;
      }
      final int num = nextPort++;

      if (num >= basePort + blockSize) {
        throw new InterpolationException(
            "Failed to reserve '" + portKey + "' port; too many ports requested");
      }
      registeredPorts.put(portKey, num);
      LOGGER.info("Reserving '{}' port: {}", portKey, num);
      return num;
    }
  }

  @Override
  public void close() throws IOException {
    if (placeHolderSocket != null) {
      placeHolderSocket.close();
    }
  }

  @SuppressWarnings("squid:S1141" /* simple enough here to keep it as 2 try/catch*/)
  private ServerSocket findServerSocket(int portToTry, int blockSize) {
    try {
      final ServerSocket markerSocket = getMarkerSocket(portToTry);

      try {
        checkAllPortsInRangeAvailable(portToTry);
        return markerSocket;
      } catch (Exception e) {
        markerSocket.close();
        throw e;
      }
    } catch (Exception e) {
      LOGGER.debug("Port {} unavailable, trying {}", portToTry, portToTry + blockSize);
      return findServerSocket(portToTry + blockSize, blockSize);
    }
  }

  private ServerSocket getMarkerSocket(int portToTry) throws IOException {
    // No need for try-with-resource, handled by calling method
    @SuppressWarnings("squid:S2095")
    final ServerSocket markerSocket = new ServerSocket(portToTry);

    markerSocket.setReuseAddress(true);
    return markerSocket;
  }

  private void checkAllPortsInRangeAvailable(int markerPort) throws IOException {
    for (int i = markerPort + 1; i < markerPort + blockSize; i++) {
      try (ServerSocket ignored = new ServerSocket(i)) {
        // Nothing to do, just checking port i was available
      }
    }
  }
}
