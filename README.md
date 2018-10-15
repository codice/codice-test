# Codice Test Generic Utilities
Repository of re-usable test utilities.

### Modules
The following modules are defined:
* spock-all
* junit-extensions
* spock-extensions
* hamcrest-extensions
* mockito-extensions
* failsafe-controller

#### spock-all
Defines a single pom artifact that contains all dependencies required to develop and execute Spock specifications. Simply add the following to your pom file to be able to compile and run Spock specifications:

```
<dependency>
    <groupId>org.codice.test</groupId>
    <artifactId>spock-all</artifactId>
    <version>${codice-test.version}</version>
    <type>pom</type>
    <scope>test</scope>
</dependency>
```

where `codice-test.version` is defined as a property with the latest code-test released version.

#### junit-extensions
Defines a set of extensions that can be used when defining JUnit test cases or Spock specifications.

Among the JUnit extensions, the [Definalizer](docs/definalizer.md) defines a JUnit test runner that supports de-finalizing Java classes.

#### spock-extensions
Defines a set of [extensions](docs/spock-extensions.md) that can be used when defining Spock specifications.

#### hamcrest-extensions
Defines a set of extensions that can be used with Hamcrest.

#### mockito-extensions
Defines a set of extensions that can be used with Mockito.

#### failsafe-controller
The [Failsafe Controller](docs/failsafe-controller.md) provides a testing framework to test code that uses Failsafe (from `net.jodah.failsafe`). 

### Future iterations
Future implementations will:
* Provide CI/CD support
