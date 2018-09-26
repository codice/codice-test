#Spock Extensions

#### @ClearInterruptions
The [`@ClearInterruptions`](../spock-extensions/src/main/groovy/org/codice/spock/ClearInterruptions.groovy) annotation can be used to clear interruption states from the current thread after testing. For example:
```
 class MyClassSpec extends Specification {
    @ClearInterruptions
    def 'test that interruptions are thrown back'() {
      given:
        def some = Mock(SomethingElse)
        def obj = new MyClass(some)

      when:
        def interrupted = obj.doSomething()

      then:
        1 * some.waitForSomething() >> { throw new InterruptedException('testing') }

      and:
        interrupted
    }
  }
```

#### @Supplemental 
The [`@Supplemental`](../spock-extensions/src/main/groovy/org/codice/spock/Supplemental.groovy) annotation can be added to any Spock specification class in order to get additional methods added to specific classes while testing specifications.

The following methods are added:

| Class                            | Method | Description |
| -------------------------------- | --- | --- |
| `java.lang.Class`                | `Method[] getApiMethods()` | Gets all API methods (inherited or not) for the given class filtering away all non public methods and all methods defined by the Object class (e.g. `Object.equals()`, `Object.toString()`, `Object.clone()` ...). |
|                                  | `Method[] getProxyableMethods()` | Gets all public proxy-able methods (inherited or not) for the class filtering away all final methods and all methods defined by the Object class (e.g. `Object.equals()`, `Object.toString()`, `Object.clone()` ...). |
|                                  | `Method getMethodBySimplePrototype(String prototype) throws NoSuchMethodException` | Returns a `Method` object that reflects the specified public member method of the underlying class or interface that matches the given simple prototype (e.g. `Object.class.getMethodBySimplePrototype('equals(Object)'))`. A simple prototype is the name of the method with the simple name of all parameter types separated by a comma in between parentheses |
|                                  | `String getNoSpockSimpleName()`| Returns the simple name of the underlying class as given in the source code stripping away any references to Spock mocks, stubs, or spies. |
| `java.lang.reflect.Method`       | `String getSimplePrototype()` | Gets a simple prototype string to represent the method. |
|                                  | `void verifyInvocation(MockInvocation delegate, Object... parameters)` | Asserts if the mock invocation matches the specified method. Only the method name and parameter types/values are verified. This method is meant to be invoked from within a closure associated with a stubbed interaction by passing it a reference to the closure's delegate and the expected parameters used when calling the method. Parameters are verified using identity check and not equality. No verification of parameters will occur if no expected parameters are specified. |
| `java.lang.System`               | `static String setPropertyIfNotNull(String name, String value)` | Sets the system property indicated by the specified key only if the specified value is not `null` otherwise remove any mappings to the specified key and returns the previous value or `null` if it did not have one |
| `spock.lang.Specification extends spock.mock.MockingApi` | `<T> T Dummy(Class<T> type)` | Creates a dummy value or stub for the specified type. It returns default values for primitive data types and their wrappers, `null` for `Void`, empty string for `String` and `GString`, empty buffer for `StringBuilder` and `StringBuffer`, corresponding empty collections for `Iterable`, `Collection`, `List`, `Set`, `SortedSet`, `NavigatableSet`, `Map`, `SortedMap`, `NavigatableMap`, `Optional.empty()` for `Optional`, 0 for `BigDecimal` and `BigInteger`, empty arrays for arrays, first enum for enumerations. For anything else, it will try to instantiate the type using its default constructor and if that fails, it returns a Spock stub. |
|                                  | `Object[] Dummies(Class<?>... types)` | Creates dummy values or stubs for the specified types. |
