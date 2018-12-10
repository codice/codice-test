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
package org.codice.junit.parameterized;

import java.util.List;
import org.codice.junit.ExtensionMethodRuleAnnotation;
import org.codice.junit.rules.MethodRuleAnnotationProcessor;
import org.junit.rules.MethodRule;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.TestWithParameters;

/**
 * The <code>MethodRuleAnnotationRunnerWithParameters</code> class defines a JUnit4 test runner that allows
 * specification of method rules via the meta-annotation {@link ExtensionMethodRuleAnnotation} for
 * parameterized tests. This class should not be referenced directly but instead used with
 * <code@RunWith(Parameterized.class)</code> using a
 * <code>@UseParametersRunnerFactory(MethodRuleAnnotationRunnerWithParametersFactory.class)</code>
 * annotation which will indirectly instantiate this runner for each of the parameterized tests.
 *
 * <p>The order annotations containing the meta-annotation {@link ExtensionMethodRuleAnnotation} are
 * provided will dictate the order they will be applied from outermost to innermost.
 *
 * <p>It is possible to annotate the test class if the method rule should apply to all tests in that
 * class or a particular test method if the method rule should only apply to that test method.
 *
 * <p>The advantage of using the test runner {@link MethodRuleAnnotationRunnerWithParameters} over
 * the method rule {@link MethodRuleAnnotationProcessor} is that it guarantees that all
 * annotation-based rules will be considered outermost compared to any rules defined within the test
 * class whereas the processor cannot guarantee that since it is at the mercy of JUnit in terms of
 * how these are internally initialized.
 */
public class MethodRuleAnnotationRunnerWithParameters extends BlockJUnit4ClassRunnerWithParameters {
  /**
   * Creates a new annotation-based test runner for the specified test class.
   *
   * @param test the test with parameters information
   * @throws InitializationError if the test class is malformed
   */
  public MethodRuleAnnotationRunnerWithParameters(TestWithParameters test)
      throws InitializationError {
    super(test);
  }

  @Override
  protected List<MethodRule> rules(Object target) {
    return MethodRuleAnnotationProcessor.around(super.rules(target));
  }
}
