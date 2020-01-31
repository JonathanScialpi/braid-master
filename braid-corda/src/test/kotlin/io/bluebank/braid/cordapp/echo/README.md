This defines a cordapp named "echo" which is used to test Braid's support for annotations.

The classes in EchoFlows.kt are currently analysed via reflection but never actually run.

This directory contains unit-tests for the cordapp -- EchoContractTests and EchoFlowTests.

These unit tests don't run correctly here because the corda test environment assumes that
the cordapp being tested has been built (which these cordapp classes haven't) -- the tests
run successfully if you copy them and the cordapp classes into a suitable project, which
has gradle files and directory names and package names based on cordapp-template-kotlin. 