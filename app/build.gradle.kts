import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ch.gryphus.callsimulator"
    // core-ktx 1.19.x requires compiling against API 37+ (AAR metadata check).
    compileSdk = 37

    defaultConfig {
        applicationId = "ch.gryphus.callsimulator"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Produce JaCoCo coverage from JVM unit tests (consumed by SonarCloud).
            enableUnitTestCoverage = true
        }
        release {
            // Enable R8: code shrinking + obfuscation + resource shrinking.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // Lint runs in its own JVM and requires that JVM to be >= the project's
    // target compatibility (now 21). The daemon JDK is irrelevant to this; the
    // toolchain below makes Lint provision a JDK 21 of its own, so the task
    // passes even when Gradle itself runs on JDK 17 (GitHub default-setup
    // CodeQL autobuild does exactly that).
    lint {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    // Modern replacement for the deprecated android.kotlinOptions {} DSL
    // (removed/erroring under Kotlin 2.4 + AGP 9).
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }

    // Provision a Java 21 toolchain for ALL JVM work (javac, Kotlin, Lint),
    // independent of whichever JDK runs the Gradle daemon. Locally the daemon
    // is temurin-21 (mise), but GitHub's default-setup CodeQL autobuild drives
    // Gradle on its own bundled JDK 17 - a JDK 17 javac cannot emit Java 21
    // bytecode ("invalid source release: 21"), and Lint refuses to run below
    // the target version. The toolchain makes Gradle fetch/use a JDK 21 for
    // those tasks regardless of the daemon, so the build is correct on any
    // launcher. Auto-provisioning is served by the foojay resolver configured
    // in settings.gradle.kts.
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
}
