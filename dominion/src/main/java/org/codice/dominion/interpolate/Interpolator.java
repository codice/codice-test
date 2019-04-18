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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.matcher.StringMatcher;
import org.apache.commons.text.matcher.StringMatcherFactory;
import org.codice.test.commons.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Interpolation service. */
public class Interpolator implements Closeable, StringLookup {
  private static final Logger LOGGER = LoggerFactory.getLogger(Interpolator.class);

  public static final String DEFAULT_CONTAINER = "default";

  public static final String INFO_FILE_KEY = "dominion.interpolator.info";
  public static final String REPLACEMENTS_KEY = "replacements";
  public static final String PORTS_KEY = "ports";

  private static final int BASE_PORT = 20000;
  private static final int BLOCK_SIZE = 20;

  protected final Class<?> testClass;

  protected final String id;

  protected final String container;

  protected final Map<String, String> replacements;

  protected final PortFinder ports;

  private final StringSubstitutor substitutor =
      new StringSubstitutor(
              this, new PrefixMatcher(), StringMatcherFactory.INSTANCE.stringMatcher("}"), '\0')
          .setEnableSubstitutionInVariables(true);

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param testClass the current test class
   * @param id a unique id for this the corresponding test run
   */
  public Interpolator(Class<?> testClass, String id) {
    this(testClass, id, Interpolator.DEFAULT_CONTAINER);
  }

  /**
   * Initializes a new interpolator inside a driver with the specified test run id and container
   * name.
   *
   * @param testClass the current test class
   * @param id a unique id for this the corresponding test run
   * @param container the name of the container for which to create an interpolator
   */
  public Interpolator(Class<?> testClass, String id, String container) {
    LOGGER.debug("Interpolator({}, {}, {})", testClass, id, container);
    this.testClass = testClass;
    this.id = id;
    this.container = container;
    this.replacements = new HashMap<>();
    replacements.put("test.id", id);
    replacements.put("container.name", container);
    initSystemReplacements();
    String path = Paths.get("target", "classes").toAbsolutePath().toString();

    replacements.put("classes.path", path);
    replacements.put("classes.url", "file:" + path);
    path = Paths.get("target", "test-classes").toAbsolutePath().toString();
    replacements.put("test-classes.path", path);
    replacements.put("test-classes.url", "file:" + path);
    this.ports = new PortFinder(container, Interpolator.BASE_PORT, Interpolator.BLOCK_SIZE);
  }

  /**
   * Initializes a new interpolator with all the specified information already computed (typically
   * from inside a container).
   *
   * @param testClass the current test class
   * @param id a unique id for this the corresponding test run
   * @param container the name of the container for which to create an interpolator
   * @param replacements the set of replacements strings to use when interpolating
   * @param ports the ports information already allocated from the driver
   */
  public Interpolator(
      Class<?> testClass,
      String id,
      String container,
      Map<String, String> replacements,
      PortFinder ports) {
    LOGGER.debug("Interpolator({}, {}, {}, {}, {})", testClass, id, container, replacements, ports);
    this.testClass = testClass;
    this.id = id;
    this.container = container;
    this.replacements = replacements;
    this.ports = ports;
    initSystemReplacements();
  }

  /**
   * Initializes a new interpolator with all interpolation information already computed and saved to
   * the specified file (typically from inside a container).
   *
   * @param testClass the current test class
   * @param file file from which to retrieve the interpolation info
   */
  public Interpolator(Class<?> testClass, File file) {
    LOGGER.debug("Interpolator({}, {})", testClass, file);
    this.testClass = testClass;
    final Properties properties =
        AccessController.doPrivileged((PrivilegedAction<Properties>) () -> Interpolator.load(file));

    try {
      this.replacements =
          new Gson().fromJson(properties.getProperty(Interpolator.REPLACEMENTS_KEY, ""), Map.class);
    } catch (JsonSyntaxException e) {
      throw new InterpolationException("Unable to determine replacement information", e);
    }
    initSystemReplacements();
    this.id = initFromReplacements("test.id");
    this.container = initFromReplacements("container.name");
    try {
      final PortFinder portFinder =
          new Gson().fromJson(properties.getProperty(Interpolator.PORTS_KEY, ""), PortFinder.class);

      this.ports =
          (portFinder != null)
              ? portFinder
              : new PortFinder(container, Interpolator.BASE_PORT, Interpolator.BLOCK_SIZE);
    } catch (JsonSyntaxException e) {
      throw new InterpolationException("Unable to determine reserved ports information", e);
    }
  }

  /**
   * Initializes a new interpolator with all information from the provided interpolator.
   *
   * @param interpolator the interpolator to proxy
   */
  protected Interpolator(Interpolator interpolator) {
    LOGGER.debug("Interpolator({})", interpolator);
    this.testClass = interpolator.testClass;
    this.id = interpolator.id;
    this.container = interpolator.container;
    this.replacements = interpolator.replacements;
    this.ports = interpolator.ports;
  }

  /**
   * Gets the current test class.
   *
   * @return the test class
   */
  public Class<?> getTestClass() {
    return testClass;
  }

  /**
   * Gets the unique identifier for the current test run.
   *
   * @return a unique identifier for the current test run
   */
  public String getId() {
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
   * Gets the system-dependent line separator string associated with the corresponding container.
   *
   * <p>On UNIX systems, it returns <code>"\n"</code>; on Microsoft Windows systems it returns
   * <code>"\r\n"</code>.
   *
   * @return the system-dependent line separator string associated with the corresponding container
   */
  public String getLineSeparator() {
    return replacements.get("%n");
  }

  /**
   * Gets the system-dependent default name-separator character associated with the corresponding
   * container.
   *
   * @return the system-dependent default name-separator character associated with the corresponding
   *     container
   */
  public String getFileSeparator() {
    return replacements.get("/");
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
  public <A extends Annotation> A interpolate(A annotation) {
    return InterpolatedAnnotationHandler.proxyIfItNeedsInterpolation(this, annotation);
  }

  /**
   * Interpolates all static fields of type <code>String</code> or <code>String[]</code> that are
   * annotated with {@link Interpolate}.
   *
   * @param <C> the class to interpolate
   * @param clazz the class to interpolate
   * @return <code>clazz</code> for chaining
   * @throws InterpolationException if unable to interpolate the class
   * @throws ContainerNotStagedException if an interpolate expression requires the container to be
   *     staged and the container has not yet been staged (e.g. <code>"{karaf.home}"</code> when
   *     using the PaxExam Dominion driver requires the container to be first staged before it can
   *     be interpolated)
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
   * @throws ContainerNotStagedException if an interpolate expression requires the container to be
   *     staged and the container has not yet been staged (e.g. <code>"{karaf.home}"</code> when
   *     using the PaxExam Dominion driver requires the container to be first staged before it can
   *     be interpolated)
   */
  public Field interpolate(Field field) {
    Interpolator.validate(field);
    try {
      final Class<?> type = field.getType();

      field.setAccessible(true);
      if (String.class.equals(type)) {
        field.set(null, interpolate((String) field.get(null)));
      } else if (String[].class.equals(type)) {
        field.set(null, interpolate((String[]) field.get(null)));
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
   * @throws ContainerNotStagedException if an interpolate expression requires the container to be
   *     staged and the container has not yet been staged (e.g. <code>"{karaf.home}"</code> when
   *     using the PaxExam Dominion driver requires the container to be first staged before it can
   *     be interpolated)
   */
  @Nullable
  public String[] interpolate(@Nullable String... s) {
    if (s == null) {
      return null;
    }
    return Stream.of(s).map(this::interpolate).toArray(String[]::new);
  }

  /**
   * Interpolates a string.
   *
   * @param s the string to interpolate
   * @return a new interpolated string or <code>null</code> if <code>s</code> is <code>null</code>
   * @throws InterpolationException if unable to interpolate the string
   * @throws ContainerNotStagedException if an interpolate expression requires the container to be
   *     staged and the container has not yet been staged (e.g. <code>"{karaf.home}"</code> when
   *     using the PaxExam Dominion driver requires the container to be first staged before it can
   *     be interpolated)
   */
  @Nullable
  public String interpolate(@Nullable String s) {
    return substitutor.replace(s);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Lookups a replacement for the given key which first check known replacements, then maven
   * profile properties, then system properties, then environment variables, and finally reserved
   * ports.
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
      if ((value == null) && key.startsWith("env.")) {
        value = System.getenv(key.substring(4));
      }
      if ((value == null) && key.startsWith("port.")) {
        value = Integer.toString(ports.getPort(key.substring(5)));
      }
    }
    return value;
  }

  /**
   * Saves the current interpolation information (i.e. replacement and port) to disk and get a file
   * object where the information was saved. This is the same file that should be passed to the
   * {@link #Interpolator(Class, File)} constructor inside the container to reload the information
   * and initialize the interpolator.
   *
   * @param root the root directory where to create the file
   * @return the file where the information was saved
   * @throws IOException if an I/O error occurs while saving the file
   */
  public File save(File root) throws IOException {
    final File file = new File(root, "interpolation.json");
    final Properties properties = new Properties();
    final Gson gson = new Gson();

    properties.put(Interpolator.REPLACEMENTS_KEY, gson.toJson(replacements));
    synchronized (ports) {
      properties.put(Interpolator.PORTS_KEY, gson.toJson(ports));
    }
    try (final Writer writer = new BufferedWriter((new FileWriter(file)))) {
      properties.store(writer, "Created by dominion");
    }
    return file;
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

  private void initSystemReplacements() {
    // force those to the system values
    replacements.put("/", File.separator);
    replacements.put("%n", System.lineSeparator());
  }

  /**
   * Validates the specified class from the interpolator's perspective.
   *
   * @param clazz the class to validate
   * @throws InterpolationException if the class is not properly annotated
   */
  public static void validate(Class<?> clazz) {
    ReflectionUtils.getAllAnnotationsForFieldsAnnotatedWith(clazz, Interpolate.class, true)
        .keySet()
        .forEach(Interpolator::validate);
  }

  private static void validate(Field field) {
    final int modifiers = field.getModifiers();

    if (!Modifier.isStatic(modifiers)) {
      throw new InterpolationException("Can only interpolate annotated static fields: " + field);
    } else if (Modifier.isFinal(modifiers)) {
      throw new InterpolationException("Can only interpolate non-final static fields: " + field);
    }
    try {
      final Class<?> type = field.getType();

      if (!String.class.equals(type) && !String[].class.equals(type)) {
        throw new InterpolationException(
            "Can only interpolate annotated static fields of type String or String[]: " + field);
      }
    } catch (SecurityException e) {
      throw new InterpolationException("Unable to interpolate field: " + field, e);
    }
  }

  private static Properties load(File file) {
    final Properties properties = new Properties();

    try (final Reader r = new BufferedReader(new FileReader(file))) {
      properties.load(r);
    } catch (IOException e) {
      throw new ContainerNotStagedException("Unable to read interpolation file: " + file, e);
    }
    return properties;
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
