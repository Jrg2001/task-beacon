plugins { `java-library` }

dependencies {
    api(project(":source-api"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
