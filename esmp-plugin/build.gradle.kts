plugins {
    `java-library`
}

dependencies {
    implementation(project(":esmp-core"))
    implementation(project(":esmp-civilization"))
    implementation(project(":esmp-resource"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    archiveBaseName.set("esmp")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { files ->
        files.map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "META-INF/versions/**/module-info.class")
    manifest.attributes("Multi-Release" to "true")
}
