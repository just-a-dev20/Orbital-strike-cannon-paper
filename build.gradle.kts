plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "io.github.justadev20"
version = providers.gradleProperty("pluginVersion").get()

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

val paperApi = "io.papermc.paper:paper-api:${providers.gradleProperty("paperApiVersion").get()}"
val nettyTransport = "io.netty:netty-transport:${providers.gradleProperty("nettyVersion").get()}"

dependencies {
    compileOnly(paperApi)
    compileOnly(nettyTransport)

    testImplementation(paperApi)
    testImplementation(nettyTransport)
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-Xlint:-classfile"))
    }

    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version, "minecraftVersion" to minecraftVersion)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        archiveBaseName = "OrbitalRailgun"
        from("LICENSE")
    }

    runServer {
        minecraftVersion(minecraftVersion)
        jvmArgs("-Dcom.mojang.eula.agree=true", "-Xms2G", "-Xmx4G")
    }
}
