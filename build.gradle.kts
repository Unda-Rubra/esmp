plugins {
    base
}

allprojects {
    group = "xdu.undarubra.esmp"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")
    dependencies.add("compileOnly", "jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }
    if (name == "esmp-core") {
        extensions.configure<SourceSetContainer> {
            named("main") { resources.srcDir(rootProject.file("schema")) }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    dependencyLocking {
        lockAllConfigurations()
    }
}

listOf(
    "esmp-core" to "world_definitions|world_instances|game_clock",
    "esmp-civilization" to "civilization_schema|settlements|settlement_.*|parcel.*|activity_.*|wild_activity.*|land_value_.*|claim_.*|player_land_progress|buildings|building_.*|decay_.*|repair_.*",
    "esmp-economy" to "economy_.*"
).forEach { (module, tables) ->
    project(":$module") {
        val generated = tasks.register<GenerateJooq>("generateJooq") {
            repositoryRoot.set(rootProject.layout.projectDirectory)
            migrations.from(rootProject.fileTree("schema") { include("**/*.xml") })
            targetPackage.set("xdu.undarubra.esmp.${module.removePrefix("esmp-")}.generated")
            realColumns.set(module == "esmp-civilization")
            includes.set(tables)
            outputDirectory.set(layout.buildDirectory.dir("generated-src/jooq"))
        }
        extensions.configure<SourceSetContainer> {
            named("main") { java.srcDir(generated.flatMap { it.outputDirectory }) }
        }
        tasks.named("compileJava") { dependsOn(generated) }
    }
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}


tasks.named("clean") {
    dependsOn(subprojects.map { "${it.path}:clean" })
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}
tasks.wrapper {
    gradleVersion = "9.7.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
}
