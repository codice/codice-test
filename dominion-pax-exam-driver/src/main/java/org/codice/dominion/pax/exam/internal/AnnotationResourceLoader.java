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
package org.codice.dominion.pax.exam.internal;

import java.io.InputStream;
import javax.annotation.Nullable;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.test.commons.ReflectionUtils.AnnotationEntry;

/**
 * Implementation of the {@link ResourceLoader} interface capable of loading a resource from where
 * an annotation was used.
 */
class AnnotationResourceLoader implements ResourceLoader {
  AnnotationEntry<?> entry;

  AnnotationResourceLoader(AnnotationEntry<?> entry) {
    this.entry = entry;
  }

  @Override
  public Class<?> getLocationClass() {
    return entry.getEnclosingAnnotation().getEnclosingElementClass();
  }

  @SuppressWarnings("squid:CommentedOutCodeLine" /* no code commented here */)
  @Nullable
  @Override
  public InputStream getResourceAsStream(String name) {
    // here we need to load the resource from where the annotation that includes
    // @Option.Annotation or @Condition.Annotation is defined and not the one directly associated
    // with the annotation that includes @Option.Annotation or @Condition.Annotation
    //
    // For example:
    //    @KarafOptions.Feature(...)
    //    @interface MyAnnotation {
    //    }
    // In this case:
    //    'entry.getAnnotation()' == @Option.Annotation defined inside @KarafOptions.Feature
    //    'entry.getEnclosingResourceAsStream()` would load from KarafOptions.Feature
    //    'entry.getEnclosingAnnotation().getAnnotation()' == @KarafOptions.Feature(...)
    //                                                        defined inside @MyAnnotation
    //    'entry.getEnclosingAnnotation().getEnclosingResourceAsStream()` would load from
    //                                                                            MyAnnotation
    //    'entry.getEnclosingAnnotation().getEnclosingAnnotation().getAnnotation()'
    //                                                                        == @MyAnnotation
    return entry.getEnclosingAnnotation().getEnclosingResourceAsStream(name);
  }

  @Override
  public String toString() {
    return getLocationClass().getName();
  }
}
