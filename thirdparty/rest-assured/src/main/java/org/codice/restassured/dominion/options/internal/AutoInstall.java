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
package org.codice.restassured.dominion.options.internal;

import org.codice.dominion.options.Option;
import org.codice.dominion.options.Options;
import org.codice.dominion.options.Permission;
import org.codice.dominion.options.karaf.KarafOptions;

/** System option for automatically installing Rest Assured test library. */
@KarafOptions.InstallFeature
@Options.GrantPermission(
  permission = {
    @Permission(clazz = java.io.FilePermission.class, name = "<<ALL FILES>>", actions = "read"),
    @Permission(clazz = java.io.FilePermission.class, name = "-", actions = "read"),
    @Permission(clazz = RuntimePermission.class, name = "createClassLoader")
  }
)
public class AutoInstall implements Option.System {}
