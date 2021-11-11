// Properties
val kotlinVersion: String by project
val asmVersion: String by project
val mixinVersion: String by project
val hephaistosVersion: String by project
val adventureVersion: String by project


plugins {
    `java-library`
    `maven-publish`
}

allprojects {
    group = "net.minestom.server"
    version = "1.0"
    description = "Lightweight and multi-threaded Minecraft server implementation"
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.spongepowered.org/maven")
    }
}

sourceSets {
    main {
        java {
            srcDir(file("src/autogenerated/java"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

tasks {
    withType<Javadoc> {
        (options as? StandardJavadocDocletOptions)?.apply {
            encoding = "UTF-8"

            // Custom options
            addBooleanOption("html5", true)
            addStringOption("-release", "17")
            // Links to external javadocs
            links("https://docs.oracle.com/en/java/javase/17/docs/api/")
            links("https://jd.adventure.kyori.net/api/${adventureVersion}/")
        }
    }
    withType<Test> {
        useJUnitPlatform()
    }
    withType<Zip> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

dependencies {
    // Junit Testing Framework
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    // Only here to ensure J9 module support for extensions and our classloaders
    testCompileOnly("org.mockito:mockito-core:4.0.0")

    // https://mvnrepository.com/artifact/it.unimi.dsi/fastutil
    api("it.unimi.dsi:fastutil:8.5.6")

    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    api("com.google.code.gson:gson:2.8.9")

    // Logging
    api("org.apache.logging.log4j:log4j-core:2.14.1")
    // SLF4J is the base logger for most libraries, therefore we can hook it into log4j2.
    api("org.apache.logging.log4j:log4j-slf4j-impl:2.14.1")

    // https://mvnrepository.com/artifact/org.jline/jline
    implementation("org.jline:jline:3.20.0")
    // https://mvnrepository.com/artifact/org.jline/jline-terminal-jansi
    implementation("org.jline:jline-terminal-jansi:3.20.0")

    implementation("com.github.ben-manes.caffeine:caffeine:3.0.4")

    // https://mvnrepository.com/artifact/com.zaxxer/SparseBitSet
    implementation("com.zaxxer:SparseBitSet:1.2")

    // Guava 21.0+ required for Mixin
    api("com.google.guava:guava:31.0.1-jre")

    // Code modification
    api("org.ow2.asm:asm:${asmVersion}")
    api("org.ow2.asm:asm-tree:${asmVersion}")
    api("org.ow2.asm:asm-analysis:${asmVersion}")
    api("org.ow2.asm:asm-util:${asmVersion}")
    api("org.ow2.asm:asm-commons:${asmVersion}")
    api("org.spongepowered:mixin:${mixinVersion}")

    // Path finding
    api("com.github.MadMartian:hydrazine-path-finding:1.6.0")

    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    api("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")

    // NBT parsing/manipulation/saving
    api("com.github.jglrxavpok:Hephaistos:${hephaistosVersion}")
    api("com.github.jglrxavpok:Hephaistos:${hephaistosVersion}:gson")
    api("com.github.jglrxavpok:Hephaistos:${hephaistosVersion}") {
        capabilities {
            requireCapability("org.jglrxavpok.nbt:Hephaistos-gson")
        }
    }

    api("com.github.Minestom:DependencyGetter:v1.0.1")
    implementation("com.github.Minestom:MinestomDataGenerator:47da93dd5a99280314d7e6137d2c81e7793f0fdb")

    // Adventure, for user-interface
    api("net.kyori:adventure-api:${adventureVersion}")
    api("net.kyori:adventure-text-serializer-gson:${adventureVersion}")
    api("net.kyori:adventure-text-serializer-plain:${adventureVersion}")
    api("net.kyori:adventure-text-serializer-legacy:${adventureVersion}")
}

configurations.all {
    // we use jetbrains annotations
    exclude("org.checkerframework:checker-qual")
}

//publishing {
//    publications {
//        mavenJava(MavenPublication) {
//            from components["java"]
//        }
//    }
//}
