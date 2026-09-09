plugins {
    `java-library`
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
