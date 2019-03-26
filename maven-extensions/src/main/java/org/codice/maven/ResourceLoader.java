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
package org.codice.maven;

import java.io.InputStream;
import javax.annotation.Nullable;

/**
 * A resource loader is capable of loading resources. Resource names follow the same conventions as
 * {@link ClassLoader#getResourceAsStream(String)}.
 */
public interface ResourceLoader {
  /**
   * Gets the class associated with the location from which the resources will be loaded.
   *
   * @return the class associated with the location from which the resources will be loaded
   */
  public Class<?> getLocationClass();

  /**
   * Locates and loads a resource given its name.
   *
   * @param name the name of the resource to be loaded
   * @return a corresponding input stream or <code>null</code> if the resource could not be found
   */
  @Nullable
  public InputStream getResourceAsStream(String name);
}
