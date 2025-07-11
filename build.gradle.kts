plugins {
    kotlin("jvm") version "2.2.0"
    application
    id("com.github.gmazzo.buildconfig") version "5.6.7"
}

group = "earth.groundctrl"
version = "v2025.07"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.9.0")
    implementation("com.github.ajalt.clikt:clikt:5.0.1")

    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.17")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
application {
    mainClass.set("earth.groundctrl.spacecafe.MainKt")
}

buildConfig {
    buildConfigField("APP_NAME", "SpaceCafe")
    buildConfigField("APP_VERSION", provider { "\"${project.version}\"" })
}
