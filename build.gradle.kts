// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.sonarqube)
}

// Ensure the JaCoCo unit-test coverage report is generated before the scan.
// CI runs `./gradlew build sonar`, and `build` does not trigger the Android
// unit-test coverage report task, so without this the scan sees no coverage.
tasks.named("sonar") {
    dependsOn(":app:createDebugUnitTestCoverageReport")
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
        // Android-framework glue for scheduling: AlarmManager / PendingIntent /
        // SharedPreferences wrapper and BroadcastReceivers. The decision logic
        // is extracted into CallSchedule.kt, which IS unit-tested and measured.
        "**/CallScheduler.kt",
        "**/CallAlarmReceiver.kt",
        "**/BootReceiver.kt",
        "**/databinding/**",
        "**/*_ViewBinding*",
      ).joinToString(",")
    )
  }
}