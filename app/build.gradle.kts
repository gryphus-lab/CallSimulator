import com.android.build.api.variant.impl.VariantOutputImpl
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// --- Semantic version (single source of truth) ------------------------------
// MAJOR.MINOR.PATCH, baselined at 1.0.0 and advanced per merged PR using the
// policy in VERSIONING.md:
//   feat  -> MINOR     fix/chore/ci/docs/refactor -> PATCH
//   breaking (feat!/BREAKING CHANGE) -> MAJOR
//   dependabot: build-tooling/patch/security bump -> PATCH; a shipped-runtime
//   bump that adds app capability -> MINOR; a breaking runtime bump -> MAJOR.
// Current value derived from merged PRs #1..#24 (see VERSIONING.md). Bump this
// line on every release.
val appVersionName = "1.3.6"

// Monotonic integer derived from the semantic version: MAJOR*10000 +
// MINOR*100 + PATCH. Always increases as the semver does, keeping Android's
// install/upgrade check happy.
val appVersionCode = appVersionName.split(".").let { (maj, min, patch) ->
    maj.toInt() * 10000 + min.toInt() * 100 + patch.toInt()
}

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
        versionCode = appVersionCode
        versionName = appVersionName

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

// --- APK naming -------------------------------------------------------------
// Rename the default "app-<buildType>.apk" to "<project>-<version>-<buildType>.apk"
// (e.g. CallSimulator-1.3.6-debug.apk / -release.apk) so every artifact
// self-identifies by app + semantic version. Uses the AGP variant API.
androidComponents {
    onVariants { variant ->
        val baseName = rootProject.name // "CallSimulator"
        variant.outputs.forEach { output ->
            (output as? VariantOutputImpl)?.outputFileName?.set(
                "$baseName-$appVersionName-${variant.buildType}.apk",
            )
        }
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
