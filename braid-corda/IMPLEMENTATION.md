This contains notes about the implementation of braid-corda module.

## Flows

braid-server binds flows to REST in the following call stack:

- BraidCordaStandaloneServer
- calls FlowInitiator::getInitiator
- which calls trampoline to convert the constructor to a KFunction
- which is converted to a KCallable

FlowInitiator has a FlowStarterAdapter and the trampolined
function body calls flowStarter.startFlowDynamic
using the parameter values which are passed to it at run-time.

---

CordaSockJSHandler depends on existence of AppServiceHub,
which is null when BraidCordaStandaloneServer calls
bootstrapBraid, therefore braid-server supports rest but not RPC.

---

Cordapps can be annotated using Swagger annotations which are defined here:

- https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations

Then the braid code in the io.bluebank.braid.corda.rest.docs.v3 package
converts the code+annotations to a Swagger document.

You might also want to read:

- https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Getting-started
- https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Integration-and-configuration