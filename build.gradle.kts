plugins {
    base
}

allprojects {
    group = "io.taskbeacon"
    version = "0.1.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
            withSourcesJar()
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
