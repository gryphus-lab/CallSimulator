// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.sonarqube") version "7.5.0.8588"
}

sonar {
  properties {
    property("sonar.projectKey", "gryphus-lab_CallSimulator")
    property("sonar.organization", "gryphus-lab")

    // JaCoCo XML from the debug unit-test coverage report (AGP enableUnitTestCoverage).
    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      "${project.rootDir}/app/build/reports/coverage/test/debug/report.xml"
    )

    // Exclude code that is UI plumbing / generated and not meaningfully unit-testable
    // on the JVM, so the coverage metric reflects the logic we actually test.
    property(
      "sonar.coverage.exclusions",
      listOf(
        "**/MainActivity.kt",
        "**/IncomingCallActivity.kt",
        "**/InCallActivity.kt",
        "**/BaseCallActivity.kt",
        "**/CallSimulationService.kt",
        "**/databinding/**",
        "**/*_ViewBinding*",
      ).joinToString(",")
    )
  }
}