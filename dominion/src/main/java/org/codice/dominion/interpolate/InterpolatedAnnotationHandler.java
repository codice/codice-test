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
package org.codice.dominion.interpolate;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Proxy handler for front-ending an annotation object in order to interpolate on-demand annotation
 * attributes (a.k.a methods) that are annotation with the {@link Interpolate} meta-annotation.
 *
 * <p>Here is a schema illustrating what happens when the annotation is proxied here for future
 * interpolation which is done via dynamic Java proxies.
 *
 * <p>Let's say we have an option annotation defined as:
 *
 * <pre><code>
 *   @Option.Annotation
 *   public @interface MyOption {
 *     String source();
 *     String destination();
 *   }
 * </code></pre>
 *
 * <p>Let's say that you now want to have the `source` attribute of this annotation, which happens
 * to be defined as the method <code>source()</code>, support interpolation. You would then annotate
 * this method with the {@link Interpolate} meta-annotation and the annotation interface would
 * become:
 *
 * <pre><code>
 *   @Option.Annotation
 *   public @interface MyOption {
 *     @Interpolate
 *     String source();
 *     String destination();
 *   }
 * </code></pre>
 *
 * At some point, the dominion framework will resolve down to this annotation and instead of
 * returning a particular instance of it, it will use the {@link
 * Interpolator#interpolate(Annotation)} method to get a new version of that particular instance
 * that will support delayed interpolation. The end result would be a new dynamic proxy object which
 * implements the <code>MyOption</code> interface which is indistinguishable from an actual
 * annotation instance typically provided by Java:
 *
 * <p>
 *
 * <pre>
 *    +------------------------+ +--------------------------------+ +------------------+ +-----------+
 *    | SomeProxyMyOption      | | InterpolationAnnotationHandler | | Interpolator     | | MyOption  |
 *    |    implements MyOption | |                                | |                  | |           |
 *  <-+-- source() <-----------+-+- invoke() <--------------------+-+- interpolate() <-+-+- source() |
 *    +------------------------+ +--------------------------------+ +------------------+ +-----------+
 * <p>
 *    +------------------------+ +--------------------------------+ +-----------+
 *    | SomeProxyMyOption      | | InterpolationAnnotationHandler | | MyOption  |
 *    |    implements MyOption | |                                | |           |
 *  <-+-- destination() <------+-+- invoke() <--------------------+-+- source() |
 *    +------------------------+ +--------------------------------+ +-----------+
 * </pre>
 *
 * <p>This handler also provides special support for the <code>toString()</code> method by
 * collecting all attributes annotated with the {@link Interpolate} meta-annotation from the
 * original annotation as depicted above, and adding them to the standard string returned from the
 * annotation's <code>toString()</code> method such that one can see both the non-interpolated
 * attributes and the interpolated value. For example, if <code>MyOption</code> had been defined as
 * <code>@MyOption(source="https://localhost:{port.https}", destination="etc/my.cfg"</code>, <code>
 * toString()</code> called on the annotation itself would return <code>
 * "@MyOption(source=https://localhost:{port.https}, destination=etc/my.cfg)"</code> whereas <code>
 * toString()</code> on the proxy annotation created here would return <code>
 * "@interpolation(@MyOption(source=https://localhost:{port.https}, destination=etc/my.cfg), source=https://localhost:1234"
 * </code>.
 *
 * @param <A> the type of annotation being handled
 */
class InterpolatedAnnotationHandler<A extends Annotation> implements InvocationHandler {
  private static final Method TO_STRING;

  /** Cache of annotation methods to interpolate keyed by the annotation class. */
  private static final Map<Class<? extends Annotation>, Set<Method>> METHODS_TO_INTERPOLATE =
      new ConcurrentHashMap<>();

  static {
    try {
      TO_STRING = Object.class.getMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new InternalError(e);
    }
  }

  /**
   * Interpolates a given annotation by returning a proxy version that will automatically
   * interpolate all <code>String</code> and <code>String[]</code> methods that are annotated with
   * the {@link Interpolate} meta-annotation.
   *
   * <p><i>Note:</i> This method will not attempt any interpolations. Instead it will return a proxy
   * which will delay interpolation until an annotation value marked to be interpolated is accessed.
   * It is therefore possible that accessing a value of an interpolated annotation throws an
   * unexpected instance of {@link InterpolationException}.
   *
   * @param <A> the type of annotation to be interpolated
   * @param annotation the annotation to be interpolated
   * @return <code>annotation</code> if no methods are requested to be interpolated or a proxy if
   *     interpolation was requested
   */
  static <A extends Annotation> A proxyIfItNeedsInterpolation(
      Interpolator interpolator, A annotation) {
    // check if the annotation has methods annotated with @Interpolate in which case, we will want
    // to create a proxy for the annotation such that we can report interpolated values instead
    final Class<? extends Annotation> clazz = annotation.annotationType();
    final Set<Method> toInterpolate =
        InterpolatedAnnotationHandler.METHODS_TO_INTERPOLATE.computeIfAbsent(
            clazz,
            c ->
                Stream.of(c.getMethods())
                    .filter(m -> m.getAnnotation(Interpolate.class) != null)
                    .collect(Collectors.toSet()));

    if (toInterpolate.isEmpty()) {
      return annotation;
    }
    return (A)
        Proxy.newProxyInstance(
            annotation.getClass().getClassLoader(),
            new Class<?>[] {annotation.annotationType(), Annotation.class},
            new InterpolatedAnnotationHandler(interpolator, annotation, toInterpolate));
  }

  private final Interpolator interpolator;
  private final A annotation;
  private final Set<Method> toInterpolate;

  private InterpolatedAnnotationHandler(
      Interpolator interpolator, A annotation, Set<Method> toInterpolate) {
    this.interpolator = interpolator;
    this.annotation = annotation;
    this.toInterpolate = toInterpolate;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    final Object o = method.invoke(annotation, args);

    if (method.equals(InterpolatedAnnotationHandler.TO_STRING)) {
      return toInterpolate
          .stream()
          .map(this::toInterpolatedString)
          .collect(Collectors.joining(", ", "@interpolation(" + o + ", ", ")"));
    } else if (!toInterpolate.contains(method)) {
      return o;
    }
    return toInterpolatedObject(method, o);
  }

  private Object toInterpolatedObject(Method method, Object o) {
    if (o instanceof String[]) {
      return interpolator.interpolate((String[]) o);
    } else if (o instanceof String) {
      return interpolator.interpolate((String) o);
    }
    throw new InterpolationException(
        "Can only interpolate annotated methods that returns String or String[] for: " + method);
  }

  private String toInterpolatedString(Method method) {
    String s;

    try {
      // Note. Annotation methods cannot have arguments!
      final Object o = toInterpolatedObject(method, method.invoke(annotation));

      if (o instanceof String[]) {
        s = Arrays.toString((String[]) o);
      } else { // must be a string
        s = (String) o;
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
      s = "?";
    }
    return method.getName() + '=' + s;
  }
}
