#Testing Failsafe Code

Failsafe (from `net.johah.failsafe`) is a really nice library that is typically used in code to repeatedly execute a given task until it succeeds or aborts. Although it can be used synchronously, it is often used asynchronously using an executor such that the tasks are executed by other threads.

Unit testing code designed with this library makes it really hard because of the asynchronous nature of the code. One can (and should) unit test the callbacks into your classes from this library to process Failsafe events such as `onSuccess`, `onRetry`, `onAbort`, `onFailure`, ... However, testing the asynchronous behavior is also something useful as it gets almost impossible to simulate race conditions that could occur in real life. 

Here comes a framework designed to help you do exactly that ... the **FailsafeController**.

The [FailsafeController](src/main/java/net/jodah/failsafe/FailsafeController.java) extends on Failsafe to allow deterministic testing of asynchronous code controlled by Failsafe. It is designed to compress the time aspect while allowing full control of the Failsafe behavior while testing one's code.

Failsafe is typically used in code by repeatedly executing a given task until it succeeds or aborts. For example:

```
+-----------------+ Failsafe execution +----------+
| Code under test |------------------->|          |
|                 |                    |          |
|  +------+       |    1st attempt     |          |
|  |      |<---------------------------| Failsafe |
|  | Task |       |       ...          |          |
|  |      |<<--------------------------|          |
|  |      |       |   final attempt    |          |
|  |      |<---------------------------|          |
|  +------+       |                    +----------+
+-----------------+
```

In this example, we see one execution from the code under test that results in multiple attempts to execute a specific task. The `net.jodah.failsafe.RetryPolicy` configured with Failsafe will typically dictate when a result from the task is considered successful, when it should abort the whole execution, or when another attempt should be made. It will also typically dictate how long to wait between each attempts.

Testing such code should be centered around controlling the exact sequence of events that surrounds the task such that one can simulate race conditions that otherwise would be very hard to do in real life. This would allow one to test _what if A happens before B_ and _what if A happens after B_. The task itself could be easily unit tested without regards to the asynchronous nature of this design which would then allow the tester to mock, control, or script its execution using the Failsafe controller and thus more easily control the sequence of events.

The Failsafe controller provides a very simple synchronization mechanism between the test code and what happens in Failsafe using a notion of conditions or latches that can be notified and waited upon from either the mocked task side or from the test code side based on the testing need. For example, one could have Failsafe respond in error to an attempt until such a condition is notified by the test code when it is time to have the asynchronous code return successfully. Or again the asynchronous task could notify the test code it has completed a certain stage thus unblocking the test code from continuing with additional verification.

It is also possible to have the asynchronous task either block or wait for the whole operation to be canceled and for the test code to also wait for a given execution to complete successfully or not before proceeding.

### Retry Policies
The Failsafe controller will use the provided retry policy as is except for the following exceptions:

* Time is compressed such that it doesn't wait before handling the next attempt (unless specifically mocked/scripted using wait actions)
* It will automatically abort whenever an `AssertionError` is detected such that the test would abort in failure.

### Scheduled Executor
The Failsafe controller will use the provided scheduled executor as is. It does intercept all request for execution to this executor in order to determine when a particular Failsafe execution has completed including dispatching all asynchronous events that are done using this executor.

### Injection
The production code has to be modified such that it becomes possible to inject the Failsafe controller in lieu of Failsafe. To better understand how this is done, let's look at how one normally invokes Failsafe with an example:

```
  Future<MyObject> future = Failsafe.with(myRetryPolicy)
    .with(myScheduledExecutor)
    .onRetry(this::logFailure)
    .onAbort(this::logInterruptionAndRecreate)
    .onFailure(this::logAndRecreateIfNotCancelled)
    .onSuccess(this::logAndSetCreated)
    .get(this::create);
```

In this example, the first line will create a `net.jodah.failsafe.SyncFailsafe` object. The second line will create a `net.jodah.failsafe.AsyncFailsafe` object from it and the next 4 lines registers event listeners. Additional lines could be added to further configure Failsafe if required. The final line is the one that actually triggers the start of an execution which returns a `java.util.concurrent.Future` allowing access to the result at the end and also provides the ability to cancel the execution.

The Failsafe controller is not a replacement for Failsafe but does require replacing the first line above. One way to achieve this is by defining a variable in your class that can be injected through the constructor at build time. This variable could be defined as a function like this:
```
  private final Function<RetryPolicy, SyncFailsafe<MyObject>> createFailsafeCreator;
```
With a package private constructor like this:
```
  MyClass(Function<RetryPolicy, SyncFailsafe<MyObject>> createFailsafeCreator) {
    this.createFailsafeCreator = createFailsafeCreator;
  }
```
The normal constructor could simply call that constructor like this:
```
 this(Failsafe::with)
```
From this point the example above would be re-written as:
```
  Future<MyObject> future = createFailsafeCreator.apply(myRetryPolicy)
    .with(myScheduledExecutor)
    .onRetry(this::logFailure)
    .onAbort(this::logInterruptionAndRecreate)
    .onFailure(this::logAndRecreateIfNotCancelled)
    .onSuccess(this::logAndSetCreated)
    .get(this::create);
```
While testing, the constructor would be called as follow:
```
  FailsafeController controller = new FailsafeController("some name");

  MyClass mine = new MyClass(controller::with);
```
From this point on, the controller instance becomes your point of contact to control, mock, or script how Failsafe will respond to each executions and attempts.

### Actions
Each time Failsafe makes an attempt, the controller intercepts the call to the task specified (see 7th line in the above example) and figures out what to do. This could be:

* return something
* throw something
* do nothing
* proceed to invoke the real task
* simulate an interruption
* notify a condition
* wait for a condition
* wait for the execution to be cancelled by the code under test via the returned future

Some of these actions can be further customized to be conditional, to repeat for a certain number of attempts, to repeat for multiple attempts until a certain condition occurs or until the execution is cancelled, or again can be delayed. Some actions will terminate an attempt like those that returns or throw something and others will just be waiting for something before moving on to the next action before responding to an attempt.

| Action                          | Description | Cardinality (invoked for how many attempts)| Terminates the attempt (doesn't call the production tasks) | Short-circuiting |
| ------------------------------- | --- | --- | --- | --- |
| Proceed                         | Proceeds to invoke the real tasks that was specified by the production code when the execution was triggered and returns the result returned by the task or again throws back any exception that is thrown out of the task | 1 | Yes | No |
| Nothing or Return with no value | Simulates a task that returns nothing (useful when invoking void tasks (e.g. `java.lang.Runnable` as these do not return anything) | 1 | Yes | Yes |
| Return                          | Simulates a task that returns a specific value. | 0, 1 or many depending on how many different values the action was configured with (one per attempt) | Yes if there are any configured values left to be returned<p>No if no values were configured | Yes |
| Throw                           | Simulates a task that throws a specific exception. Exceptions can be configured with actual objects or with class names. In the later case, an exception is instantiated using:<p><ol><li>a constructor that accepts a string message; or <li>a constructor that accepts a string message and a cause exception; or <li>a constructor that accepts a cause exception; or <li>a default constructor</ol>No matter how the exception is created (passed in or instantiated from a class name), its stack trace is filled in at the time it is re-thrown | 0, 1 or many depending on how many different exceptions the action was configured with (one per attempt) | Yes if there are any configured exceptions left to be thrown<p>No if no exceptions were configured | Yes |
| Throw or Return                 | Simulates a task that returns a specific value or throws a specific exception. Exceptions can be configured with actual objects or with class names. In the later case, an exception is instantiated using:<p><ol><li>a constructor that accepts a string message; or <li>a constructor that accepts a string message and a cause exception; or <li>a constructor that accepts a cause exception; or <li>a default constructor</ol>No matter how the exception is created (passed in or instantiated from a class name), its stack trace is filled in at the time it is re-thrown. Values and exceptions can be intermix. Values that are instance of `java.lang.Throwable` and classes that extends `java.lang.Throwable` will be instantiated and thrown out | 0, 1 or many depending on how many different values/exceptions the action was configured with (one per attempt) | Yes if there are any configured values/exceptions left to be returned or thrown<p>No if no values/exceptions were configured | Yes |
| Interrupt                       | Simulate a thread interruption by both raising the interrupted flag of the current thread and throwing back an `java.lang.InterruptedException` | 1 | Yes | Yes |
| Notify                          | Notifies or raises a particular condition in order to wake up or trigger code that might be waiting for this condition | 1 | No | Yes |
| Wait                            | Blocks the attempt and waits for a particular condition to be notified or raised if not already notified. | 1 | No | Yes |
| Wait to be Cancelled            | Blocks the attempt and waits for the execution to be cancelled via the returned `java.util.concurrent.Future` from Failsafe if not already cancelled. | 1 | No | Yes |

| Customization   | Description | New Cardinality of the Customized Action |
| --------------- | --- | --- |
| Only If         | Conditionally invokes the customized action based on a static boolean value provided at the time the action is constructed or based on the evaluation of a specified predicate at the time an attempt is made. if the result is false, it will move on to the next recorded action to respond to the attempt instead of invoking the customized action | Changes to 0 if the condition evaluates to false otherwise no change |
| Times           | Invokes the customized action for a specified number of attempts. If the number of attempts configured is 0, the customized action is skipped and it will move on to the next recorded action to respond to the attempt | The new cardinality is based on the specified number of times to repeat |
| Until Cancelled	| Invokes the customized action for each attempts until such time the execution is cancelled via the returned `java.util.concurrent.Future` from Failsafe. It will invoke the customized action if the execution has not been cancelled yet otherwise it will move on to the next action to respond to the attempt | 0 or more based on when the execution is cancelled | 
| Until Notified	| Invokes the customized action for each attempts until such time the specified condition is notified or raised. It will invoke the customized action if the condition has not been notified yet otherwise it will move on to the next action to respond to the attempt | 0 or more based on when the condition is notified |
| Delayed         | Blocks the attempt and waits for a specified amount of time before proceeding with the customized action to respond to an attempt. This form of customization should be used lightly and avoided if at all possible as it introduces delays in test cases | No changes |
| Never           | Does not invoke the customized action but instead moves on to the next recorded action to respond to the attempt | It becomes 0 |
| Forever         | Repeats the customized action for every subsequent attempts of a given execution | Will repeat forever |

Actions are recorded by starting a sequence using one of the static methods in the `net.jodah.failsafe.Actions` class and chaining them together. Each of the resulting sequence can
then be recorded for a particular execution. For example:
```
  Actions.waitTo("connect").before().returning(true);
```
In this example, the first Failsafe attempt for the execution would be blocked until some other part of the code decides to notify the "connect" condition and return true as a result of that attempt.
```
  Actions.doThrow(NullPointerException)
    .then().waitTo("connect again").before().returning(false)
    .then().doReturn(true);
```
In this example, the first Failsafe attempt for the next execution would result in a java.lang.NullPointerException being thrown back. The expectation is that a second attempt would be made at which point it would block until some other part of the code decides to notify the "connect again" at which point it would return false. When a third attempt is made by Failsafe, true would then be returned and no more attempts would be expected for this execution.

The above example can be written in Spock as:
```
 doThrow(NullPointerException)
    .then().waitTo("connect again").before().returning(false)
    .then().doReturn(true)
```
or with more Groovy flair as:
```
 doThrow(NullPointerException) + waitTo("connect again").before().returning(false) + doReturn(true)
```
### Execution
As indicated above, an execution encompass the point where Failsafe is called using one of the methods where a task is provided to Failsafe. The execution is completed when the `java.util.concurrent.Future` for asynchronous tasks is completed and can provide the result for the task or the error that occurred or again when Failsafe return with a result or an exception for synchronous tasks.  Whenever an execution starts, the Failsafe controller will retrieve the sequence of actions associated with the next recorded execution. An execution is recorded with the controller using one of the `net.jodah.failsafe.FailsafeController.onNextExecution(net.jodah.failsafe.Actions.Done)` method. For Groovy developers, it is also possible to use a closure that returns the sequence of actions using the `net.jodah.failsafe.FailsafeController.onNextExecution(groovy.lang.Closure)` method. No matter which way you do it, the sequence of actions will be defined as indicated in the previous section.

Each and every expected executions must be recorded and each and every expected attempts must be accounted for with actions otherwise the Failsafe controller will generate an `java.lang.AssertionError`.

Examples of how to record expected executions:
```
  def pingController = new FailsafeController('SolrClient Ping')
    .onNextExecution(doNothing())
    .and().onNextExecution {
      doThrow(pingError)
      .then().doThrow(new SolrException(ErrorCode.UNKNOWN, 'failed').untilNotifiedTo('connect')
      .then().doNothing()
  }
```
In the above example, two expected executions are recorded with a Failsafe controller used to perform ping connections to a Solr server. For the first execution, only one attempt is expected which will result in ding nothing (here we are assuming the task is a void one). On the second execution, an exception will be thrown if the condition 'connect' has not been notified. This will continue to happen for all subsequent attempts until such time where the condition is notified. When that happens, the attempt will be handled using the doNothing() action which is interpreted as a successful connection by the code under test.

Syntax sugar is provided for Spock users to make the mocking look more like Spock's mocking. For example, the above could be re-written as follow:
```
  def pingController = new FailsafeController('SolrClient Ping') >>> [ 
    doNothing(), 
    doThrow(pingError) + doThrow(new SolrException(ErrorCode.UNKNOWN, 'failed').untilNotifiedTo('connect') + doNothing()
  ]
```
or again as:
```
  def pingController = new FailsafeController('SolrClient Ping') >> doNothing() >> doThrow(pingError) + doThrow(new SolrException(ErrorCode.UNKNOWN, 'failed').untilNotifiedTo('connect') + doNothing(
```
### Conditions
The Failsafe controller maintains a simple set of conditions that have been notified since it was created. Each condition is simply identified using a unique name. As explained before, it provides a very simple latching mechanism between Failsafe's execution attempts and the main test logic.

Conditions can be notified via the _Notify_ action when responding to Failsafe attempts or via the `net.jodah.failsafe.FailsafeController.notify(java.lang.String)` or `net.jodah.failsafe.FailsafeController.notifyTo(java.lang.String)` methods on the controller by the test code. To wait on a condition, simply use the _Wait_ action, customized an action with _Until Notified_ or again call one of the two `net.jodah.failsafe.FailsafeController.waitFor(java.lang.String)` or `net.jodah.failsafe.FailsafeController.waitTo(java.lang.String)` methods on the controller.

### Verification
Once a test is completed, the controllers in play should be verified to see if any errors occurred or again if all recorded expected executions and actions have been processed. This can be done via the `net.jodah.failsafe.FailsafeController.verify()` method.

### Timing, delays, and blockages
To simplify the design of the controller all waits that are designed to block execution until something happens do not expect a timeout to be specified. This is primarily due to the fact that time should be removed from the equation when we are performing asynchronous testing such that the tests ends up being deterministic. This is also why the Failsafe controller automatically compress time for the specified retry policy. Because of this and because the code under test could also be buggy and not behave as expected, the test might actually block. It is therefore the responsibility of the test writer to take advantage of the test framework capability to associate a timeout to a given test such that when a condition like this occurs, the test is aborted and cleaned up which is where the Failsafe controller could be shutdown to unblock all places where it might be waiting for something to happen.

In the best case scenario, your test cases should be quick and not delay (except if using the _Delayed_ customization). In the worst case scenario when a test case is failing, it might actually end up blocking until such configured timeout with your test runner.

### Shutting Down
Each created Failsafe controller should be shutdown when cleaning up a test case to ensure that all threads involved are properly released and that we stop waiting on something to happen within the controller. The Failsafe controller was designed to support thread interruption to stop doing what it is doing as soon as requested.