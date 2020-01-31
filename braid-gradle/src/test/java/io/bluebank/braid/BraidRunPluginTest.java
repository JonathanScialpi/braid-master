package io.bluebank.braid;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class BraidRunPluginTest {

    @Test
    public void startBraid(TestContext ctx) {
        Async async = ctx.async();
        BraidRunPluginExtension extension = new BraidRunPluginExtension();
        extension.setPort(9000);
        extension.setNetworkAndPort("localhost:10003");
        extension.setUsername("user1");
        extension.setPassword("password");


        new BraidRunPlugin().startBraid(extension)
                .setHandler(h -> {
                            ctx.assertTrue(h.succeeded());
                            async.complete();
                        }
                );
    }
}