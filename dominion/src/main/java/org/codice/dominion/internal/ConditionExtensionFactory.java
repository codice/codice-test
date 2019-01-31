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
package org.codice.dominion.internal;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.codice.dominion.conditions.Condition;
import org.codice.dominion.conditions.Conditions;
import org.codice.dominion.conditions.extensions.BooleanSystemPropertyExtension;
import org.codice.dominion.conditions.extensions.NotBlankSystemPropertyExtension;
import org.codice.dominion.conditions.extensions.NotEmptySystemPropertyExtension;

/** Factory implementation for the system Dominion condition extensions. */
public class ConditionExtensionFactory implements Condition.Factory {
  private static final Map<Class<? extends Annotation>, Condition.Extension<?>> EXTENSIONS;

  static {
    final Map<Class<? extends Annotation>, Condition.Extension<?>> map = new HashMap<>(8);

    map.put(Conditions.BooleanSystemProperty.class, new BooleanSystemPropertyExtension());
    map.put(Conditions.NotBlankSystemProperty.class, new NotBlankSystemPropertyExtension());
    map.put(Conditions.NotEmptySystemProperty.class, new NotEmptySystemPropertyExtension());
    EXTENSIONS = Collections.unmodifiableMap(map);
  }

  @Nullable
  @Override
  public Condition.Extension getExtension(java.lang.annotation.Annotation annotation) {
    return ConditionExtensionFactory.EXTENSIONS.get(annotation.annotationType());
  }
}
