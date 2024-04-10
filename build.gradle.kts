plugins {
    kotlin("jvm") version "1.9.23"
}

group = "mr3zee.pet"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.aallam.openai:openai-client:3.7.1")
    implementation("io.ktor:ktor-client-okhttp:2.3.10")
    implementation("io.ktor:ktor-client-okhttp-jvm:2.3.10")
    implementation("org.slf4j:slf4j-simple:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

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
