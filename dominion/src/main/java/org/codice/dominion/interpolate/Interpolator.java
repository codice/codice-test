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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.matcher.StringMatcher;
import org.apache.commons.text.matcher.StringMatcherFactory;
import org.codice.dominion.internal.PortFinder;
import org.codice.test.commons.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Interpolation service. */
public class Interpolator implements Closeable, StringLookup {
  private static final Logger LOGGER = LoggerFactory.getLogger(Interpolator.class);

  public static final String DEFAULT_CONTAINER = "default";

  public static final String REPLACEMENTS_KEY = "dominion.interpolator.replacements";
  public static final String PORTS_KEY = "dominion.interpolator.ports";

  private static final int BASE_PORT = 20000;
  private static final int BLOCK_SIZE = 20;

  private static final Method TO_STRING;

  static {
    try {
      TO_STRING = Object.class.getMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new InternalError(e);
    }
  }

  protected final String id;

  protected final String container;

  protected final Map<String, String> replacements;

  protected final PortFinder ports;

  private final StringSubstitutor substitutor =
      new StringSubstitutor(
              this, new PrefixMatcher(), StringMatcherFactory.INSTANCE.stringMatcher("}"), '\0')
          .setEnableSubstitutionInVariables(true);

  /** Cache of annotation methods to interpolate keyed by the annotation class. */
  private final Map<Class<? extends Annotation>, Set<Method>> methodsToInterpolate =
      new ConcurrentHashMap<>();

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param id a unique id for this the corresponding test run
   */
  public Interpolator(String id) {
    this(id, Interpolator.DEFAULT_CONTAINER);
  }

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param id a unique id for this the corresponding test run
   * @param container the name of the container for which to create an interpolator
   */
  public Interpolator(String id, String container) {
    LOGGER.debug("Interpolator({}, {})", id, container);
    this.id = id;
    this.container = container;
    this.replacements = new HashMap<>();
    replacements.put("test.id", id);
    replacements.put("container.name", container);
    replacements.put("/", File.separator);
    String path = Paths.get("target", "classes").toAbsolutePath().toString();

    replacements.put("classes.path", path);
    replacements.put("classes.url", "file:" + path);
    path = Paths.get("target", "test-classes").toAbsolutePath().toString();
    replacements.put("test-classes.path", path);
    replacements.put("test-classes.url", "file:" + path);
    this.ports = new PortFinder(Interpolator.BASE_PORT, Interpolator.BLOCK_SIZE);
  }

  /**
   * Initializes a new interpolator with all the specified information already computed (typically
   * from inside a container).
   *
   * @param id a unique id for this the corresponding test run
   * @param container the name of the container for which to create an interpolator
   * @param replacements the set of replacements strings to use when interpolating
   * @param ports the ports information already allocated from the driver
   */
  public Interpolator(
      String id, String container, Map<String, String> replacements, PortFinder ports) {
    LOGGER.debug("Interpolator({}, {}, {}, {})", id, container, replacements, ports);
    this.id = id;
    this.container = container;
    this.replacements = replacements;
    this.ports = ports;
  }

  /**
   * Initializes a new interpolator with all the specified information already computed (typically
   * from inside a container).
   *
   * @param properties properties from which to retrieve the replacement and port info
   */
  public Interpolator(Properties properties) {
    LOGGER.debug("Interpolator(Properties)");
    try {
      this.replacements =
          new Gson().fromJson(properties.getProperty(Interpolator.REPLACEMENTS_KEY, ""), Map.class);
    } catch (JsonSyntaxException e) {
      throw new InterpolationException("Unable to determined replacement information", e);
    }
    this.id = initFromReplacements("test.id");
    this.container = initFromReplacements("container.name");
    try {
      final PortFinder portFinder =
          new Gson().fromJson(properties.getProperty(Interpolator.PORTS_KEY, ""), PortFinder.class);

      this.ports =
          (portFinder != null)
              ? portFinder
              : new PortFinder(Interpolator.BASE_PORT, Interpolator.BLOCK_SIZE);
    } catch (JsonSyntaxException e) {
      throw new InterpolationException("Unable to determined reserved ports information", e);
    }
  }

  /**
   * Gets the unique identifier for the current test run.
   *
   * @return a unique identifier for the current test run
   */
  public String getUUID() {
    return id;
  }

  /**
   * Gets the name of the container associated with this interpolator.
   *
   * @return the container name
   */
  public String getContainer() {
    return container;
  }

  /**
   * Interpolates a given annotation by returning a proxy version that will automatically
   * interpolate all <code>String</code> and <code>String[]</code> methods that are annotated with
   * the {@link Interpolate} annotation.
   *
   * <p><<i>Note:</i> This method will not attempt any interpolations. Instead it will return a
   * proxy which will delay interpolation until an annotation value marked to be interpolated is
   * accessed. It is therefore possible that accessing a value of an interpolated annotation throws
   * an instance of {@link InterpolationException}.
   *
   * @param <A> the type of annotation to be interpolated
   * @param annotation the annotation to be interpolated
   * @return <code>annotation</code> if no methods are requested to be interpolated or a proxy if
   *     interpolation was requested
   */
  public <A extends Annotation> A interpolate(A annotation) {
    // check if the annotation has methods annotated with @Interpolate in which case, we will want
    // to create a proxy for the annotation such that we can report interpolated values instead
    final Class<? extends Annotation> clazz = annotation.annotationType();
    final Set<Method> toInterpolate =
        methodsToInterpolate.computeIfAbsent(
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
            new InvocationHandler() {
              @Override
              public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                final Object o = method.invoke(annotation, args);

                if (method.equals(Interpolator.TO_STRING)) {
                  return toInterpolate
                      .stream()
                      .map(this::toInterpolatedString)
                      .collect(Collectors.joining(", ", "@interpolation(" + o + ", ", ")"));
                } else if (!toInterpolate.contains(method)) {
                  return o;
                }
                return toInterpolatedObject(o);
              }

              private Object toInterpolatedObject(Object o) {
                if (o instanceof String[]) {
                  return interpolate((String[]) o);
                } else if (o instanceof String) {
                  return interpolate((String) o);
                }
                throw new InterpolationException(
                    "Can only interpolate annotated methods that returns String or String[]");
              }

              private String toInterpolatedString(Method method) {
                String s;

                try {
                  // Note. Annotation methods cannot have arguments!
                  final Object o = toInterpolatedObject(method.invoke(annotation));

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
            });
  }

  /**
   * Interpolates all static fields of type <code>String</code> or <code>String[]</code> that are
   * annotated with {@link Interpolate}.
   *
   * @param <C> the class to interpolate
   * @param clazz the class to interpolate
   * @return <code>clazz</code> for chaining
   * @throws InterpolationException if unable to interpolate the class
   * @throws ContainerNotStagedException if an <code>"{karaf.XXXX}"</code> expression is found and
   *     PaxExam has not yet staged the container
   */
  public <C> Class<C> interpolate(Class<C> clazz) {
    ReflectionUtils.getAllAnnotationsForFieldsAnnotatedWith(clazz, Interpolate.class, true)
        .keySet()
        .forEach(this::interpolate);
    return clazz;
  }

  /**
   * Interpolates a given static field as long as it is of type <code>String</code> or <code>
   * String[]</code>.
   *
   * @param field the static field to interpolate
   * @return <code>field</code> for chaining
   * @throws InterpolationException if unable to interpolate the field
   * @throws ContainerNotStagedException if an <code>"{karaf.XXXX}"</code> expression is found and
   *     PaxExam has not yet staged the container
   */
  public Field interpolate(Field field) {
    final int modifiers = field.getModifiers();

    if (!Modifier.isStatic(modifiers)) {
      throw new InterpolationException("Can only interpolate annotated static fields");
    }
    try {
      final Class<?> type = field.getType();

      field.setAccessible(true);
      if (String.class.equals(type)) {
        field.set(null, interpolate((String) field.get(null)));
      } else if (String[].class.equals(type)) {
        field.set(null, interpolate((String[]) field.get(null)));
      } else {
        throw new InterpolationException(
            "Can only interpolate annotated static fields of type String or String[]");
      }
    } catch (SecurityException | IllegalAccessException e) {
      throw new InterpolationException("Unable to interpolate field: " + field, e);
    }
    return field;
  }

  /**
   * Interpolates an array of strings.
   *
   * @param s the array of strings to interpolate
   * @return a cloned version of the array where all elements where interpolated or <code>null
   *     </code> if <code>s</code> is <code>null</code>
   * @throws InterpolationException if unable to interpolate a string in the array
   * @throws ContainerNotStagedException if an <code>"{karaf.XXXX}"</code> expression is found and
   *     PaxExam has not yet staged the container
   */
  @Nullable
  public String[] interpolate(@Nullable String[] s) {
    if (s == null) {
      return null;
    }
    return Stream.of(s).map(substitutor::replace).toArray(String[]::new);
  }

  /**
   * Interpolates a string.
   *
   * @param s the string to interpolate
   * @return a new interpolated string or <code>null</code> if <code>s</code> is <code>null</code>
   * @throws InterpolationException if unable to interpolate the string
   * @throws ContainerNotStagedException if an <code>"{karaf.XXXX}"</code> expression is found and
   *     PaxExam has not yet staged the container
   */
  @Nullable
  public String interpolate(@Nullable String s) {
    return substitutor.replace(s);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Lookups a replacement for the given key which first check known replacements, then system
   * properties, and finally reserved ports.
   *
   * @param key the key to search for
   * @return the corresponding replacement or <code>null</code> if none found
   */
  @Override
  @Nullable
  public String lookup(String key) {
    final int i = key.indexOf('?');

    if (i != -1) {
      final int j = key.indexOf(':', i + 1);

      if (j == -1) {
        throw new InterpolationException(
            "Invalid interpolation ternary operator; ':' was not found");
      }
      return Boolean.parseBoolean(key.substring(0, i))
          ? key.substring(i + 1, j)
          : key.substring(j + 1);
    }
    String value = replacements.get(key);

    if (value == null) {
      value = System.getProperty(key);
      if ((value == null) && key.startsWith("port.")) {
        value = Integer.toString(ports.getPort(key.substring(5)));
      }
    }
    return value;
  }

  /**
   * Gets the current replacement information as a Json string suitable to rebuild an interpolator
   * using the {@link #Interpolator(Properties)} constructor.
   *
   * @return a json string for the current set of replacement information
   */
  public String getReplacementsInfo() {
    return new Gson().toJson(replacements);
  }

  /**
   * Gets the current port information as a Json string suitable to rebuild an interpolator using
   * the {@link #Interpolator(Properties)} constructor.
   *
   * @return a json string for the current set of port information
   */
  public String getPortsInfo() {
    synchronized (ports) {
      return new Gson().toJson(ports);
    }
  }

  @Override
  public void close() throws IOException {
    ports.close();
  }

  protected String initFromReplacements(String key) {
    final String value = replacements.get(key);

    if (value == null) {
      throw new ContainerNotStagedException(
          "Unable to retrieved replacement string for {" + key + "}");
    }
    return value;
  }

  /** Matcher to match '{' as the prefix as long as it is not preceded with $. */
  private static class PrefixMatcher implements StringMatcher {
    @Override
    public int isMatch(char[] buffer, int pos, int bufferStart, int bufferEnd) {
      if (pos + 1 > bufferEnd) {
        return 0;
      }
      if ((buffer[pos] == '{') && ((pos == bufferStart) || (buffer[pos - 1] != '$'))) {
        return 1;
      }
      return 0;
    }
  }
}
