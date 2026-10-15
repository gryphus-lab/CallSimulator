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
  }
}