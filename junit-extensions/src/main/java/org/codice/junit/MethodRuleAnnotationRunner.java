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
package org.codice.junit;

import java.util.List;
import org.codice.junit.rules.MethodRuleAnnotationProcessor;
import org.junit.rules.MethodRule;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * The <code>MethodRuleAnnotationRunner</code> class defines a JUnit4 test runner that allows
 * specification of method rules via the meta-annotation {@link ExtensionMethodRuleAnnotation}.
 *
 * <p>The order annotations containing the meta-annotation {@link ExtensionMethodRuleAnnotation} are
 * provided will dictate the order they will be applied from outermost to innermost.
 *
 * <p>It is possible to annotate the test class if the method rule should apply to all tests in that
 * class or a particular test method if the method rule should only apply to that test method.
 *
 * <p>The advantage of using the test runner {@link MethodRuleAnnotationRunner} over the method rule
 * {@link MethodRuleAnnotationProcessor} is that it guarantees that all annotation-based rules will
 * be considered outermost compared to any rules defined within the test class whereas the processor
 * cannot guarantee that since it is at the mercy of JUnit in terms of how these are internally
 * initialized.
 */
public class MethodRuleAnnotationRunner extends BlockJUnit4ClassRunner {
  /**
   * Creates a new annotation-based test runner for the specified test class.
   *
   * @param clazz the test class to run
   * @throws InitializationError if the test class is malformed
   */
  public MethodRuleAnnotationRunner(Class<?> clazz) throws InitializationError {
    super(clazz);
  }

  @Override
  protected List<MethodRule> rules(Object target) {
    final List<MethodRule> rules = super.rules(target);

    if (rules
        .stream()
        .filter(MethodRuleAnnotationProcessor.class::isInstance)
        .findAny()
        .isPresent()) {
      throw new IllegalStateException(
          "a method rule annotation processor is already defined in "
              + target.getClass().getName());
    }
    // by adding it last, the annotation processor method rule will have higher priority and be
    // the outmost rule
    rules.add(new MethodRuleAnnotationProcessor());
    return rules;
  }
}
