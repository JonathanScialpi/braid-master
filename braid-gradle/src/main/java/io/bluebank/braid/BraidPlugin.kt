/**
 * Copyright 2018 Royal Bank of Scotland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.bluebank.braid

import com.typesafe.config.Config
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files.copy
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

open class BraidPlugin : Plugin<Project> {
  companion object {
    val reader = HoconReader()
  }

  override fun apply(project: Project) {
    val extension = project.extensions
        .create("braid", BraidPluginExtension::class.java)

    project.buildDir

    project.task("braid")
        .doLast {
          downloadBraid(project,extension)
          nodeDirectories(project).forEach {
            val config = reader.read("${it.absolutePath}/web-server.conf")
            if(config.hasPath("webAddress"))
              installBraidTo(project,  "${it.absolutePath}", braidProperties(config))
            else
              println("Cant find:${it.absolutePath}/web-server.conf so not creating braid")
          }
        }
  }

  private fun braidProperties(config: Config): JsonObject {
    val extension = JsonObject()
    extension.put("networkAndPort",config.getString("rpcSettings.address"))
    extension.put("username",config.getConfigList("security.authService.dataSource.users").get(0).getString("user"))
    extension.put("password",config.getConfigList("security.authService.dataSource.users").get(0).getString("password"))
    extension.put("port",Integer.parseInt(config.getString("webAddress").split(":")[1]))
    extension.put("cordapps", JsonArray().add("cordapps"))
    return extension
  }

  private fun nodeDirectories(project: Project) =
      File("${project.buildDir}/nodes").listFiles().filter { it.isDirectory && !it.name.startsWith(".") }

  private fun downloadBraid(project: Project, extension: BraidPluginExtension) {
    val version = extension.version ?: ManifestReader("${extension.releaseRepo}/io/bluebank/braid/braid-server/maven-metadata.xml").releaseVersion()
    val repo = if (version.contains("SNAPSHOT")) extension.snapshotRepo else extension.releaseRepo

    val path = "$repo/io/bluebank/braid/braid-server/$version/braid-server-$version.jar"
    if(repo.startsWith("http")) {
      URL(path).downloadTo("${project.buildDir}/braid.jar")
    } else {
      copy(File(path).toPath(),File("${project.buildDir}/braid.jar").toPath(), REPLACE_EXISTING)
    }
  }

  private fun installBraidTo(project: Project, destinationDirectory: String, config: JsonObject) {

    project.copy {
      it.from(project.getPluginFile("/braid.bat"))
      it.into("$destinationDirectory")
    }

    project.copy {
      it.from(project.getPluginFile("/braid"))
      it.fileMode = executableFileMode
      it.into("$destinationDirectory")
    }

    project.copy {
      it.from("${project.buildDir}/braid.jar")
      it.fileMode = executableFileMode
      it.into("$destinationDirectory")
    }

    // <node address> <username> <password> <port> <openApiVersion> [<cordaAppJar1> <cordAppJar2>
    File("$destinationDirectory/braid.conf")
        .writeText(config.encodePrettily())

    println("Braid installed at: $destinationDirectory")
  }
  val executableFileMode = "0755".toInt(8)
}

private fun Project.getPluginFile(filePathInJar: String): File {
  val tmpDir = File(this.buildDir, "tmp")
  tmpDir.mkdirs()
  val outputFile = File(tmpDir, filePathInJar)
  outputFile.outputStream().use {
    BraidPlugin::class.java.getResourceAsStream(filePathInJar).copyTo(it)
  }
  return outputFile
}

private fun URL.downloadTo(destination: String) {
  Channels.newChannel(this.openStream()).use { rbc ->
    FileOutputStream(destination).use { fos ->
          fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE);
        }
  }
}
