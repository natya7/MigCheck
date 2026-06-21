plugins {
    `java-library`
    `java-test-fixtures`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

dependencies {
    implementation(libs.jsqlparser)

    testFixturesApi(platform(libs.testcontainers.bom))
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.testcontainers.mysql)
    testFixturesApi(libs.postgresql)
    testFixturesApi(libs.mysql)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.launcher)

    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter)
    compileOnly(platform(libs.testcontainers.bom))
    compileOnly(libs.testcontainers.postgresql)
    compileOnly(libs.testcontainers.mysql)
    compileOnly(libs.postgresql)
    compileOnly(libs.mysql)
}

tasks.test { useJUnitPlatform() }
