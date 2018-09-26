# Definalizer
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