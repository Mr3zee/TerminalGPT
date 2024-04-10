// Gradle config file
// https://docs.gradle.org/current/userguide/userguide.html

plugins {
    // using Kotlin/JVM
    kotlin("jvm") version "1.9.23"
}

group = "mr3zee.pet"
version = "1.0-SNAPSHOT"

// source for dependencies
repositories {
    mavenCentral()
}

// dependencies of the app
dependencies {
    implementation(kotlin("stdlib"))
    // OpenAI Kotlin client
    implementation("com.aallam.openai:openai-client:3.7.1")
    // Ktor Client engine
    implementation("io.ktor:ktor-client-okhttp:2.3.10")
    // for Ktor logs
    implementation("org.slf4j:slf4j-simple:1.6.1")
}

kotlin {
    jvmToolchain(17)
}

// fat jar task (usage: `./gradlew fatJar`)
// creates a fat jar for the application (https://stackoverflow.com/questions/11947037/what-is-an-uber-jar-file)
// use run.sh script to run the app
tasks.create<Jar>("fatJar") {
    manifest {
        attributes("Main-Class" to "MainKt")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )
    with(tasks["jar"] as CopySpec)
}
