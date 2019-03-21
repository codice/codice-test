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

import org.codice.dominion.options.Options.AddLocalUser;
import org.codice.dominion.options.Utilities;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.KarafUsersPropertiesFileUserPutOption;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link AddLocalUser} option annotation. */
public class LocalUserExtension implements Extension<AddLocalUser> {
  @Override
  public Option[] options(
      AddLocalUser annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader) {
    final KarafUsersPropertiesFileUserPutOption option =
        new KarafUsersPropertiesFileUserPutOption(annotation.userId());

    org.codice.dominion.options.Utilities.applyIfDefined(
        annotation.roles(), option, option::addRoles);
    Utilities.applyIfDefined(annotation.groups(), option, option::addGroups);
    return new Option[] {option};
  }
}
