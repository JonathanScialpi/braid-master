# Creation of synthetic objects

- [Overview](#overview)
- [Two implementations to annotate the synthetic type](#two-implementations-to-annotate-the-synthetic-type)
- [Why two implementations?](#why-two-implementations)
  - [Problems when the type definition uses $ref](#problems-when-the-type-definition-uses-ref)
  - [The second implementation replaces $ref with allRef](#the-second-implementation-replaces-ref-with-allref)
  - [The first implementation still exists though disabled](#the-first-implementation-still-exists-though-disabled)
- [How I reused some ModelResolver implementation details](#how-i-reused-some-modelresolver-implementation-details)
  - [Discovery](#discovery)
  - [Copy-and-pasting](#copy-and-pasting)
  - [Swagger's resolveProperty interface](#swaggers-resolveproperty-interface)
  - [Another way to have implemented this](#another-way-to-have-implemented-this)
- [Handling different types of annotation](#handling-different-types-of-annotation)
  - [@Operation](#operation)
  - [@Schema](#schema)
  - [@Parameter](#parameter)

## Overview

The basic idea is that during Braid startup it will:

- Inspect Cordapps and find the flow classes (and other classes) in those Cordapps
- Bind the flow constructors to Router paths --
  see `BraidCordaStandaloneServer.createRestConfig` --
  so that Braid users can invoke the flow constructors using Braid.

There are two complications:

- Braid must convert the flow constructor to a "callable function" -- that's what the
  `trampoline` function is doing, with its `KFunction` return type.
  
  To do this, Braid uses the list of constructor parameters to create a
  "synthetic type" -- this is the type of payload data which (when using REST)
  a user sends in the body of an HTTP POST request to the route.
  
  The construction of this synthetic type is implemented in
  `ClassFromParametersBuilder` (using a `ClassWriter` instance with extension methods
  defined in `AsmUtilities.kt`).

- Users can "annotate" their source code, using annotations defined by Swagger.
  Swagger creates API documentation and so on from these annotations, so, they are useful!
  
  So when Braid constructs a synthetic type from a flow constructor, it must also port any
  user-created Swagger annotations from the flow constructor to the synthetic type.

## Two implementations to annotate the synthetic type

Porting the annotations has been non-trivial: we've made two implementations of that
functionality.

- One implementation is in `AsmUtilities.kt` --
  i.e. when we create each field of the synthetic type,
  we also create any annotations for that field.
  
  Therefore when Swagger processes the synthetic type (as usual),
  it finds those annotations and includes those in its "model".

- The other implementation is in the `SyntheticModelConverter` object.

  This object is hooked into the chain of Swagger's `ModelResolver` instances.
  
  When using this implementation, the synthetic type is unannotated, and instead
  when swagger tries to "resolve" the synthetic type to create a "model" (i.e. "Schema"),
  then the `SyntheticModelConverter` intercepts the resolution request and constructs
  (a.k.a. synthesises) a schema which includes the annotations.

Both implementations are in the code, only one is used at run-time.
The choice of which is used is hard-coded into the `SynthesisOptions` object.

## Why two implementations?

The first implementation was easier to write -- and easier to understand, safer --
and was the first implementation we attempted.

### Problems when the type definition uses $ref

Unfortunately we discovered a limitation in the Swagger model, which is that it isn't
possible to annotate a field or parameter with a complex type!
If you try to, then you'd be annotating the type, rather than the field or parameter.

This is unfortunate, for example you might have several different parameters of type
`Party`, and you might want to annotate those parameters to say what each is for --
to distinguish the different usages of `Party`.

But anyway, this limitation is known --
it's a consequence of the way in which `$ref` is used in the Swagger OpenAPI spec., and
it will not be changed in the foreseeable future --
and as it says in [Using $ref](https://swagger.io/docs/specification/using-ref/)

> Any sibling elements of a `$ref` are ignored.

This is a known issue -- [Extra JSON Reference properties ignored - reduces reusability of
references](https://github.com/OAI/OpenAPI-Specification/issues/556)

Worse than that, however, Swagger associates the annotation with the referenced type,
so for example an annotation like this one ...

```
@Schema(description = "The counter-party which will respond with an echo")
val responder: Party
```

... will be added to the shared/global definition of the `Party` type like this ...

```
      "net.corda.core.identity.Party" : {
        "required" : [ "name", "owningKey" ],
        "type" : "object",
        "properties" : {
          "name" : {
            "type" : "string",
            "description" : "CordaX500Name encoded Party",
            "example" : "O=Bank A, L=London, C=GB"
          },
          "owningKey" : {
            "type" : "string",
            "description" : "Base 58 Encoded Public Key",
            "example" : "GfHq2tTVk9z4eXgyUuofmR16H6j7srXt8BCyidKdrZL5JEwFqHgDSuiinbTE"
          }
        },
        "description" : "The counter-party which will respond with an echo"
      }
```

I guess that's a bug but what can you do.
This bug is documented by some of the tests in the FlowInitiatorTest class in braid-corda.

### The second implementation replaces $ref with allRef

There seems to be two recommended ways to work-around this in the model, according to e.g.
https://stackoverflow.com/questions/33629750/swagger-schema-properties-ignored-when-using-ref-why/

1. Replace the `$ref` with an `allOf` definition
2. Replace the `$ref` with an inline definition (i.e. copy the type definition inline)

I don't see an easy way to do either of these work-arounds using only Swagger annotations:

1. https://stackoverflow.com/questions/57459520/swagger-openapi-annotations-how-to-produce-allof-with-ref
   and https://github.com/swagger-api/swagger-core/issues/3290 say that trying to define a
   Scehma annotation to specify `allOf` doesn't work --
   which is also what I find when I try it in the FlowInitiatorTest class
2. The Swagger API doesn't seem to support replacing `$ref` with an inline definition --
   I tried altering the undocumented `resolveAsRef` in in the FlowInitiatorTest class
   which seemed to make no difference that I noticed

So there's no known way to synthesise and/or annotate a type,
in such a way that Swagger's `ModelResolver` will create its schema using `allRef`.

It's therefore to do this that we implement the `SyntheticModelConverter` --
i.e. to create schema whose fields are defined using `allRef` instead of `$ref`,
so that these fields too can have their own annotations.

### The first implementation still exists though disabled

The second (i.e. `SyntheticModelConverter`) implementation might be a little risky --
because it currently depends on knowing some undocumented Swagger internals.
And it's difficult to be sure we've tested exhaustively/fully,
there might be flow constructors with parameter types which we hadn't anticipated.

It's partly for this reason that the first implementation remains in the code.
In an emergency -- if you find that Braid's `SyntheticModelConverter` can't properly
handle some unusual CordApp -- then you might edit the `SynthesisOptions` object
to revert to using the simpler implementation (at the cost of not having annotations
on complex types).

## How I reused some ModelResolver implementation details

### Discovery

To discover some `ModelResolver` internals,
i.e. to see how how Swagger resolves a type if left to itself when a custom resolver
isn't installed, I did the following:

- Write a test case in `SyntheticModelResolverTest`
- Set a debugger breakpoint on `ModelResolver.resolve`
- Run the test for a given type.

Doing that shows that `ModelResolver` recurses --
i.e. it calls itself (via its `ModelConverterContext` instance),
after creating various complicated `AnnotatedType`,
e.g. to resolve the type of each field within a type.

The simplest form of `AnnotatedType` instance can't be used to resolve some complex types
-- for example this doesn't work:

```kotlin
val partySet: Set<Party> = emptySet()
// this fails to resolve a standalone Set<Party>
val resolvedPartySet = context.resolve(AnnotatedType(partySet::class.java))
```

... but for example this does work:

```kotlin
class Foo(val partySet: Set<Party>)
// this can resolve a property of type Set<Party> within an object
val resolvedPartySet = context.resolve(AnnotatedType(Foo::class.java))
```

Given that we only want to resolve things within an object
(i.e. properties within the "synthetic object",
where these properties correspond to flow constructor parameters), this is good enough.

Running the above test with a breakpoint, to see how or where `ModelResolver` resolves
each property, finds that this is happening on approximately line 600 of
[our current version of the ModelResolver](https://github.com/swagger-api/swagger-core/blob/v2.0.9/modules/swagger-core/src/main/java/io/swagger/v3/core/jackson/ModelResolver.java).

### Copy-and-pasting

I copied, pasted, and refactored this code
(and simplified it some, removing support for use-cases which I hope don't apply here).
The result of this is seen in the `resolveSchema` and `getPropertyAnnotationType`
functions of the `SyntheticModelConverter` object.

I write this in case you need to maintain that.

Apart from some unexpected parameter type,
another scenario which could conceivably break this implementation is if
there were breaking change to the Swagger implementation in a future version
(e.g. if it changes the way in which `ModelResolver` uses `AnnotatedType`).

### Swagger's resolveProperty interface

Swagger's `ModelConverterContext` interface used to support a `resolveProperty` method ---
which, might have been useful ...

```
Property resolveProperty(Type var1, Annotation[] var2);
```

... but that was in v2 i.e. `io.swagger.converter` not `io.swagger.v3.core.converter` --
and I don't know why it was removed from the public interface.

### Another way to have implemented this

A better way to implement this might be to rewrite `SynethicModelConverter` so that it:

- Delegates (e.g. to `ModelResolver`) the resolving of the entire synthetic type --
  with all of its properties --
  without trying to resolve each property individually
- Edit the Schema object which it gets from the base resolvers --
  i.e. convert `$ref` to `allRef`, and then add or mixing the per-field annotations,
  more-or-less as it does now.

This might make the implementation less fragile, i.e. less dependent on copy-and-pasting
implementation details from `ModelResolver`.

## Handling different types of annotation

In theory we might like to support all annotations with which CordApp developers might annotate their flows,
in practice we support specific annotation types.

The annotations are:

- `@Operation` (on the flow)
- `@Schema` and `@Parameter` (on the flow constructor parameters)

### @Operation

In theory the `@Operation` annotation can only be used on a method, not on a constructor --
because of it's annotated with `@Target(ElementType.METHOD)`.

So, we ask the CordApp developer to annotate the `call` method of their flow class
as shown for example in the `io.bluebank.braid.cordapp.echo.flows` test class.

These annotations -- including the `@Operation` annotation -- are:

- Extracted from the flow class by the `getFlowMethodAnnotations` function
- Passed in the `trampoline` function's `additionalAnnotations` parameter
- Returned in the `annotations` property of `SyntheticConstructorAndTransformer` which implements
  the `KFunction` interface

They are therefore not included in the synthetic type (which contains the parameters as fields)
nor passed to the `SynethicModelConverter`.

### @Schema

We hope that CordApp developers will use `@Schema` annotations to annotation their constructor parameters.

Braid will use these to annotate the corresponding fields of the synthetic type,
using one of the two implementations described in this README as above.

There seem to be two places in the Swagger code which might copy a Schema annotation to a Schema model:

- One is the `AnnotationsUtils.getSchemaFromAnnotation` method which is `public` and `static`.
- Another is the `ModelResolver.resolveSchemaMembers` method which is `protected` and non-static.

ModelResolver uses the latter.

The former would be more convenient (because it's `public` and `static`),
however it creates a new Schema instance instead of copying the annotation into an existing instance;
also, using the latter is closer to what ModelResolver does which might be more correct.

To implement reusing the latter I copied functions from Swagger's `ModelResolver.java` source code into a
`ModelResolverFunctions.java` class in this project/namespace.
In future you might prefer to implement another way to access `protected` functions, which doesn't copy-and-paste them.

One benefit of copy-and-pasting the function was that I could verify that they have no side-effects, i.e. that they
don't depend on nor mutate the state of the `ModelResolver` instance, nor of its `AbstractModelConverter` base class.

### @Parameter

CordApp developers might naturally use `@Paramater` rather than `@Schema` annotations,
to annotate the parameters of their flow constructors.

In theory the `Parameter` interface is annotated like this ...

```
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
```

... therefore it can also be used to annotate fields (of the synthetic type) as well as parameters
(of the flow constructor) -- but in practice the Swagger ModelResolver seems to ignore Parameter annotations,
i.e. they don't affect the generated schema.

Therefore, to support `@Paramater` annotations, I wrote a `schemaFromParameter` function,
which converts a `@Paramater` annotation to either a `@Schema` or an `@ArraySchema` annotation.

There's one test case which tests the fact that a `@Paramater` annotation is converted,
but it's not a complicated test case (not a complicated `@Paramater` instance),
so the `schemaFromParameter` function isn't tested very thoroughly at the moment.
