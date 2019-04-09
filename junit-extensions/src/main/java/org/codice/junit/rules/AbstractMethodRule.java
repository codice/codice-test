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

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/** This class provides additional useful functionalities for method rules. */
public abstract class AbstractMethodRule implements SnapshotMethodRule {
  /** Holds the test method currently running. */
  private volatile FrameworkMethod method = null;

  /** Holds the Javassist reference to the test class. */
  private volatile CtClass ctclass = null;

  /** Holds the Javassist reference to the test method. */
  private volatile CtMethod ctmethod = null;

  /**
   * Gets the name of the test method being executed.
   *
   * @return the name of the test method being executed
   */
  public String getName() {
    final FrameworkMethod m = method;

    return (m != null) ? m.getName() : null;
  }

  /**
   * Gets the source file for the test method if available.
   *
   * @return the source file where the test method is defined or <code>null</code> if not available
   */
  public String getSourceFile() {
    final CtClass c = ctclass;

    if (c != null) {
      return c.getClassFile2().getSourceFile();
    }
    return null;
  }

  /**
   * Gets the line number for the test method if available.
   *
   * @return the line number of the source line corresponding to the test method or <code>-1</code>
   *     if not available
   */
  public int getLineNumber() {
    final CtMethod m = ctmethod;

    if (m != null) {
      return m.getMethodInfo2().getLineNumber(0);
    }
    return -1;
  }

  @Override
  public void snapshot(FrameworkMethod method, Object target) {
    init(method, target);
  }

  @Override
  public Statement applyAfterSnapshot(Statement base, FrameworkMethod method, Object target) {
    init(method, target);
    return base;
  }

  private void init(FrameworkMethod method, Object target) {
    if (this.method == null) {
      this.method = method;
      try {
        final ClassPool pool = ClassPool.getDefault();

        pool.insertClassPath(new LoaderClassPath(target.getClass().getClassLoader()));
        this.ctclass = pool.get(method.getDeclaringClass().getCanonicalName());
        this.ctmethod = ctclass.getDeclaredMethod(method.getName());
      } catch (Exception e) { // ignore and continue without javassist info
        e.printStackTrace();
      }
    }
  }
}
