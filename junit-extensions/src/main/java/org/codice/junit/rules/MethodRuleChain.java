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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * The <code>MethodRuleChain</code> class provides a JUnit4 method rule that allows ordering of
 * other method rules.
 */
public class MethodRuleChain implements MethodRule {
  /** Holds an empty chain. */
  private static final MethodRuleChain EMPTY_CHAIN = new MethodRuleChain(Collections.emptyList());

  /**
   * Returns a <code>MethodRuleChain</code> without a {@link MethodRule}. This method may be the
   * starting point of a chain.
   *
   * @return a method rule chain without any method rules
   */
  public static MethodRuleChain emptyChain() {
    return MethodRuleChain.EMPTY_CHAIN;
  }

  /**
   * Returns a <code>MethodRuleChain</code> with a single {@link MethodRule}. This method is the
   * usual starting point of a chain.
   *
   * @param outerRule the outer rule of the chain
   * @return a new chain with a single rule
   */
  public static MethodRuleChain outer(MethodRule outerRule) {
    return MethodRuleChain.emptyChain().around(outerRule);
  }

  /** Holds the rules starting with the inner most. */
  private final List<MethodRule> rules;

  /**
   * Instantiates a new <code>MethodRuleChain</code> object.
   *
   * @param rules the non-<code>null</code> method rules
   */
  public MethodRuleChain(List<MethodRule> rules) {
    this.rules = rules;
  }

  /**
   * Instantiates a new <code>MethodRuleChain</code> object.
   *
   * @param rules the non-<code>null</code> method rules
   */
  public MethodRuleChain(MethodRule... rules) {
    this(Arrays.asList(rules));
  }

  /**
   * Create a new <code>MethodRuleChain</code>, which encloses the specified rule with the rules of
   * the current chain.
   *
   * @param enclosedRule the rule to enclose
   * @return a new chain
   */
  public MethodRuleChain around(MethodRule enclosedRule) {
    final List<MethodRule> rules = new ArrayList<>();

    rules.add(enclosedRule);
    rules.addAll(this.rules);
    return new MethodRuleChain(rules);
  }

  /**
   * Gets the rules defined in the current chain.
   *
   * @return a stream of all rules defined in the current chain
   */
  public Stream<MethodRule> rules() {
    return rules.stream();
  }

  /**
   * {@inheritDoc}
   *
   * @see org.junit.rules.MethodRule#apply(org.junit.runners.model.Statement,
   *     org.junit.runners.model.FrameworkMethod, java.lang.Object)
   */
  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    for (final MethodRule r : rules) {
      base = r.apply(base, method, target);
    }
    return base;
  }
}
