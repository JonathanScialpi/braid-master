package io.bluebank.braid;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
//import org.hamcrest.CoreMatchers.is;
//import org.hamcrest.MatcherAssert.assertThat;

import static org.junit.Assert.assertEquals;

/**
 * Based on https://guides.gradle.org/testing-gradle-plugins/
 *
 * Testing plugins
 */
public class BraidPluginTest {
    @Rule
    public TemporaryFolder testProjectDir = new TemporaryFolder();

    public static String script =
                    "plugins {\n" +
                    "    id 'io.bluebank.braid'\n" +
                    "}\n" +
                    "\n" +
                    "braid {\n" +
                    "    port = 10018\n" +
                    "    username = 'user1'\n" +
                    "    password = 'password'\n" +
                    "    networkAndPort = 'localhost:10003'\n" +
                    "    cordAppsDirectory = \"kotlin-source/build/nodes/PartyC/cordapps\"\n" +
                    "}";

    @Test
    public void startBraid() throws IOException {
//        Project project = ProjectBuilder.builder().build();
//        project.getPluginManager().apply("io.bluebank.braid");
//        project.task("braid");


        File buildFile = testProjectDir.newFile("build.gradle");
        FileOutputStream outputStream = new FileOutputStream(buildFile);
        outputStream.write(script.getBytes());
        outputStream.close();


        BuildResult braid = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments("braid")
            .withPluginClasspath()
            .build();

        assertEquals(braid.task(":braid").getOutcome(), TaskOutcome.SUCCESS);

    }
}
