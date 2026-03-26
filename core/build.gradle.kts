plugins {
    `java-library`
    `java-test-fixtures`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

dependencies {
    testFixturesApi(platform(libs.testcontainers.bom))
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.postgresql)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test { useJUnitPlatform() }
