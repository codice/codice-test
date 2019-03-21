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
package org.codice.dominion.options;

/** Class used to defined known user roles. */
public class UserRoles {
  /** Group user role. */
  public static final String GROUP = "group";

  /** Administrator user role. */
  public static final String ADMIN = "admin";

  /** Manager user role. */
  public static final String MANAGER = "manager";

  /** Viewer user role. */
  public static final String VIEWER = "viewer";

  /** SSH user role. */
  public static final String SSH = "ssh";

  /** Data manager user role. */
  public static final String DATA_MANAGER = "data-manager";

  /** System administrator user role. */
  public static final String SYSTEM_ADMIN = "system-admin";

  /** System bundles user role. */
  public static final String SYSTEM_BUNDLES = "systembundles";

  /** System user role. */
  public static final String SYSTEM_USER = "system-user";

  /** System history user role. */
  public static final String SYSTEM_HISTORY = "system-history";

  private UserRoles() {}
}
