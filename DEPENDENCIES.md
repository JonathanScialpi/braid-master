# Dependencies
This summarises the dependencies defined in each module's `pom.xml` file.

This summary was created 2019-12-05 so it's possibly out of date.

groupId|artifactId|version|modules
---|---|---|---
com.fasterxml.jackson.core|jackson-annotations|2.9.7|braid-server
com.fasterxml.jackson.core|jackson-core|2.9.7|braid-core
com.fasterxml.jackson.core|jackson-databind|2.9.7|braid-core
com.fasterxml.jackson.dataformat|jackson-dataformat-yaml|2.9.7|braid-core
com.fasterxml.jackson.datatype|jackson-datatype-jdk8|2.9.7|braid-corda
com.fasterxml.jackson.datatype|jackson-datatype-jsr310|2.9.7|braid-core
com.fasterxml.jackson.module|jackson-module-jsonSchema|2.9.7|braid-core, braid-server
com.fasterxml.jackson.module|jackson-module-kotlin|2.9.7|braid-core, braid-server
com.fasterxml.jackson.module|jackson-module-parameter-names|2.9.7|braid-corda
io.bluebank.braid|braid-client|${project.version}|braid-corda-client
io.bluebank.braid|braid-corda|${project.version}|braid-corda-client, examples/example-cordapp, braid-server
io.bluebank.braid|braid-core|${project.version}|braid-standalone-server, braid-corda, braid-client, braid-corda-client, braid-server
io.bluebank.braid|braid-standalone-server|${project.version}|examples/example-server
io.github.classgraph|classgraph|4.6.12|braid-corda
io.netty|netty-buffer|4.1.24.Final|braid-core
io.netty|netty-codec-http|4.1.24.Final|braid-core
io.netty|netty-codec-http2|4.1.24.Final|braid-core
io.netty|netty-common|4.1.24.Final|braid-core
io.netty|netty-handler|4.1.24.Final|braid-core
io.netty|netty-handler-proxy|4.1.24.Final|braid-core
io.netty|netty-resolver|4.1.24.Final|braid-core
io.netty|netty-resolver-dns|4.1.24.Final|braid-core
io.netty|netty-transport|4.1.24.Final|braid-core
io.reactivex|rxjava|1.3.8|braid-core
io.swagger.core.v3|swagger-core|2.0.9|braid-core
io.swagger.core.v3|swagger-models|2.0.9|braid-core
io.vertx|vertx-auth-common|3.7.1|braid-core
io.vertx|vertx-auth-jwt|3.7.1|braid-core
io.vertx|vertx-auth-shiro|3.7.1|braid-core
io.vertx|vertx-core|3.7.1|braid-core, braid-server
io.vertx|vertx-lang-kotlin|3.7.1|braid-core, braid-server
io.vertx|vertx-rx-java|3.7.1|braid-core
io.vertx|vertx-web|3.7.1|braid-core, braid-server
javax.ws.rs|javax.ws.rs-api|2.0.1|braid-core
net.corda|corda-core|4.3|braid-server
net.corda|corda-jackson|4.3|braid-server
net.corda|corda-node|4.3|braid-corda, braid-corda-client
net.corda|corda-rpc|4.3|braid-server
org.apache.commons|commons-lang3|3.9|braid-core
org.apache.logging.log4j|log4j-slf4j-impl|2.11.2|braid-server
org.jetbrains.kotlin|kotlin-reflect|1.2.71|examples/example-server, examples/example-server, braid-core, braid-server
org.jetbrains.kotlin|kotlin-stdlib-jdk8|1.2.71|examples/example-server, braid-core, braid-server
org.ow2.asm|asm|5.0.4|braid-core
org.slf4j|jul-to-slf4j|1.7.25|braid-client, braid-server
org.slf4j|slf4j-api|1.7.25|braid-core, braid-server
