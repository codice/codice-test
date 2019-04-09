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
package org.codice.junit.rules;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * A <code>SnapshotMethodRule</code> extends on JUnit's {@link MethodRule} interface to allow
 * snapshots to be taken before any of the rules that might be defined are applied as long as the
 * test class is running with the {@link org.codice.junit.MethodRuleAnnotationRunner} or the
 * dominion test runner, chained as part of the {@link MethodRuleChain}, or the {@link
 * MethodRuleAnnotationProcessor} method rule has been defined in the test class (for
 * annotation-based rules). Otherwise it will behave as a normal method rule and snapshoting will be
 * triggered when the rule is applied (see the default implementation for the {@link #apply} method.
 */
public interface SnapshotMethodRule extends MethodRule {
  /**
   * Called to take a snapshot before any method rules are applied.
   *
   * @param method the method for which we are snapshoting
   * @param target the object on which the method will be run.
   */
  void snapshot(FrameworkMethod method, Object target);

  /**
   * Modifies the method-running {@link Statement} to implement an additional test-running rule.
   *
   * <p>This method will only be called after the {@link #snapshot} method has been called.
   *
   * @param base the {@link Statement} to be modified
   * @param method the method to be run
   * @param target the object on which the method will be run.
   * @return a new statement, which may be the same as {@code base}, a wrapper around {@code base},
   *     or a completely new Statement.
   */
  Statement applyAfterSnapshot(Statement base, FrameworkMethod method, Object target);

  /**
   * {@inheritDoc}
   *
   * <p>The implementation provided here is only used when such a method rule is not being used as a
   * snapshot rule (see class description).
   */
  @Override
  default Statement apply(Statement base, FrameworkMethod method, Object target) {
    snapshot(method, target);
    return applyAfterSnapshot(base, method, target);
  }
}
