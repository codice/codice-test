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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.codice.junit.ExtensionMethodRuleAnnotation;
import org.codice.test.commons.ReflectionUtils;
import org.codice.test.commons.ReflectionUtils.AnnotationEntry;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * The <code>MethodRuleAnnotationProcessor</code> class provides a JUnit4 method rule that allows
 * specification of method rules via the meta-annotation {@link ExtensionMethodRuleAnnotation}.
 *
 * <p>The order annotations containing the meta-annotation {@link ExtensionMethodRuleAnnotation} are
 * provided will dictate the order they will be applied from outermost to innermost.
 *
 * <p>It is possible to annotate the test class if the method rule should apply to all tests in that
 * class or a particular test method if the method rule should only apply to that test method.
 *
 * <p>The advantage of using the test runner {@link org.codice.junit.MethodRuleAnnotationRunner}
 * over the method rule {@link MethodRuleAnnotationProcessor} is that it guarantees that all
 * annotation-based rules will be considered outermost compared to any rules defined within the test
 * class whereas the processor cannot guarantee that since it is at the mercy of JUnit in terms of
 * how these are internally initialized.
 */
public class MethodRuleAnnotationProcessor implements MethodRule {
  /**
   * Adds a method rule annotation processor around a set of other rules. If one is already defined,
   * it is promoted to the bottom of the list otherwise a new one is added at the bottom of the
   * list.
   *
   * <p>By adding it last, the annotation processor method rule will have higher priority and be the
   * outer most rule.
   *
   * @param rules the rules in which to adda method rule annotation processor around
   * @return the updated list of rules
   */
  public static List<MethodRule> around(List<MethodRule> rules) {
    MethodRule found = null;

    for (final Iterator<MethodRule> i = rules.iterator(); i.hasNext(); ) {
      final MethodRule r = i.next();

      if (r instanceof MethodRuleAnnotationProcessor) {
        found = r;
        i.remove();
      }
    }
    // by adding it last, the annotation processor method rule will have higher priority and be
    // the outer most rule
    rules.add((found != null) ? found : new MethodRuleAnnotationProcessor());
    return rules;
  }

  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    final List<MethodRule> rules =
        Stream.concat(
                ReflectionUtils.annotationsByType(
                    target.getClass(), ExtensionMethodRuleAnnotation.class),
                ReflectionUtils.annotationsByType(
                    method.getMethod(), ExtensionMethodRuleAnnotation.class))
            .map(MethodRuleAnnotationProcessor::newInstance)
            .collect(Collectors.toList());

    for (final ListIterator<MethodRule> i = rules.listIterator(rules.size()); i.hasPrevious(); ) {
      base = i.previous().apply(base, method, target);
    }
    return base;
  }

  private static MethodRule newInstance(AnnotationEntry<ExtensionMethodRuleAnnotation> entry) {
    final Class<? extends MethodRule> clazz = entry.getAnnotation().value();

    try {
      return MethodRuleAnnotationProcessor.newInstance(
          clazz, entry.getEnclosingAnnotation().getAnnotation());
    } catch (IllegalAccessException | InstantiationException e) {
      throw new AssertionError("failed to instantiate method rule: " + clazz.getName(), e);
    } catch (InvocationTargetException e) {
      throw new AssertionError(
          "failed to instantiate method rule: " + clazz.getName(), e.getTargetException());
    }
  }

  private static MethodRule newInstance(Class<? extends MethodRule> clazz, Annotation enclosing)
      throws InvocationTargetException, InstantiationException, IllegalAccessException {
    try {
      // first check if a constructor that can receive the annotation exist
      return clazz.getConstructor(enclosing.annotationType()).newInstance(enclosing);
    } catch (NoSuchMethodException e) { // ignore and continue with default ctor
      return clazz.newInstance();
    }
  }
}
