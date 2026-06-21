plugins {
    `java-library`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

dependencies {
    api(project(":core"))
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    testRuntimeOnly(libs.flyway.mysql)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(testFixtures(project(":core")))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }
