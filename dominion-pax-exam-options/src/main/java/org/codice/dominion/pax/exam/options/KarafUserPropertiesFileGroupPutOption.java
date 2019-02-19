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
public class KarafUserPropertiesFileGroupPutOption
    extends KarafDistributionConfigurationFilePutOption {
  public static final String GROUP_PREFIX = "_g_:";

  private final Set<String> roles;

  /**
   * Creates a new option.
   *
   * @param groupId the group id to be added or replaced
   */
  public KarafUserPropertiesFileGroupPutOption(String groupId) {
    super(KarafOptions.USER_PROPERTIES, groupId, null);
    this.roles = new LinkedHashSet<>();
  }

  /**
   * Creates a new option.
   *
   * @param groupId the group id to be added or replaced
   * @param roles the roles to be added to the user
   */
  public KarafUserPropertiesFileGroupPutOption(String groupId, String... roles) {
    this(groupId);
    addRoles(roles);
  }

  /**
   * Gets the unique group id to add or replace.
   *
   * @return the unique group id
   */
  public String getGroupId() {
    return super.getKey();
  }

  /**
   * Adds the specified role to the group.
   *
   * @param role the role to be added to the group
   * @return this for chaining
   */
  public KarafUserPropertiesFileGroupPutOption addRole(String role) {
    roles.add(role);
    return this;
  }

  /**
   * Adds the specified roles to the group.
   *
   * @param roles the roles to be added to the group
   * @return this for chaining
   */
  public KarafUserPropertiesFileGroupPutOption addRoles(String... roles) {
    Stream.of(roles).forEach(this.roles::add);
    return this;
  }

  /**
   * Gets the roles to be added to the group.
   *
   * @return a stream of all roles for the group
   */
  public Stream<String> roles() {
    return roles.stream();
  }

  @Override
  public String getKey() {
    return KarafUserPropertiesFileGroupPutOption.GROUP_PREFIX + getGroupId();
  }

  @Override
  public Object getValue() {
    return roles.stream().collect(Collectors.joining(","));
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{groupId=" + getGroupId() + ", roles=" + roles + "}";
  }
}
