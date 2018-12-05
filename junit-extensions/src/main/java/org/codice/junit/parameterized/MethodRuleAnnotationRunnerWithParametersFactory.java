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

import org.codice.junit.ExtensionMethodRuleAnnotation;
import org.codice.junit.rules.MethodRuleAnnotationProcessor;
import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

/**
 * The <code>MethodRuleAnnotationRunnerWithParametersFactory</code> class defines a JUnit4 parameter
 * factory to create test runners that allows specification of method rules via the meta-annotation
 * {@link ExtensionMethodRuleAnnotation}. This class should be used when running tests with
 * <code@RunWith(Parameterized.class)</code> using a
 * <code>@UseParametersRunnerFactory(MethodRuleAnnotationRunnerWithParametersFactory.class)</code>
 * annotation.
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
public class MethodRuleAnnotationRunnerWithParametersFactory implements ParametersRunnerFactory {
  @Override
  public Runner createRunnerForTestWithParameters(TestWithParameters test)
      throws InitializationError {
    return new MethodRuleAnnotationRunnerWithParameters(test);
  }
}
