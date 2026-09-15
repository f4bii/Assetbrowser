plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    `maven-publish`
}

version = property("mod_version")!!
group = property("maven_group")!!

base {
    archivesName = property("archives_base_name")!!.toString()
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

loom {
    splitEnvironmentSourceSets()

    runs.named("client") {
        vmArg("-Ddevauth.enabled=1")
        property("devauth.state-dir", rootProject.file(".devauth").absolutePath)
    }

    mods {
        create("assetbrowser") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.named("client").get())
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    add("localRuntime", "net.litetex.mcm:dev-auth-neo:${property("dev_auth_version")}")
    add("clientImplementation", "net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xjspecify-annotations=strict")
    }
}
