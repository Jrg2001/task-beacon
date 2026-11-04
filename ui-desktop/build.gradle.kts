plugins {
    `java-library`
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21.0.5"
    modules("javafx.controls")
}

dependencies {
    api(project(":core"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
