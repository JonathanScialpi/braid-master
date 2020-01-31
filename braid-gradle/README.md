
# Braid Gradle Plugin

In order to build and distribute the Plugin to https://plugins.gradle.org/

## Building the plugin
The plugin is built from here
```
./gradlew assemble
```

## Releasing the Braid plugin
1. You need to copy the correct keys (from Fuzz/Robin) for publishing to
Add the keys into ~/.gradle/gradle.properties

```
gradle.publish.key=07C....
gradle.publish.secret=BY3...
```

2. To publish the build plugin run the following gradlew task
```
./gradlew publishPlugins
```


## Using the plugin
1. Add the following to your (corda samples) build.gradle file
```
plugins {
    id "io.bluebank.braid" version "....."
}


braid {
    port = 10018
    username = 'user1'
    password = 'password'
    networkAndPort = 'localhost:10003'
    cordAppsDirectory = "kotlin-source/build/nodes/Notary/cordapps"
}

```

2. To generate the braid example run the following
```
.\gradlew braid
```