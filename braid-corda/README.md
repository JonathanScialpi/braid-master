# Braid Corda

Braid Corda allows you to create Braid endpoints and streams from your corda node.  This facilitates:

 * testing your CorDapp
 * communication with your controlling app
 
## Steps to add Braid to your CordApp

There is an example in ```example-cordap/cordap``` but the steps you need follow.  They presume that you are using the CordApp 
Template that R3 provide.

### Configure Gradle to be aware of the em-tech nexus repository and Braid

The Braid maven dependencies are stored in maven central. 
so the first thing to do is to add this to the repositories section of your build.gradle file in both the root and the cordapp 
directories of your CordApp Template.  Note that at the moment you must put this before the corda artifactory:

```gradle
repositories {
    mavenLocal()
    jcenter()
    mavenCentral()
    maven { url 'https://jitpack.io' }
    // must go before the corda one 
    maven { url 'https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases' }
}

``` 

You also need to add ```braid-corda``` to the dependencies section of the build.gradle file:

```gradle
compile "io.bluebank.braid:braid-corda:$braid_version"
```

Finally we need to specify the braid version.  We do this in the ```cordapp/build.gradle``` file by adding:

```gradle
buildscript {
    ext.braid_version = '4.1.2-RC13'
}
``` 

### Creating your Braid infrastructure and a test in your CordApp

The first thing we need to do is to create something to expose.  In this case we will create an echo method that will 
allow us to send up a string and get that string back - just enough to test everything works.

In the cordapp ```src/main/kotlin``` create a file called ```EchoService.kt```:

```kotlin
class EchoService(private val serviceHub: ServiceHubInternal) {

    fun echo(something: String): String  {
        return something
    }

}
```

All we've done here is define our echo method.   Now we need to create the BraidServer that will expose this for us.  
For the moment, create BraidServer.kt in the same directory with the following contents:

```kotlin
@CordaService
class BraidServer(private val serviceHub: ServiceHub) : SingletonSerializeAsToken() {

    companion object {
        private val log = loggerFor<BraidServer>()
    }

    init {
        val config = BraidConfig.fromResource(configFileName)
        if (config == null) {
            log.warn("config $configFileName not found")
        } else {
            bootstrap(config)
        }
    }

    private fun bootstrap(config: BraidConfig) {
        config
            .withService("daoservice", DaoService(serviceHub as ServiceHubInternal))
            .bootstrapBraid(serviceHub)
    }

    private val configFileName: String
        get() {
            val name = serviceHub.myInfo.legalIdentities.first().name.organisation.replace(" ","")
            return "braid-$name.json"
        }

}
```

You will note that this is effectively one line of code which creates Braid and registers our service with it.  Most of the
complexity here is to do with us wanting config so that each node inside one VM listens on a different port.

You will note that we're loading config file names based on the corda node names.  As our intention is to write an
Integration test, we add two files specifiying slightly different.  When we run the ```DriverBasedTest``` that tips up 
with the CordappTemplate later, we will bring up two nodes called Bank A and Network Map Service.  So we create two files 
with names ```braid-BankA.json``` and ```braid-NetworkMapService.json``` in the ```test/resources``` directory with the
simple contents:

```json
{
  "port": 8080
}
```

### Integration Test

> This section requires updating with the JVM Proxy work

Finally we change ```DriverTest``` to contain this:

```kotlin
@RunWith(VertxUnitRunner::class)
class DriverBasedTest {

    private val vertx: Vertx = Vertx.vertx(VertxOptions().setBlockedThreadCheckInterval(30_000))
    private val client = vertx.createHttpClient(HttpClientOptions()
        .setDefaultPort(8080)
        .setDefaultHost("localhost")
        .setSsl(true)
        .setTrustAll(true)
        .setVerifyHost(false)
    )!!

    @After
    fun after(testContext: TestContext) {
        client.close()
    }

    @Test
    fun `test echo service`(testContext: TestContext) {
        driver(isDebug = true, startNodesInProcess = true) {
            val partyA = startNode(providedName = DUMMY_BANK_A.name).getOrThrow()
            jsonRPC("ws://localhost:8080/api/jsonrpc/daoservice/websocket","echo", "testString").map {
                Json.decodeValue(it, JsonRPCResultResponse::class.java)
            }.map {
                it.result.toString()
            }.map {
                testContext.assertEquals("testString", it)
            }.setHandler(testContext.asyncAssertSuccess())
        }
    }

    private fun jsonRPC(url: String, method: String, vararg params: Any?): Future<Buffer> {
        val id = 1L
        val result = Future.future<Buffer>()
        try {
            client.websocket(url) { socket ->
                socket.handler { response ->
                    val jo = JsonObject(response)
                    val responseId = jo.getLong("id")
                    if (responseId != id) {
                        result.fail("expected id $id but $responseId")
                    } else if (jo.containsKey("result")) {
                        result.complete(response)
                    } else if (jo.containsKey("error")) {
                        result.fail(jo.getJsonObject("error").encode())
                    } else if (jo.containsKey("completed")) {
                        // we ignore the 'completed' message
                    }
                }.exceptionHandler { err ->
                    result.fail(err)
                }
                val request = JsonRPCRequest(id = id, method = method, params = params.toList())
                socket.writeFrame(WebSocketFrame.textFrame(Json.encode(request), true))
            }
        } catch (err: Throwable) {
            result.fail(err)
        }
        return result
    }
}
```

When you run this in IntelliJ, you will need to add ```-ea -javaagent:lib/quasar.jar``` to the run configuration so that
various bits of corda work properly, but you should see your test pass \\o/ :-)