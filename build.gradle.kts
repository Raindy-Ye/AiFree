import sun.jvmstat.monitor.MonitoredVmUtil.mainClass

plugins {
    kotlin("jvm") version "2.3.21"
    id("io.ktor.plugin") version "3.4.3"
}

group = "org.raindy"
version = "1.0.0"

repositories {
    mavenCentral()
}

ktor {
    fatJar {
        archiveFileName.set("${project.name}-${project.version}-all.jar")
    }
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    implementation(platform("io.ktor:ktor-bom:3.4.3"))
    // Ktor server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-forwarded-header")
    // Ktor client
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-server-status-pages")

    implementation("com.microsoft.playwright:playwright:1.59.0")

    implementation("io.klogging:klogging:0.11.7")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.jar {
    enabled = false  // only built the fat jar
}

tasks.test {
    useJUnitPlatform()
}
