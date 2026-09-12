plugins { java }
repositories { mavenCentral() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
dependencies {
    implementation(gradleApi())
    implementation("org.jooq:jooq-codegen:3.21.8")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.liquibase:liquibase-core:5.0.4")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")
}
dependencyLocking { lockAllConfigurations() }
