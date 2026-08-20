plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.10"
    id("antlr")
}


repositories {
    mavenCentral()
    maven("https://jitpack.io") //Kotlin-ANTLR
}

val projectVersion: String by project

kotlin {
    group = "de.unimuenster.imi.fhir"
    version = projectVersion
}


subprojects {
    version = projectVersion
}

val hapi_version: String by project

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
}


kotlin {
    jvmToolchain(17)
}


tasks {
    compileKotlin {
        dependsOn(generateGrammarSource)
    }
}

allprojects {
    apply(plugin = "maven-publish")
}

tasks.register("publishAllModules") {
    dependsOn(":transform-fhir:publish", ":columns-parser:publish")
}


