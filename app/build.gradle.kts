plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.1.1"
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

tasks.register<Exec>("portableExe") {
    group = "distribution"
    description = "Creates a self-contained Windows app image with TaskBeacon.exe"
    dependsOn("installDist")
    val packageRoot = rootProject.layout.projectDirectory.dir("release")
    inputs.dir(layout.buildDirectory.dir("install/app/lib"))
    outputs.dir(packageRoot.dir("TaskBeacon"))
    doFirst {
        val destination = packageRoot.asFile
        delete(destination.resolve("TaskBeacon"))
        destination.mkdirs()
        val executable = if (System.getProperty("os.name").lowercase().contains("win"))
            "jpackage.exe" else "jpackage"
        commandLine(
            file("${System.getProperty("java.home")}/bin/$executable"),
            "--type", "app-image",
            "--input", layout.buildDirectory.dir("install/app/lib").get().asFile.absolutePath,
            "--dest", destination.absolutePath,
            "--name", "TaskBeacon",
            "--app-version", project.version.toString(),
            "--vendor", "TaskBeacon",
            "--description", "Local-first task status monitor",
            "--main-jar", tasks.named<org.gradle.jvm.tasks.Jar>("jar").get().archiveFileName.get(),
            "--main-class", application.mainClass.get()
        )
    }
}

jlink {
    imageZip.set(layout.buildDirectory.file("distributions/taskbeacon-${System.getProperty("os.name")}.zip"))
    options.set(listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages"))
    launcher { name = "taskbeacon" }
    jpackage {
        imageName = "TaskBeacon"
        installerName = "TaskBeacon"
        appVersion = project.version.toString()
    }
}
