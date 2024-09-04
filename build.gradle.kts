plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

group = "net.vekn"
version = "1.0.1"

val lombokVersion = "1.18.34"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.7")
    implementation("org.apache.commons:commons-lang3:3.17.0")

    testCompileOnly("org.projectlombok:lombok:${lombokVersion}")
    testAnnotationProcessor("org.projectlombok:lombok:${lombokVersion}")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("net.vekn.Main")
}

tasks.shadowJar {
    archiveFileName.set("${project.name}-${project.version}-executable.jar")
    manifest {
        attributes["Main-Class"] = "net.vekn.Main"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
