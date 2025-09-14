plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.funguscow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":Synthesis"))
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

tasks.test {
    useJUnitPlatform()
    this.testLogging {
        this.showStandardStreams = true
    }
}

tasks.shadowJar {
    archiveBaseName.set("Speakeasy")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("com.funguscow.synthland.speaker.VoicesToWavKt")
}
