plugins {
    `java-library`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

dependencies {
    api(project(":core"))
    implementation(libs.liquibase.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(testFixtures(project(":core")))
    testImplementation(project(":flyway"))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }
