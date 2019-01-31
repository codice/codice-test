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
package org.codice.test.commons;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;

/** Sets of reflection useful functions. */
public class ReflectionUtils {
  // cache of all expanded annotations for a given annotated element
  private static final Map<AnnotatedElement, Annotation[]> annotations = new IdentityHashMap<>();

  // cache of the value() method for retrieving repeatable annotations for a given container
  // annotation type
  private static final Map<Class<? extends Annotation>, Method> containerValueMethod =
      new IdentityHashMap<>();

  // fake method to indicate that no container annotation value() method is defined
  private static final Method NOT_DEFINED;

  static {
    try {
      NOT_DEFINED = Object.class.getMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Gets the annotations that are <em>associated</em> with the specified element and the meta
   * annotations recursively contained in annotations that are <em>associated</em> with the
   * specified element.
   *
   * @param <A> the type of annotation to find
   * @param element the annotated element to find all annotations and meta annotations for
   * @param clazz the type of annotations to find
   * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
   *     specified type found in the order they are defined
   */
  public static <A extends Annotation> Stream<AnnotationEntry<A>> annotationsByType(
      AnnotatedElement element, Class<A> clazz) {
    final LinkedList<Map<Annotation, Annotation>> stack = new LinkedList<>();

    stack.push(new IdentityHashMap<>());
    return ReflectionUtils.annotations(element)
        .map(a -> ReflectionUtils.annotationEntry(stack, element, null, a))
        .filter(Objects::nonNull)
        .flatMap(AnnotationEntry::thisAndEnclosedAnnotations)
        .filter(ae -> ae.isInstanceOf(clazz))
        .map(AnnotationEntry.class::<A>cast);
  }

  /**
   * Gets the annotations that are <em>associated</em> with the specified element and the meta
   * annotations recursively contained in annotations that are <em>associated</em> with the
   * specified element while applying a filter at each level of the hierarchy before extracting all
   * annotations of the specified type.
   *
   * @param <A> the type of annotation to find
   * @param filter a filter to apply to an annotation element before deciding to include and recurse
   *     through it
   * @param element the annotated element to find all annotations and meta annotations for
   * @param clazz the type of annotations to find
   * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
   *     specified type found in the order they are defined
   */
  public static <A extends Annotation> Stream<AnnotationEntry<A>> annotationsByType(
      Predicate<AnnotationEntry<?>> filter, AnnotatedElement element, Class<A> clazz) {
    final LinkedList<Map<Annotation, Annotation>> stack = new LinkedList<>();
    final Set<AnnotationEntry<?>> filtered = new HashSet<>();

    stack.push(new IdentityHashMap<>());
    // the stream we get back is ordered from top annotation to deepest annotation. As such, if an
    // annotation doesn't pass the filter, we can therefore remove all its contained annotations
    // recursively safely. we must apply the filter before checking for instance of
    return ReflectionUtils.annotations(element)
        .map(a -> ReflectionUtils.annotationEntry(stack, element, null, a))
        .filter(Objects::nonNull)
        .flatMap(AnnotationEntry::thisAndEnclosedAnnotations)
        .filter(ae -> ae.filter(filter, filtered))
        .filter(ae -> ae.isInstanceOf(clazz))
        .map(AnnotationEntry.class::<A>cast);
  }

  /**
   * Gets the annotations that are <em>associated</em> with the specified element and the meta
   * annotations recursively contained in annotations that are <em>associated</em> with the
   * specified element.
   *
   * @param element the annotated element to find all annotations and meta annotations for
   * @param classes the types of annotations to find
   * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
   *     specified types found in the order they are defined
   */
  public static Stream<AnnotationEntry<?>> annotationsByTypes(
      AnnotatedElement element, Class<? extends Annotation>... classes) {
    final LinkedList<Map<Annotation, Annotation>> stack = new LinkedList<>();

    stack.push(new IdentityHashMap<>());
    // the stream we get back is ordered from top annotation to deepest annotation. As such, if an
    // annotation doesn't pass the filter, we can therefore remove all its contained annotations
    // recursively safely. we must apply the filter before checking for instance of
    return ReflectionUtils.annotations(element)
        .map(a -> ReflectionUtils.annotationEntry(stack, element, null, a))
        .filter(Objects::nonNull)
        .flatMap(AnnotationEntry::thisAndEnclosedAnnotations)
        .filter(ae -> ae.isInstanceOf(classes));
  }

  /**
   * Gets the annotations that are <em>associated</em> with the specified element and the meta
   * annotations recursively contained in annotations that are <em>associated</em> with the
   * specified element while applying a filter at each level of the hierarchy before extracting the
   * all annotations of the specified types.
   *
   * @param filter a filter to apply to an annotation element before deciding to include and recurse
   *     through it
   * @param element the annotated element to find all annotations and meta annotations for
   * @param classes the types of annotations to find
   * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
   *     specified types found in the order they are defined
   */
  public static Stream<AnnotationEntry<?>> annotationsByTypes(
      Predicate<AnnotationEntry<?>> filter,
      AnnotatedElement element,
      Class<? extends Annotation>... classes) {
    final LinkedList<Map<Annotation, Annotation>> stack = new LinkedList<>();
    final Set<AnnotationEntry<?>> filtered = new HashSet<>();

    stack.push(new IdentityHashMap<>());
    // the stream we get back is ordered from top annotation to deepest annotation. As such, if an
    // annotation doesn't pass the filter, we can therefore remove all its contained annotations
    // recursively
    // safely. we must apply the filter before checking for instance of
    return ReflectionUtils.annotations(element)
        .map(a -> ReflectionUtils.annotationEntry(stack, element, null, a))
        .filter(Objects::nonNull)
        .flatMap(AnnotationEntry::thisAndEnclosedAnnotations)
        .filter(ae -> ae.filter(filter, filtered))
        .filter(ae -> ae.isInstanceOf(classes));
  }

  /**
   * Gets the annotations that are <em>associated</em> with the specified elements.
   *
   * @param <A> the type of annotation to search for
   * @param annotation the annotation to find
   * @param elements the annotated elements to find all annotations for
   * @return a stream of all annotations of the specified type found on all specified elements
   */
  public static <A extends Annotation> Stream<A> annotationsByType(
      Class<A> annotation, AnnotatedElement... elements) {
    return Stream.of(elements).map(e -> e.getAnnotationsByType(annotation)).flatMap(Stream::of);
  }

  /**
   * Gets all members of a given type annotated with the specified annotation.
   *
   * @param <T> the type of members to retrieve (either {@link Field}, {@link Method}, or {@link
   *     Constructor})
   * @param <A> the type of annotation to search for
   * @param type the type of members to retrieve
   * @param clazz the class from which to find all annotated members
   * @param annotation the annotation for which to find all members
   * @param up <code>true</code> to look up the class hierarchy; <code>false</code> to only look at
   *     the specified class level
   * @return a non-<code>null</code> map in the provided order for all annotated members with their
   *     found annotations
   * @throws IllegalArgumentException if <code>type</code> is not {@link Field}, {@link Method}, or
   *     {@link Constructor}
   */
  public static <T extends Member, A extends Annotation>
      Map<T, A[]> getAllAnnotationsForMembersAnnotatedWith(
          Class<T> type, Class<?> clazz, Class<A> annotation, boolean up) {
    final LinkedList<Class<?>> classes = new LinkedList<>();
    Class<?> currentClass = clazz;

    if (up) {
      while (currentClass != null) {
        classes.push(currentClass);
        currentClass = currentClass.getSuperclass();
      }
    } else {
      classes.push(currentClass);
    }
    final Map<T, A[]> members = new LinkedHashMap<>(12);

    while (!classes.isEmpty()) {
      currentClass = classes.pop();
      for (final T m : ReflectionUtils.getDeclaredMembers(type, currentClass)) {
        if (m instanceof AnnotatedElement) {
          final A[] as = ((AnnotatedElement) m).getAnnotationsByType(annotation);

          if (as.length > 0) {
            members.put(m, as);
          }
        }
      }
    }
    return members;
  }

  /**
   * Gets all methods annotated with the specified annotation.
   *
   * @param <A> the type of annotation to search for
   * @param clazz the class from which to find all annotated methods
   * @param annotation the annotation for which to find all methods
   * @param up <code>true</code> to look up the class hierarchy; <code>false</code> to only look at
   *     the specified class level
   * @return a non-<code>null</code> map in the provided order for all annotated methods with their
   *     found annotations
   */
  public static <A extends Annotation> Map<Method, A[]> getAllAnnotationsForMethodsAnnotatedWith(
      Class<?> clazz, Class<A> annotation, boolean up) {
    return ReflectionUtils.getAllAnnotationsForMembersAnnotatedWith(
        Method.class, clazz, annotation, up);
  }

  /**
   * Gets all fields annotated with the specified annotation.
   *
   * @param <A> the type of annotation to search for
   * @param clazz the class from which to find all annotated fields
   * @param annotation the annotation for which to find all fields
   * @param up <code>true</code> to look up the class hierarchy; <code>false</code> to only look at
   *     the specified class level
   * @return a non-<code>null</code> map in the provided order for all annotated fields with their
   *     found annotations
   */
  public static <A extends Annotation> Map<Field, A[]> getAllAnnotationsForFieldsAnnotatedWith(
      Class<?> clazz, Class<A> annotation, boolean up) {
    return ReflectionUtils.getAllAnnotationsForMembersAnnotatedWith(
        Field.class, clazz, annotation, up);
  }

  /**
   * Gets the declared members of the given type from the specified class.
   *
   * @param <T> the type of member
   * @param type the type of members to retrieve
   * @param clazz the class from which to retrieve the members
   * @return the non-<code>null</code> members of the given type from the specified class
   * @throws IllegalArgumentException if <code>type</code> is not {@link Field}, {@link Method}, or
   *     {@link Constructor}
   */
  @SuppressWarnings("unchecked")
  public static <T extends Member> T[] getDeclaredMembers(Class<T> type, Class<?> clazz) {
    if (type == Field.class) {
      return (T[]) clazz.getDeclaredFields();
    } else if (type == Method.class) {
      return (T[]) clazz.getDeclaredMethods();
    } else if (type == Constructor.class) {
      return (T[]) clazz.getDeclaredConstructors();
    } else {
      throw new IllegalArgumentException("invalid member class: " + type.getName());
    }
  }

  /**
   * Represents an annotation or a meta annotation found.
   *
   * <p>Relies on default {@link #hashCode()} and {@link #equals(Object)} for identity check.
   *
   * @param <A> the type of annotation
   */
  public static class AnnotationEntry<A extends Annotation> {
    private final AnnotatedElement annotatedElement;
    private final A annotation;
    @Nullable private final AnnotationEntry enclosingAnnotation;
    private List<AnnotationEntry<?>> annotations;

    AnnotationEntry(
        AnnotatedElement annotatedElement, A annotation, AnnotationEntry enclosingAnnotation) {
      this.annotatedElement = annotatedElement;
      this.annotation = annotation;
      this.enclosingAnnotation = enclosingAnnotation;
    }

    /**
     * Checks if this represents a meta annotation.
     *
     * @return <code>true</code> if this represents a meta annotation; <code>false</code> otherwise
     */
    public boolean isMetaAnnotation() {
      return enclosingAnnotation != null;
    }

    /**
     * Checks if the annotation represented by this entry is an instance of the specified class of
     * annotations.
     *
     * @param clazz the class of annotation to check against
     * @return <code>true</code> if this annotation is an instance of the specified class; <code>
     *     false</code> otherwise
     */
    public boolean isInstanceOf(Class<? extends Annotation> clazz) {
      return annotation.annotationType().equals(clazz);
    }

    /**
     * Checks if the annotation represented by this entry is an instance of one of the specified
     * classes of annotations.
     *
     * @param classes the classes of annotation to check against
     * @return <code>true</code> if this annotation is an instance of one of the specified classes;
     *     <code>false</code> otherwise
     */
    public boolean isInstanceOf(Class<? extends Annotation>... classes) {
      return ArrayUtils.contains(classes, annotation.annotationType());
    }

    /**
     * Gets the element that was annotated. This will be either the direct element where the
     * annotation was found or in the case of a meta annotation, it will be the element where an
     * annotation was found that recursively included a meta annotation.
     *
     * @return the element that was annotated
     */
    public AnnotatedElement getAnnotatedElement() {
      return annotatedElement;
    }

    /**
     * Gets the annotation that was found.
     *
     * @return the annotation that was found
     */
    public A getAnnotation() {
      return annotation;
    }

    /**
     * Gets the immediate annotation where this annotation was defined if this represents a meta
     * annotation.
     *
     * @return the immediate annotation where this meta annotation was defined or <code>null</code>
     *     if this is not a meta annotation
     */
    public AnnotationEntry getEnclosingAnnotation() {
      return enclosingAnnotation;
    }

    /**
     * Gets all annotations annotated directly on this annotation.
     *
     * @return a stream of all annotations annotated directly on this annotation
     */
    public Stream<AnnotationEntry<?>> annotations() {
      return annotations.stream();
    }

    /**
     * Gets all annotations recursively enclosed in this one in the order they are defined.
     *
     * @return a stream of all annotations enclosed in this one
     */
    public Stream<AnnotationEntry<?>> enclosedAnnotations() {
      return annotations.stream().flatMap(AnnotationEntry::thisAndEnclosedAnnotations);
    }

    /**
     * Checks if at least one annotation of the specified class is defined in the enclosed
     * annotations.
     *
     * @param clazz the class of annotation to check against
     * @return <code>true</code> if at least one enclosed annotation is an instance of the specified
     *     class; <code>false</code> otherwise
     */
    public boolean isEnclosingAnInstanceOf(Class<? extends Annotation> clazz) {
      return enclosedAnnotations().anyMatch(ae -> ae.isInstanceOf(clazz));
    }

    /**
     * Gets the annotations that are <em>associated</em> with this annotation and the meta
     * annotations recursively contained in annotations that are <em>associated</em> with this
     * annotation.
     *
     * @param <A> the type of annotation to find
     * @param clazz the type of annotations to find
     * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
     *     specified type found in this annotation in the order they are defined
     */
    public <A extends Annotation> Stream<AnnotationEntry<A>> enclosingAnnotationsByType(
        Class<A> clazz) {
      return enclosedAnnotations()
          .filter(ae -> ae.isInstanceOf(clazz))
          .map(AnnotationEntry.class::<A>cast);
    }

    /**
     * Gets the annotations that are <em>associated</em> with this annotation and the meta
     * annotations recursively contained in annotations that are <em>associated</em> with this
     * annotation.
     *
     * @param classes the types of annotations to find
     * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
     *     specified types found in this annotation in the order they are defined
     */
    public Stream<AnnotationEntry<?>> enclosingAnnotationsByTypes(
        AnnotatedElement element, Class<? extends Annotation>... classes) {
      return enclosedAnnotations().filter(ae -> ae.isInstanceOf(classes));
    }

    /**
     * Gets the class associated with the element where the annotation was defined. For normal
     * annotations, this will be the annotated element (i.e. the class or the class defining the
     * annotated method, field, or constructor). For meta annotations, this will be the annotation
     * class.
     *
     * @return the class associated with the element where the annotation was defined or <code>null
     *     </code> if unable to determine
     */
    @Nullable
    public Class<?> getEnclosingElementClass() {
      if (isMetaAnnotation()) {
        return enclosingAnnotation.annotation.annotationType();
      } else if (annotatedElement instanceof Class) {
        return (Class) annotatedElement;
      } else if (annotatedElement instanceof Field) {
        return ((Field) annotatedElement).getDeclaringClass();
      } else if (annotatedElement instanceof Method) {
        return ((Method) annotatedElement).getDeclaringClass();
      } else if (annotatedElement instanceof Constructor) {
        return ((Constructor) annotatedElement).getDeclaringClass();
      }
      return null;
    }

    /**
     * Gets the classloader associated with the element where the annotation was defined. For normal
     * annotations, this will be the classloader associated with the annotated element (i.e. the
     * class or the class defining the annotated method, field, or constructor). For meta
     * annotations, this will be the classloader associated with the enclosing annotation class.
     *
     * @return the classloader associated with the element where the annotation was defined or
     *     <code>null</code> if unable to determine or if the bootstrap classloader was used to load
     *     the element
     */
    @Nullable
    public ClassLoader getEnclosingClassLoader() {
      final Class<?> clazz = getEnclosingElementClass();

      return (clazz != null) ? clazz.getClassLoader() : null;
    }

    /**
     * Gets the code source for the element where the annotation was defined. For normal
     * annotations, this will be the code source for the annotated element (i.e. the class or the
     * class defining the annotated method, field, or constructor). For meta annotations, this will
     * be the code source associated with the enclosing annotation class.
     *
     * @return the code source for the element where the annotation was defined or <code>null</code>
     *     if unable to determine or unknown
     */
    @Nullable
    public CodeSource getEnclosingCodeSource() {
      final Class<?> clazz = getEnclosingElementClass();

      return (clazz != null) ? clazz.getProtectionDomain().getCodeSource() : null;
    }

    /**
     * Gets an input stream for reading the specified resource defined where the annotation was
     * defined. For normal annotations, this will be a resource where the annotated element (i.e.
     * the class or the class defining the annotated method, field, or constructor) is defined. For
     * meta annotations, this will be a resource defined with the enclosing annotation class.
     *
     * @return an input stream for reading the resource, or <code>null</code> if the resource could
     *     not be found
     */
    @Nullable
    public InputStream getEnclosingResourceAsStream(String name) {
      // first see if we can retrieve it from the associated code source. This has the advantage
      // that for resources that have a common name which could be present in multiple jars, we will
      // retrieved the one that is closely associated with where the annotation was used as opposed
      // to the first one the classloader search algorithm finds which is based on the classpath
      // order.
      final Class<?> clazz = getEnclosingElementClass();
      final ClassLoader classloader;
      final CodeSource codesource;

      if (clazz != null) {
        classloader = clazz.getClassLoader();
        codesource = clazz.getProtectionDomain().getCodeSource();
      } else {
        classloader = null;
        codesource = null;
      }
      InputStream is = MavenUtils.getResourceAsStreamFromCodeSource(name, codesource);

      if (is == null) { // fallback to classloader
        is = AnnotationEntry.getResourceAsStreamFromClassLoader(name, classloader);
      }
      if (is == null) { // fallback to the thread context classloader
        is = AnnotationEntry.getResourceAsStreamFromThreadContextClassLoader(name);
      }
      if (is == null) { // fallback to a test resource under target
        is = AnnotationEntry.getResourceAsStreamFromFile("target/test-classes/", name);
      }
      if (is == null) { // finally fallback to a resource under target
        is = AnnotationEntry.getResourceAsStreamFromFile("target/classes/", name);
      }
      return is;
    }

    @Override
    public String toString() {
      if (isMetaAnnotation()) {
        return annotation
            + " enclosed in ["
            + enclosingAnnotation
            + "] and located in ["
            + annotatedElement
            + ']';
      }
      return annotation + " located in " + annotatedElement;
    }

    void setAnnotations(List<AnnotationEntry<?>> annotations) {
      this.annotations = annotations;
    }

    boolean filter(Predicate<AnnotationEntry<?>> filter, Set<AnnotationEntry<?>> filtered) {
      if (filtered.contains(this)) {
        return false;
      } else if (!filter.test(this)) {
        filtered(filtered);
        return false;
      }
      return true;
    }

    void filtered(Set<AnnotationEntry<?>> filtered) {
      filtered.add(this);
      annotations.forEach(ae -> ae.filtered(filtered));
    }

    private Stream<AnnotationEntry<?>> thisAndEnclosedAnnotations() {
      return Stream.concat(
          Stream.of(this),
          annotations.stream().flatMap(AnnotationEntry::thisAndEnclosedAnnotations));
    }

    @Nullable
    private static InputStream getResourceAsStreamFromClassLoader(
        String name, @Nullable ClassLoader classloader) {
      return (classloader != null)
          ? classloader.getResourceAsStream(name)
          : ClassLoader.getSystemResourceAsStream(name);
    }

    @Nullable
    private static InputStream getResourceAsStreamFromThreadContextClassLoader(String name) {
      final ClassLoader contextClassloader = Thread.currentThread().getContextClassLoader();

      if (contextClassloader == null) {
        return null;
      }
      return contextClassloader.getResourceAsStream(name);
    }

    @Nullable
    private static InputStream getResourceAsStreamFromFile(String dir, String name) {
      try {
        final File file = new File(FilenameUtils.separatorsToSystem(dir + name));

        if (file.canRead()) {
          return new BufferedInputStream(new FileInputStream(file));
        }
      } catch (IOException e) {
      }
      return null;
    }
  }

  // this method properly expands containers for repeatable annotations
  private static Stream<Annotation> annotations(AnnotatedElement element) {
    return Stream.of(
        ReflectionUtils.annotations.computeIfAbsent(element, ReflectionUtils::getAnnotations0));
  }
  // this method properly expands containers for repeatable annotations
  private static Annotation[] getAnnotations0(AnnotatedElement element) {
    return Stream.of(element.getAnnotations())
        .flatMap(ReflectionUtils::expandContainers)
        .toArray(Annotation[]::new);
  }

  @Nullable
  private static AnnotationEntry<?> annotationEntry(
      LinkedList<Map<Annotation, Annotation>> stack,
      AnnotatedElement element,
      @Nullable AnnotationEntry enclosingAnnotation,
      Annotation annotation) {
    final Map<Annotation, Annotation> visited = stack.peek();

    if (visited.putIfAbsent(annotation, annotation) != null) {
      return null;
    }
    stack.push(new IdentityHashMap<>(visited));
    try {
      final AnnotationEntry<?> entry =
          new AnnotationEntry<>(element, annotation, enclosingAnnotation);

      entry.setAnnotations(
          ReflectionUtils.annotations(annotation.annotationType())
              .map(a -> ReflectionUtils.annotationEntry(stack, element, entry, a))
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
      return entry;
    } finally {
      stack.pop();
    }
  }

  private static Method getContainerValueMethod0(Class<? extends Annotation> clazz) {
    try {
      final Method method = clazz.getMethod("value");
      final Class<?> returnType = method.getReturnType();

      if (!returnType.isArray()) {
        return ReflectionUtils.NOT_DEFINED;
      }
      final Class<?> elementType = returnType.getComponentType();

      if (!elementType.isAnnotation()) {
        return ReflectionUtils.NOT_DEFINED;
      }
      final Repeatable repeatable = elementType.getAnnotation(Repeatable.class);

      return ((repeatable != null) && clazz.equals(repeatable.value())) ? method : null;
    } catch (NoSuchMethodException e) {
      return ReflectionUtils.NOT_DEFINED;
    }
  }

  @Nullable
  private static Method getContainerValueMethod(Class<? extends Annotation> clazz) {
    final Method method =
        ReflectionUtils.containerValueMethod.computeIfAbsent(
            clazz, ReflectionUtils::getContainerValueMethod0);

    return (method != ReflectionUtils.NOT_DEFINED) ? method : null;
  }

  private static Stream<Annotation> expandContainers(Annotation annotation) {
    final Class<? extends Annotation> clazz = annotation.annotationType();
    final Method method = ReflectionUtils.getContainerValueMethod(clazz);

    if (method == null) {
      return Stream.of(annotation);
    }
    try {
      final Annotation[] annotations = (Annotation[]) method.invoke(annotation);

      return Stream.concat(
          Stream.of(annotation), Stream.of(annotations).flatMap(ReflectionUtils::expandContainers));
    } catch (IllegalArgumentException
        | InvocationTargetException
        | ClassCastException
        | IllegalAccessException e) {
      throw new AnnotationFormatError(
          annotation + " is an invalid container for repeating annotations", e);
    }
  }
}
