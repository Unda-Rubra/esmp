plugins {
    `java-library`
}

dependencies {
    api("org.jooq:jooq:3.21.8")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.liquibase:liquibase-core:5.0.4")
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
