# Versioning

This app uses [Semantic Versioning](https://semver.org): **MAJOR.MINOR.PATCH**,
baselined at **1.0.0**.

The version is a single source of truth: `appVersionName` in
[`app/build.gradle.kts`](app/build.gradle.kts). `versionCode` is derived from it
(`MAJOR*10000 + MINOR*100 + PATCH`), and the built APK is named
`CallSimulator-<version>-<buildType>.apk`
(e.g. `CallSimulator-1.3.6-release.apk`).

## When to bump

Bump `appVersionName` on every change merged to `main`, by the highest-impact
change in the PR:

| Change | Bump | Example |
| -------- | ------ | --------- |
| Breaking change — removed/renamed user-facing behaviour, data-format break, raised `minSdk` | **MAJOR** | `feat!:` / a commit with `BREAKING CHANGE:` |
| New backward-compatible feature | **MINOR** | background-call service, scheduled calls |
| Bug fix, build/CI/config fix, refactor, chore, docs | **PATCH** | `fix(sonar): …`, `chore: …`, namespace rename |

### Dependabot updates

Classify by what the dependency change affects, not merely by its own version jump:

- **Build-tooling / test-only bump** (AGP, Gradle wrapper, Kotlin, lint, CI
  actions) — no change to what ships on-device → **PATCH**.
- **Security fix** (any Dependabot security alert) → at least **PATCH**.
- **Shipped-runtime library bump that adds app capability** (a minor/feature
  bump of a dependency whose code runs in the APK) → **MINOR**.
- **Shipped-runtime library major bump that changes/removes behaviour the app
  relies on** → **MAJOR**.

Rule of thumb: if it only affects how the app is *built or tested*, it's a
PATCH; if it changes what the app *does on a user's device*, treat it like a
feature (MINOR) or a breaking change (MAJOR).

## How the current version was derived

Starting from `1.0.0`, each PR merged to `main` (in merge order):

| PR | Change | Type | Version |
| ---- | -------- | ------ | --------- |
| #1 | Add CI workflow | ci → PATCH | 1.0.1 |
| #2 | Add Dependabot workflow | ci → PATCH | 1.0.2 |
| #8 | Resolve Dependabot updates | dep (build) → PATCH | 1.0.3 |
| #9 | Clean up UI text content | fix → PATCH | 1.0.4 |
| #10 | Clear Gradle build warnings | chore → PATCH | 1.0.5 |
| #11 | Upgrade build JDK 17 → 21 | chore → PATCH | 1.0.6 |
| #14 | Fix SonarCloud issues on main | fix → PATCH | 1.0.7 |
| #15 | Skip Sonar scan when token absent | ci → PATCH | 1.0.8 |
| #13 | Bump constraintlayout 2.2.0 → 2.2.2 | dep (runtime, patch) → PATCH | 1.0.9 |
| #12 | Bump material 1.12.0 → 1.14.0 | dep (runtime, minor) → MINOR | 1.1.0 |
| #16 | Rename namespace com.example → ch.gryphus | refactor → PATCH | 1.1.1 |
| #17 | Keep calls alive in background (foreground service) | feat → MINOR | 1.2.0 |
| #18 | Schedule a simulated call | feat → MINOR | 1.3.0 |
| #19 | Fix 9 Dependabot alerts | fix (security) → PATCH | 1.3.1 |
| #20 | Full-size Calling Card photo | fix → PATCH | 1.3.2 |
| #21 | Bump gradle-wrapper 9.8.1 → 9.8.2 | dep (build) → PATCH | 1.3.3 |
| #22 | Bump AGP 9.4.1 → 9.5.0 | dep (build) → PATCH | 1.3.4 |
| #23 | Clean up README files | docs → PATCH | 1.3.5 |
| #24 | Sync JVM bytecode target 17 → 21 | fix → PATCH | 1.3.6 |

**Current version: `1.3.6`** (`versionCode 10306`).

> Note: #12 (Material Components) is a *shipped-runtime* UI library whose minor
> bump adds capability, so it's a MINOR. #13 (ConstraintLayout) is a patch bump
> of a runtime lib → PATCH. All AGP/Gradle/Kotlin/JDK/CI changes are build-only
> → PATCH.
