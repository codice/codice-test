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
package org.codice.test.commons

import spock.lang.Specification
import spock.lang.Unroll

import java.lang.annotation.Inherited
import java.lang.annotation.Repeatable
import java.lang.annotation.Retention
import java.lang.annotation.Target
import java.util.stream.Collectors

class ReflectionUtilsSpec extends Specification {
  @Unroll
  def "test annotationsByType() finds recursively from #from_what with no filters"() {
    when:
      def entries = ReflectionUtils.annotationsByType(e, OptionAnnotation).collect(Collectors.toList())

    then:
      entries.size() == 6

      println entries[0]
      entries[0].metaAnnotation
      entries[0].isInstanceOf(OptionAnnotation)
      entries[0].annotation.value() == "OA5"
      entries[0].enclosingAnnotation.isInstanceOf(Option1)
      entries[0].enclosingAnnotation.enclosingAnnotation.isInstanceOf(CompoundOption)
      entries[0].enclosingAnnotation.enclosingAnnotation.enclosingAnnotation == null
      entries[0].annotatedElement == e
      entries[0].annotations().map({
        it.annotation.annotationType()
      }).collect(Collectors.toList()) == [Target, Retention, Inherited, Repeatable]
      entries[0].enclosedAnnotations().filter({ it.enclosingAnnotation == entries[0] }).count() == 4

      println entries[1]
      entries[1].metaAnnotation
      entries[1].isInstanceOf(OptionAnnotation)
      entries[1].annotation.value() == "OA1"
      entries[1].enclosingAnnotation.isInstanceOf(OptionA)
      entries[1].enclosingAnnotation.enclosingAnnotation.isInstanceOf(Option1)
      entries[1].enclosingAnnotation.enclosingAnnotation.enclosingAnnotation.isInstanceOf(CompoundOption)
      entries[1].annotatedElement == e
      entries[1].annotations().map({
        it.annotation.annotationType()
      }).collect(Collectors.toList()) == [Target, Retention, Inherited, Repeatable]
      entries[1].enclosedAnnotations().filter({ it.enclosingAnnotation == entries[1] }).count() == 4

      println entries[2]
      entries[2].metaAnnotation
      entries[2].isInstanceOf(OptionAnnotation)
      entries[2].annotation.value() == "OA1.1"
      entries[2].enclosingAnnotation.isInstanceOf(OptionA)
      entries[2].enclosingAnnotation.enclosingAnnotation.isInstanceOf(Option1)
      entries[2].enclosingAnnotation.enclosingAnnotation.enclosingAnnotation.isInstanceOf(CompoundOption)
      entries[2].annotatedElement == e
      entries[2].annotations().map({
        it.annotation.annotationType()
      }).collect(Collectors.toList()) == [Target, Retention, Inherited, Repeatable]
      entries[2].enclosedAnnotations().filter({ it.enclosingAnnotation == entries[2] }).count() == 4

      println entries[3]
      entries[3].metaAnnotation
      entries[3].isInstanceOf(OptionAnnotation)
      entries[3].annotation.value() == "OA2"
      entries[3].enclosingAnnotation.isInstanceOf(OptionB)
      entries[3].enclosingAnnotation.enclosingAnnotation.isInstanceOf(Option1)
      entries[3].enclosingAnnotation.enclosingAnnotation.enclosingAnnotation.isInstanceOf(CompoundOption)
      entries[3].annotatedElement == e
      entries[3].annotations().map({
        it.annotation.annotationType()
      }).collect(Collectors.toList()) == [Target, Retention, Inherited, Repeatable]
      entries[3].enclosedAnnotations().filter({ it.enclosingAnnotation == entries[3] }).count() == 4

      println entries[4]
      entries[4].metaAnnotation
      entries[4].isInstanceOf(OptionAnnotation)
      entries[4].annotation.value() == "OA3"
      entries[4].enclosingAnnotation.isInstanceOf(OptionC)
      entries[4].enclosingAnnotation.enclosingAnnotation.isInstanceOf(Option2)
      entries[4].enclosingAnnotation.enclosingAnnotation.enclosingAnnotation.isInstanceOf(CompoundOption)
      entries[4].annotatedElement == e
      entries[4].annotations().map({
        it.annotation.annotationType()
      }).collect(Collectors.toList()) == [Target, Retention, Inherited, Repeatable]
      entries[4].enclosedAnnotations().filter({ it.enclosingAnnotation == entries[4] }).count() == 4

      println entries[5]
      entries[5].metaAnnotation
      entries[5].isInstanceOf(OptionAnnotation)
      entries[5].annotation.value() == "OA4"
      entries[5].enclosingAnnotation.isInstanceOf(OptionD)
      entries[5].enclosingAnnotation.enclosingAnnotation.isInstanceOf(CompoundOption)
      entries[5].enclosingAnnotation.enclosingAnnotation.enclosingAnnotation == null
      entries[5].annotatedElement == e
      entries[5].annotations().map({
        it.annotation.annotationType()
      }).collect(Collectors.toList()) == [Target, Retention, Inherited, Repeatable]
      entries[5].enclosedAnnotations().filter({ it.enclosingAnnotation == entries[5] }).count() == 4

    where:
      from_what  || e
      'a class'  || AClass
      'a field'  || AClass.getField('field')
      'a method' || AClass.getMethod('method')
  }

  def "test annotationsByType() finds recursively with filters"() {
    given:
      def e = AClass

    when:
      def entries = ReflectionUtils.annotationsByType({ entry ->
        entry.annotations().allMatch({ ae ->
          if (ae.isEnclosingAnInstanceOf(OptionAnnotation)) {
            // if it encloses an option annotation, then we shall not look for recursive annotated
            // conditions as we will let the deepest annotations take care of them
            return true
          }
          return ae
              .enclosingAnnotationsByType(ConditionAnnotation)
              .allMatch({ it.annotation.value() })
        })
      }, e, OptionAnnotation).collect(Collectors.toList())

    then:
      entries.size() == 1

      println entries[0]
      entries[0].metaAnnotation
      entries[0].isInstanceOf(OptionAnnotation)
      entries[0].annotation.value() == "OA4"
      entries[0].enclosingAnnotation.isInstanceOf(OptionD)
      entries[0].enclosingAnnotation.enclosingAnnotation.isInstanceOf(CompoundOption)
      entries[0].enclosingAnnotation.enclosingAnnotation.enclosingAnnotation == null
      entries[0].annotatedElement == e
      entries[0].annotations().map({
        it.annotation.annotationType()
      }).collect(Collectors.toList()) == [Target, Retention, Inherited, Repeatable]
      entries[0].enclosedAnnotations().filter({ it.enclosingAnnotation == entries[0] }).count() == 4
  }
}

