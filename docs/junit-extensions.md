## JUnit Extensions

JUnit extensions are divided in new method rules, method rule annotations, and test runners.

### JUnit Method Rules

#### MethodRuleAnnotationProcessor
The [MethodRuleAnnotationProcessor](../junit-extensions/src/main/java/org/codice/junit/rules/MethodRuleAnnotationProcessor.java) provides a JUnit4 method rule that implements a similar support to Spock extensions by allowing specification of simple JUnit method rules via the meta-annotation [ExtensionMethodRuleAnnotation](../junit-extensions/src/main/java/org/codice/junit/ExtensionMethodRuleAnnotation.java).

New annotations created referencing a JUnit method rule can be used to annotate JUnit test class when the method rule should apply to all tests in that class. These anntations can also be used to annotate a particular test method if the method rule should only apply to that test method.

The order these new annotations are defined in will dictate the order they will be applied from outermost to innermost.

Method rules referenced in this manner will not retain any states from one test method to the next as they will be re-instantiated each time. The method rule class must define either a public constructor with a single argument of the same annotation type as the annotation that is annotated with the meta-annotation [ExtensionMethodRuleAnnotation](../junit-extensions/src/main/java/org/codice/junit/ExtensionMethodRuleAnnotation.java) or a public default constructor. Defining a constructor with the annotation as a parameter allows customization of the method rule using your own annotation.


```
  @RestoreSystemProperties // will apply to each test methods
  public class MyTest {
    @Rule public final MethodRuleAnnotationProcessor processor = new MethodRuleAnnotationProcessor();

    @ClearInterruptions // will only apply to this test method
    @Test
    public void testSomething() throws Exception {
    }

    @Test
    public void testSomethingElse() throws Exception {
    }
  }
```

The advantage of using the test runner [MethodRuleAnnotationRunner](../junit-extensions/src/main/java/org/codice/junit/MethodRuleAnnotationRunner.java) over the method rule [MethodRuleAnnotationProcessor](../junit-extensions/src/main/java/org/codice/junit/rules/MethodRuleAnnotationProcessor.java) is that it guarantees that all annotation-based rules will be considered outermost compared to any rules defined within the test class whereas the processor cannot guarantee that since it is at the mercy of JUnit in terms of how the rules are internally initialized.

#### RestoreSystemProperties
The [RestoreSystemProperties](../junit-extensions/src/main/java/org/codice/junit/rules/RestoreSystemProperties.java) provides a Java version of the Spock annotation which when defined as a JUnit rule will automatically reset the system properties to their initial values after each tests.
```
  public class MyTest {
    @Rule public final RestoreSystemProperties restoreProperties = new RestoreSystemProperties();

    @Test
    public void testSomething() throws Exception {
      System.setProperty("ddf.home", "some new location");
      final MyClass obj = new MyClass(some);
      
      obj.doSomething();
    }
  }
```

#### ClearInterruptions
The [ClearInterruptions](../junit-extensions/src/main/java/org/codice/junit/rules/ClearInterruptions.java) provides a Java version of the Spock annotation which when defined as a JUnit rule will clear interruption state from the current thread after testing. For example:
```
  public class MyTest {
    @Rule public final ClearInterruptions clearInterruptions = new ClearInterruptions();

    @Test
    public void testThatInterruptionAreThrownBack() throws Exception {
      final SomethingElse some = mock(SomethingElse.class);
      final MyClass obj = new MyClass(some);
      
      when(some.waitForSomething()).thenThrow(new InterruptedException("testing"));
      
      obj.doSomething();
    }
  }
```

#### MethodRuleChain
The [MethodRuleChain](../junit-extensions/src/main/java/org/codice/junit/rules/MethodRuleChain.java) provides a JUnit4 rule that can be used to create a controlled chain of method rules when the order they are processed is important.

```
  public class MyTest {
    @Rule public final MethodRuleChain chain = 
      MethodRuleChain
        .outer(new RestoreSystemProperties())
        .around(new ClearInterruptions());

    @Test
    public void testSomething() throws Exception {
      System.setProperty("ddf.home", "some new location");
      final MyClass obj = new MyClass(some);
      
      obj.doSomething();
    }
  }
```

### JUnit Method Rules

#### RestoreSystemProperties
The [RestoreSystemProperties](../junit-extensions/src/main/java/org/codice/junit/RestoreSystemProperties.java) annotation can be used in conjunction with the MethodRuleAnnotationProcessor JUnit method rule to indicate all methods of a test class or specific ones where system properties should automatically be reset to their initial values after testing.

#### ClearInterruptions
The [ClearInterruptions](../junit-extensions/src/main/java/org/codice/junit/ClearInterruptions.java) annotation can be used in conjunction with the MethodRuleAnnotationProcessor JUnit method rule to indicate all methods of a test class or specific ones where the interruption state from the current thread should automatically be cleared after testing.

### JUnit Test Runners

#### MethodRuleAnnotationProcessor
The [MethodRuleAnnotationRunner](../junit-extensions/src/main/java/org/codice/junit/MethodRuleAnnotationRunner.java) provides a JUnit4 test runner that supports annotation-based method rules similar support to Spock extensions by allowing specification of simple JUnit method rules via the meta-annotation [ExtensionMethodRuleAnnotation](../junit-extensions/src/main/java/org/codice/junit/ExtensionMethodRuleAnnotation.java).

New annotations created referencing another JUnit method rule can be used to annotate JUnit test class when the method rule should apply to all tests in that class. These annotations can also be used to annotate a particular test method if the method rule should only apply to that test method.

The order these new annotations are defined in will dictate the order they will be applied from outermost to innermost.

Method rules referenced in this manner will not retain any states from one test method to the next as they will be re-instantiated each time. The method rule class must define either a public constructor with a single argument of the same annotation type as the annotation that is annotated with the meta-annotation [ExtensionMethodRuleAnnotation](../junit-extensions/src/main/java/org/codice/junit/ExtensionMethodRuleAnnotation.java) or a public default constructor. Defining a constructor with the annotation as a parameter allows customization of the method rule using your own annotation.

```
  @RestoreSystemProperties // will apply to each test methods
  @RunWith(MethodRuleAnnotationRunner.class)
  public class MyTest {
    @ClearInterruptions // will only apply to this test method
    @Test
    public void testSomething() throws Exception {
    }

    @Test
    public void testSomethingElse() throws Exception {
    }
  }
```

Example of a method rule annotation that can be customized:

```
public MyMethodRule implements MethodRule {
  private final long timeout;
  
  public MyMethodRule(MyAnnotation annotation) {
    this.timeout = annotation.timeout();  
  }
  
  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object o) {
    ...
  }
}

@ExtensionMethodRuleAnnotation(MyMethodRule.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface MyAnnotation {
  long timeout();
}
```

The advantage of using the test runner [MethodRuleAnnotationRunner](../junit-extensions/src/main/java/org/codice/junit/MethodRuleAnnotationRunner.java) over the method rule [MethodRuleAnnotationProcessor](../junit-extensions/src/main/java/org/codice/junit/rules/MethodRuleAnnotationProcessor.java) is that it guarantees that all annotation-based rules will be considered outermost compared to any rules defined within the test class whereas the processor cannot guarantee that since it is at the mercy of JUnit in terms of how the rules are internally initialized.

#### Definalizer
The [Definalizer JUnit test runner](../junit-extensions/src/main/java/org/codice/junit/DeFinalizer.java) is designed as a generic proxy test runner for another JUnit test runner by indirectly instantiating that runner in order to add support for de-finalizing (i.e. removing the final constraint) 3rd party Java classes that need to be mocked or stubbed during testing. 
It does so by creating a classloader designed with an aggressive strategy where it will load all classes first before delegating to its parent. This classloader will therefore reload all classes while definalizing those that are requested except for all classes in the following packages:
* java
* javax
* sun
* org.xml
* org.junit

These packages are not being reloaded as they are required for this test runner to delegate to the real test runner. Even the actual test class will be reloaded in this internal classloader.

The indirect extension is done by means of delegation as the real test runner is instantiated from within the classloader that is created internally. 
This is to ensure that everything the test class, the test runner, and everything they indirectly reference are loaded from within the classloader.

This runner is especially useful with Spock where it is not possible to mock final methods as can be done with Mockito.

The [`@Definalize`](../junit-extensions/src/main/java/org/codice/junit/Definalize.java) annotation should be added to the test class to specify which classes and/or packages to definalize.

The [`@DefinalizeWith`](../junit-extensions/src/main/java/org/codice/junit/DefinalizeWith.java) annotation can be added to specify which actual test runner should be used to run the test case. By default, it will either use the standard JUnit or Sputnik test runner.

#### Examples
```
  @RunWith(DeFinalizer)
  @DeFinalize(SolrCore)
  class EmbeddedSolrFactorySpec extends Specification {
    ...
  }
```
The above test specification replaces the test runner with this new Definalizer test runner which will internally instantiate the actual Sputnik test runner and delegate to it. Then the class is annotated with DeFinalize to list the classes that should be manipulated to remove all final keywords from these classes and their methods,  One can also specify packages to be definalized instead of classes if needed but typically, just specifying the classes directly is enough.
 That's it!
 The Definalizer is not just useful for Spock as it can actually wrap around any test runners. By default it will either internally instantiate Sputnik or the standard JUnit test runner based on the test class it is associated with. However, one could use the DeFinalizeWith annotation to specify your own test runner to be internally instantiated. For example:
```
  @RunWith(DeFinalizer.class)
  @DefinalizeWith(Parameterized.class)
  @DeFinalize(packages="org.xml.sax")
  public class MyParameterizedTest {
    ...
  }
```
The above test class will run the Parameterized test runner normally and definalize all classes loaded from the `org.xml.sax` package.
