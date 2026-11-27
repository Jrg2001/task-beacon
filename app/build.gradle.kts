import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21.0.5"
    modules("javafx.controls")
}

dependencies {
    implementation(project(":source-api"))
    implementation(project(":core"))
    implementation(project(":source-demo"))
    implementation(project(":source-codex"))
    implementation(project(":source-claude"))
    implementation(project(":ui-desktop"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")
}

application {
    mainClass.set("io.taskbeacon.app.TaskBeaconLauncher")
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Class-Path"] = configurations.runtimeClasspath.get().files
            .joinToString(" ") { it.name }
    }
}

// Packaging. Only the Java modules the app uses go into the bundled runtime (from `jdeps --print-module-deps`
// over the installDist jars); keep this list in sync when adding dependencies that need other modules.
val runtimeModules = "java.base,java.desktop,java.naming,java.prefs,java.sql,jdk.jfr,jdk.unsupported"
val packagingIcon = layout.projectDirectory.file("src/main/packaging/TaskBeacon.ico")
// Never change: Windows uses it to recognise newer TaskBeacon installers as upgrades of this one.
val windowsUpgradeUuid = "6f2b6a52-8c1e-4c7a-9d0e-3b5f1a7c4e21"

fun jpackageExecutable(): File {
    val name = if (System.getProperty("os.name").lowercase().contains("win")) "jpackage.exe" else "jpackage"
    return file("${System.getProperty("java.home")}/bin/$name")
}

fun commonJpackageArgs(): List<String> = listOf(
    "--input", layout.buildDirectory.dir("install/app/lib").get().asFile.absolutePath,
    "--name", "TaskBeacon",
    "--app-version", project.version.toString(),
    "--vendor", "TaskBeacon",
    "--description", "Local-first task status monitor",
    "--icon", packagingIcon.asFile.absolutePath,
    "--main-jar", tasks.named<org.gradle.jvm.tasks.Jar>("jar").get().archiveFileName.get(),
    "--main-class", application.mainClass.get(),
    "--add-modules", runtimeModules,
    "--jlink-options", "--strip-native-commands --strip-debug --no-man-pages --no-header-files --compress zip-6"
)

tasks.register<Exec>("portableExe") {
    group = "distribution"
    description = "Creates a self-contained Windows app image with TaskBeacon.exe"
    dependsOn("installDist")
    val packageRoot = rootProject.layout.projectDirectory.dir("release")
    inputs.dir(layout.buildDirectory.dir("install/app/lib"))
    inputs.file(packagingIcon)
    inputs.file(File(System.getProperty("java.home"), "release"))
    inputs.property("hostArchitecture", System.getProperty("os.arch"))
    inputs.property("jpackageArgs", providers.provider { commonJpackageArgs() })
    outputs.dir(packageRoot.dir("TaskBeacon"))
    doFirst {
        val destination = packageRoot.asFile
        delete(destination.resolve("TaskBeacon"))
        destination.mkdirs()
        commandLine(listOf(jpackageExecutable().absolutePath, "--type", "app-image",
            "--dest", destination.absolutePath) + commonJpackageArgs())
    }
}

tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Zips the portable app image: unzip anywhere and run TaskBeacon.exe"
    dependsOn("portableExe")
    from(rootProject.layout.projectDirectory.dir("release/TaskBeacon")) { into("TaskBeacon") }
    archiveFileName.set("TaskBeacon-${project.version}-windows-portable.zip")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("release"))
}

tasks.register<Exec>("windowsInstaller") {
    group = "distribution"
    description = "Creates TaskBeacon-<version>.exe, a per-user Windows installer (needs WiX Toolset 3 on PATH)"
    dependsOn("installDist")
    val destination = rootProject.layout.projectDirectory.dir("release").asFile
    inputs.dir(layout.buildDirectory.dir("install/app/lib"))
    inputs.file(packagingIcon)
    inputs.file(File(System.getProperty("java.home"), "release"))
    inputs.property("hostArchitecture", System.getProperty("os.arch"))
    inputs.property("jpackageArgs", providers.provider { commonJpackageArgs() })
    val installerName = "TaskBeacon-${project.version}.exe"
    inputs.property("windowsUpgradeUuid", windowsUpgradeUuid)
    outputs.file(destination.resolve(installerName))
    doFirst {
        destination.mkdirs()
        delete(temporaryDir.resolve(installerName))
        commandLine(listOf(jpackageExecutable().absolutePath, "--type", "exe",
            "--dest", temporaryDir.absolutePath,
            // Installs for the current user (no administrator prompt) into %LOCALAPPDATA%\TaskBeacon App and
            // upgrades in place. Never install into %LOCALAPPDATA%\TaskBeacon: that is the app's data folder
            // (config, logs, pets) and uninstalling deletes the whole install folder. A nested folder such as
            // Programs\TaskBeacon is rejected by WiX (ICE64).
            "--win-per-user-install",
            "--install-dir", "TaskBeacon App",
            "--win-menu", "--win-menu-group", "TaskBeacon",
            "--win-shortcut",
            "--win-upgrade-uuid", windowsUpgradeUuid) + commonJpackageArgs())
    }
    doLast {
        Files.move(temporaryDir.resolve(installerName).toPath(), destination.resolve(installerName).toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
