// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Security: pin patched versions of vulnerable transitive dependencies on the
// BUILD (buildscript/plugin) classpath. They arrive via the Android Gradle
// Plugin's tooling (bundletool, sdklib, apksig, manifest/XML parsing, SDK
// downloader) and are build-time only - they never ship in the APK, so there is
// no runtime exposure in the shipped app. We still force the fixed versions to
// clear the Dependabot alerts and keep the toolchain current. AGP 9.4.1 is the
// latest stable, so bumping AGP is not an option; forcing the transitives is the
// supported remediation. CVEs addressed:
//   bouncycastle 1.86    (>= 1.85 Name-Constraints bypass + ASN.1 nesting-depth;
//                          >= 1.84 LDAP injection + risky crypto algorithm)
//   jose4j        0.9.7   (>= 0.9.6 DoS via compressed JWE)
//   jdom2         2.0.6.1 (XXE injection)
//   commons-lang3 3.21.0  (>= 3.18.0 uncontrolled recursion)
//   httpclient    4.5.14  (>= 4.5.13 cross-site scripting)
//   kotlin-gradle-plugin 2.4.21 (>= 2.4.20-Beta1 unsafe build-cache
//                          deserialization). Pinned to 2.4.21 to match the
//                          Kotlin version the app already compiles with, so the
//                          build toolchain stays internally consistent with AGP.
buildscript {
    configurations.classpath {
        resolutionStrategy {
            force(
                "org.bouncycastle:bcprov-jdk18on:1.86",
                "org.bouncycastle:bcpkix-jdk18on:1.86",
                "org.bouncycastle:bcutil-jdk18on:1.86",
                "org.bitbucket.b_c:jose4j:0.9.7",
                "org.jdom:jdom2:2.0.6.1",
                "org.apache.commons:commons-lang3:3.21.0",
                "org.apache.httpcomponents:httpclient:4.5.14",
                "org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21",
                "org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.4.21"
            )
        }
    }
}

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