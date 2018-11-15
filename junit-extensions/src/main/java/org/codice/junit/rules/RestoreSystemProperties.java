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

import java.util.Properties;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * The <code>RestoreSystemProperties</code> class defines a method rule for JUnit test classes that
 * will restore any changes made to system properties by a given test method.
 */
public class RestoreSystemProperties implements MethodRule {
  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object o) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        final Properties original = new Properties();

        original.putAll(System.getProperties());
        try {
          statement.evaluate();
        } finally {
          System.setProperties(original);
        }
      }
    };
  }
}
