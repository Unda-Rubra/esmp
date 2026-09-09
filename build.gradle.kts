plugins {
    base
}

allprojects {
    group = "xdu.undarubra.esmp"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
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
