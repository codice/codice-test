# Codice Test Generic Utilities
Repository of re-usable test utilities.

### Modules
The following modules are defined:
* spock-shaded
* spock-extensions
* definalizer
* failsafe-controller

#### spock-shaded
Defines a single jar file that contains all dependencies required to develop and execute Spock specifications.

#### spock-extensions
Defines a set of [extensions](docs/spock-extensions.md) that can be used when defining Spock specifications.

#### definalizer
The [Definalizer](docs/definalizer.md) defines a JUnit test runner that supports de-finalizing Java classes.

#### failsafe-controller
The [Failsafe Controller](doscs/failsafe-controller.md) provides a testing framework to test code that uses Failsafe (from `net.jodah.failsafe`). 

### Future iterations
Future implementations will:
* Provide CI/CD support
