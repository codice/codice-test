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
package org.codice.junit

import org.junit.runner.RunWith
import org.spockframework.mock.CannotCreateMockException
import spock.lang.Specification

@RunWith(DeFinalizer)
@DeFinalize(DefinalizedClass)
class DefinalizeSpec extends Specification {
  def "test when definalized without mocking"() {
    given:
      def c = new DefinalizedClass()

    expect:
      c.string.equals('DefinalizedClass')
  }

  def "test when final without mocking"() {
    given:
      def c = new FinalClass()

    expect:
      c.string.equals('FinalClass')
  }

  def "test when definalized with mocking"() {
    given:
      def c = Mock(DefinalizedClass) {
        getString() >> 'Mocked'
      }

    expect:
      c.string.equals('Mocked')
  }

  def "test when final with mocking"() {
    when:
      def c = Mock(FinalClass) {
        getString() >> 'Mocked'
      }

    then:
      thrown(CannotCreateMockException)
  }

  def "test when definalized with groovy mocking"() {
    given:
      def c = GroovyMock(DefinalizedClass) {
        getString() >> 'Mocked'
      }

    expect:
      c.string.equals('Mocked')
  }

  def "test when final with groovy mocking"() {
    given:
      def c = GroovyMock(FinalClass) {
        getString() >> 'Mocked'
      }

    expect:
      c.string.equals('Mocked')
  }

  static final class DefinalizedClass {
    final def getString() {
      return 'DefinalizedClass';
    }
  }

  static final class FinalClass {
    final def getString() {
      return 'FinalClass';
    }
  }
}
