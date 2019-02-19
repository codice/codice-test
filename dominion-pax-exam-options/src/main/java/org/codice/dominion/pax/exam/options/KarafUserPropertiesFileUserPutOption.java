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
package org.codice.dominion.pax.exam.options;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.codice.dominion.options.karaf.KarafOptions;
import org.ops4j.pax.exam.karaf.options.KarafDistributionConfigurationFilePutOption;

/** PaxExam option for adding a new user or replacing an existing one. */
public class KarafUserPropertiesFileUserPutOption
    extends KarafDistributionConfigurationFilePutOption {

  private final Set<String> roles;
  private final Set<String> groups;

  /**
   * Creates a new option.
   *
   * @param userId the user id to be added or replaced
   */
  public KarafUserPropertiesFileUserPutOption(String userId) {
    super(KarafOptions.USER_PROPERTIES, userId, null);
    this.roles = new LinkedHashSet<>();
    this.groups = new LinkedHashSet<>();
  }

  /**
   * Creates a new option.
   *
   * @param userId the user id to be added or replaced
   * @param roles the roles to be added to the user
   */
  public KarafUserPropertiesFileUserPutOption(String userId, String... roles) {
    this(userId);
    addRoles(roles);
  }

  /**
   * Gets the unique user id to add or replace.
   *
   * @return the unique user id
   */
  public String getUserId() {
    return getKey();
  }

  /**
   * Adds the specified role to the user.
   *
   * @param role the role to be added to the user
   * @return this for chaining
   */
  public KarafUserPropertiesFileUserPutOption addRole(String role) {
    roles.add(role);
    return this;
  }

  /**
   * Adds the specified roles to the user.
   *
   * @param roles the roles to be added to the user
   * @return this for chaining
   */
  public KarafUserPropertiesFileUserPutOption addRoles(String... roles) {
    Stream.of(roles).forEach(this.roles::add);
    return this;
  }

  /**
   * Gets the roles to be added to the user.
   *
   * @return a stream of all roles for the user
   */
  public Stream<String> roles() {
    return roles.stream();
  }

  /**
   * Adds the specified group to the user.
   *
   * @param group the group to be added to the user
   * @return this for chaining
   */
  public KarafUserPropertiesFileUserPutOption addGroup(String group) {
    groups.add(group);
    return this;
  }

  /**
   * Adds the specified groups to the user.
   *
   * @param groups the groups to be added to the user
   * @return this for chaining
   */
  public KarafUserPropertiesFileUserPutOption addGroups(String... groups) {
    Stream.of(groups).forEach(this.groups::add);
    return this;
  }

  /**
   * Gets the groups to be added to the user.
   *
   * @return a stream of all groups for the user
   */
  public Stream<String> groups() {
    return groups.stream();
  }

  @Override
  public Object getValue() {
    return Stream.concat(
            roles.stream(),
            groups.stream().map(KarafUserPropertiesFileGroupPutOption.GROUP_PREFIX::concat))
        .collect(Collectors.joining(","));
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{userId="
        + getUserId()
        + ", roles="
        + roles
        + ", groups="
        + groups
        + "}";
  }
}
