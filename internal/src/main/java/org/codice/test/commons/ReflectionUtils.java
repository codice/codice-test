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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang.ArrayUtils;

/** Sets of reflection useful functions. */
public class ReflectionUtils {
  /**
   * Gets the annotations that are <em>associated</em> with the specified element and the meta
   * annotations recursively contained in annotations that are <em>associated</em> with the
   * specified element.
   *
   * @param <A> the type of annotation to find
   * @param element the annotated element to find all annotations and meta annotations for
   * @param annotation the type of annotations to find
   * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
   *     specified types found in the order they are defined
   */
  public static <A extends Annotation> Stream<AnnotationEntry<A>> annotationsByType(
      AnnotatedElement element, Class<A> annotation) {
    final Map<Annotation, Annotation> visited = new IdentityHashMap<>();

    return Stream.of(element.getAnnotations())
        .flatMap(a -> ReflectionUtils.annotationsByTypes(visited, element, null, a))
        .filter(ae -> ae.instanceOf(annotation))
        .map(AnnotationEntry.class::<A>cast);
  }

  /**
   * Gets the annotations that are <em>associated</em> with the specified element and the meta
   * annotations recursively contained in annotations that are <em>associated</em> with the
   * specified element.
   *
   * @param element the annotated element to find all annotations and meta annotations for
   * @param annotations the types of annotations to find
   * @return a stream of all annotation entries (i.e. annotations and meta annotations) of the
   *     specified types found in the order they are defined
   */
  public static Stream<AnnotationEntry<?>> annotationsByTypes(
      AnnotatedElement element, Class<? extends Annotation>... annotations) {
    final Map<Annotation, Annotation> visited = new IdentityHashMap<>();

    return Stream.of(element.getAnnotations())
        .flatMap(a -> ReflectionUtils.annotationsByTypes(visited, element, null, a))
        .filter(ae -> ae.instanceOf(annotations));
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
   * Gets all methods of a given type annotated with the specified annotation.
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
   * Gets the declared members of the given type from the specified class.
   *
   * @author paouelle
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
   * @param <A> the type of annotation
   */
  public static class AnnotationEntry<A extends Annotation> {
    private final AnnotatedElement annotatedElement;
    @Nullable private final Annotation enclosingAnnotation;
    private final A annotation;

    AnnotationEntry(
        AnnotatedElement annotatedElement, Annotation enclosingAnnotation, A annotation) {
      this.annotatedElement = annotatedElement;
      this.enclosingAnnotation = enclosingAnnotation;
      this.annotation = annotation;
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
     * Gets the immediate annotation where this annotation was found if this represents a meta
     * annotation.
     *
     * @return the immediate annotation where this meta annotation was found or <code>null</code> if
     *     this is not a meta annotation
     */
    @Nullable
    public Annotation getEnclosingAnnotation() {
      return enclosingAnnotation;
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
     * Gets the classloader associated with the element where the annotation was defined. For normal
     * annotations, this will be the classloader associated with the annotated element (i.e. the
     * class or the class defining the annotated method, field, or constructor). For meta
     * annotations, this will be the classloader associated with the enclosing annotation class.
     *
     * @return the classloader associated with the element where the annotation was defined or
     *     <code>null</code> if unable to determine
     */
    @Nullable
    public ClassLoader getClassLoader() {
      final Class<?> clazz;

      if (isMetaAnnotation()) {
        clazz = enclosingAnnotation.annotationType();
      } else if (annotatedElement instanceof Class) {
        clazz = (Class) annotatedElement;
      } else if (annotatedElement instanceof Field) {
        clazz = ((Field) annotatedElement).getDeclaringClass();
      } else if (annotatedElement instanceof Method) {
        clazz = ((Method) annotatedElement).getDeclaringClass();
      } else if (annotatedElement instanceof Constructor) {
        clazz = ((Constructor) annotatedElement).getDeclaringClass();
      } else {
        return null;
      }
      return clazz.getClassLoader();
    }

    @Override
    public String toString() {
      if (isMetaAnnotation()) {
        return annotation
            + " enclosed in "
            + enclosingAnnotation
            + " and located in "
            + annotatedElement;
      }
      return annotation + " located in " + annotatedElement;
    }

    boolean instanceOf(Class<? extends Annotation> annotation) {
      return this.annotation.annotationType().equals(annotation);
    }

    boolean instanceOf(Class<? extends Annotation>... annotations) {
      return ArrayUtils.contains(annotations, annotation.annotationType());
    }
  }

  private static Stream<AnnotationEntry<?>> annotationsByTypes(
      Map<Annotation, Annotation> visited,
      AnnotatedElement element,
      @Nullable Annotation enclosingAnnotation,
      Annotation annotation) {
    if (visited.putIfAbsent(annotation, annotation) != null) {
      return Stream.empty();
    }
    return Stream.concat(
        Stream.of(new AnnotationEntry<>(element, enclosingAnnotation, annotation)),
        Stream.of(annotation.annotationType().getAnnotations())
            .flatMap(a -> ReflectionUtils.annotationsByTypes(visited, element, annotation, a)));
  }
}
