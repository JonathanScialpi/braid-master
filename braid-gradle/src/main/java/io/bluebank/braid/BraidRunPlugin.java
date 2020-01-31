package io.bluebank.braid;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.Scanner;

import io.bluebank.braid.corda.server.BraidMain;
import io.vertx.core.Future;

/**
 * from https://docs.gradle.org/current/userguide/custom_tasks.html
 * and https://guides.gradle.org/writing-gradle-plugins/
 * and https://guides.gradle.org/publishing-plugins-to-gradle-plugin-portal/
 * 
 */
public class BraidRunPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        BraidRunPluginExtension extension = project.getExtensions()
                .create("runBraid", BraidRunPluginExtension.class);

        project.task("runBraid")
                .doLast(task -> {
                    startBraid(extension).setHandler(h->{
                        System.out.println("Braid started on port:" + extension.getPort());
                    });
                });

        System.out.println("Press any key to quit:");
        Scanner userInput = new Scanner(System.in);
        userInput.nextLine();
    }

    public Future<String> startBraid(BraidRunPluginExtension extension) {
        System.out.println(
                "this will in future start braid on port port" + extension.getPort());
        System.out.println(
                "cordApps directory: " + extension.getCordAppsDirectory());

        return new BraidMain().start(extension.getNetworkAndPort(),
                extension.getUsername(),
                extension.getPassword(),
                extension.getPort());
    }
}