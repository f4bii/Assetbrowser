plugins {
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.jvm")
    id("dev.kikugie.stonecutter")
    id("me.modmuss50.mod-publish-plugin")
    `maven-publish`
}

version = "${property("mod_version")}+${sc.current.version}"
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
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runs.named("client") {
        generateRunConfig = sc.current.isActive
        if (sc.current.version == sc.tree.vcs.version) {
            runDirectory = rootProject.layout.projectDirectory.dir("run")
        }
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
    minecraft("com.mojang:minecraft:${sc.current.version}")
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
    val properties = mapOf(
        "version" to project.version,
        "minecraft_dependency" to project.property("minecraft_dependency"),
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
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

publishMods {
    file = tasks.jar.flatMap { it.archiveFile }
    version = project.version.toString()
    displayName = "Asset Browser ${property("mod_version")} for ${sc.current.version}"
    // CHANGELOG from release.yml
    changelog = providers.environmentVariable("CHANGELOG").orElse("")
    type = STABLE
    modLoaders.add("fabric")
    dryRun = providers.environmentVariable("MODRINTH_TOKEN").getOrNull() == null

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = property("modrinth_project_id").toString()
        minecraftVersions.addAll(property("modrinth_game_versions").toString().split(" "))
        requires("fabric-language-kotlin")
        environment = CLIENT_ONLY
    }
}
