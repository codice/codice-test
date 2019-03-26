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
package org.codice.restassured.dominion.options;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.codice.dominion.options.Options;
import org.codice.dominion.options.Permission;
import org.codice.dominion.options.karaf.KarafOptions;

/**
 * This class defines annotations that can be used to configure Dominion containers. It is solely
 * used for scoping.
 */
public class RestAssuredOptions {
  /** Options to install the Rest Assured test library. */
  @KarafOptions.InstallFeature(name = "rest-assured")
  @Options.GrantPermission(
    permission = {
      @Permission(clazz = java.io.FilePermission.class, name = "<<ALL FILES>>", actions = "read"),
      @Permission(clazz = java.io.FilePermission.class, name = "-", actions = "read"),
      @Permission(clazz = RuntimePermission.class, name = "createClassLoader")
    }
  )
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface Install {}
}
