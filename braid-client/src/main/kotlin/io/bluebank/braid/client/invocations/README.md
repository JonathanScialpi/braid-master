# Package io.bluebank.braid.client.invocations

This package abstracts and implements the mechanics for invoking the braid server, handling requests
via all the different ways that Braid supports, and provides error reporting and log tracing.

## 1. Design Considerations

Braid supports a rich set of function signatures that it can invoke:

* Blocking calls
* Calls returning Futures
* Calls returning rx-java Observables

Each of these has its own discrete set of concerns.

### 1.1. Blocking calls 

* invoke immediately
* must not block the eventloops used by vertx/netty
* must receive at most one result from server, or an exception
* they must return the result, or throw an exception, either relevant for the function call, or for transport failure.

### 1.2. Future calls 

* invoke immediately
* must not block the eventloops used by vertx/netty
* must receive at most one result from server, or an exception
* must return a valid future which will complete at some point in the future.

### 1.3. Observable calls

* only invoke when subscribed
* invoke once per subscription
* can be subscribed to multiple times!
* may receive zero or more items before completion or failure

## 2. High level actors

The principle classes are:

* **[Invocations](Invocations.kt)** - this is the public class, responsible for:
  * managing socket connections
  * tracking invocations
  * dispatching the meat of the invocation and response handling logic to [InvocationStrategy](impl/InvocationStrategy.kt)
  * provides capabilities for InvocationStrategy: 
    * a number requestId number fountain
    * assign an invocation strategy for a requestId
    * remove an invocation strategy for a requestId
  
* **[InvocationStrategy](impl/InvocationStrategy.kt)** - an invocation strategy ('strategy' for short) is an abstraction
to handle different function call semantics outlined in [section 1](#1-design-considerations). It's job is to:
  * provide a factory to determine which concrete strategy it should use
  * a constructor to carry the parameters for the invocation
  * a method to return the result of the invocation (this may or may not initiate the invocation depending on the return type)
  * a method to handle messages for a given requestId
  * protected methods for concrete implementations to perform the actual invocation
  * abstract methods for concrete implementations to be notified of messages for results, errors, and completion. 
   