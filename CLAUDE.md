# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A multi-plugin Gradle repository publishing standalone Gradle plugins under the group `com.rohittp.plugables`. Published to GitHub Packages at `https://maven.pkg.github.com/rohittp0/plugables`.

## Common Commands

```bash
# Build all plugins
./gradlew build

# Build a specific plugin
./gradlew :<plugin-name>:build

# Run tests for all plugins
./gradlew test

# Run tests for a specific plugin
./gradlew :<plugin-name>:test

# Run a single test class
./gradlew :<plugin-name>:test --tests "com.rohittp.plugables.<plugin-name>.<TestClass>"

# Publish a plugin to GitHub Packages (requires GITHUB_ACTOR and GITHUB_TOKEN env vars)
./gradlew :<plugin-name>:publish

# Publish all plugins
./gradlew publish
```

## Architecture

The root project applies shared config only — no plugin logic lives at the root. Each plugin is a fully self-contained subproject with its own `build.gradle.kts` and versioning.

**Plugin structure (per subproject):**
- `<Name>Plugin.kt` — implements `Plugin<Project>`, calls `extensions.create(...)` then `tasks.register(...)`
- `<Name>Extension.kt` — abstract class with `@Inject constructor(objects: ObjectFactory)` for DSL configuration
- Task files — one class per task, registered in the plugin class

**Plugin ID pattern:** `com.rohittp.plugables.<plugin-name>`  
**Implementation class pattern:** `com.rohittp.plugables.<plugin-name>.<Name>Plugin`

## Adding a New Plugin

1. Create `<plugin-name>/` directory with `build.gradle.kts` and `src/main/kotlin/com/rohittp/plugables/<plugin-name>/`
2. Add `include(":<plugin-name>")` to `settings.gradle.kts`
3. Implement Plugin, Extension, and task classes following the patterns above
4. Set `version = "x.y.z"` in the subproject's `build.gradle.kts`

## Key Conventions

- JVM target: 21 (set via `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }`)
- Each plugin sets its own version in its `build.gradle.kts`; root project never sets plugin versions
- Repositories (`google()`, `mavenCentral()`) are configured in root `subprojects {}` block — do not redeclare them in subprojects
- Publishing credentials come exclusively from `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables