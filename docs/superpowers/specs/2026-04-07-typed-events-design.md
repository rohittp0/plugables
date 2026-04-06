# Typed Events Plugin — Design Spec

**Date:** 2026-04-07  
**Status:** Approved

## Overview

A Gradle plugin (`com.rohittp.plugables.typed-events`) that reads an analytics event schema from a YAML file and generates an abstract `AnalyticsBase` Kotlin class. The user subclasses `AnalyticsBase`, overrides `logEvent`, and gets fully-typed methods for every event defined in the YAML.

---

## Plugin Identity

| Field | Value |
|-------|-------|
| Plugin ID | `com.rohittp.plugables.typed-events` |
| Implementation class | `com.rohittp.plugables.typedevents.TypedEventsPlugin` |
| Gradle subproject | `typed-events/` |
| Group | `com.rohittp.plugables` |

---

## DSL

```kotlin
typedEvents {
    specFile.set(file("src/main/analytics/events.yaml"))  // required — no default
}
```

Only one required property. All other configuration is fixed.

---

## YAML Schema

Events are defined as a YAML list. Two forms are supported:

**Full form** (with params):
```yaml
- purchase_completed:
    info: User completed a purchase
    function: logPurchaseCompleted  # optional — auto-derived from event_name if omitted
    params:
      item_id:
        type: String
        info: The purchased item ID
      price:
        type: Double
        info: Final price paid
```

**Shorthand form** (no params):
```yaml
- screen_viewed: User viewed a screen
```

### Rules
- `info` is required on every event and every param.
- `function` is optional; if omitted, the method name is derived from `event_name` by camel-casing with a `log` prefix (e.g., `purchase_completed` → `logPurchaseCompleted`).
- `type` must be a valid Kotlin type string (e.g., `String`, `Double`, `Int`, `Boolean`, `String?`).
- Event names and derived function names must be unique within the file.

---

## Generated Output

**Fixed location:** `com/rohittp/plugables/analytics/AnalyticsBase.kt`  
**Fixed package:** `com.rohittp.plugables.analytics`  
**Fixed class name:** `AnalyticsBase`  
**Output root:** `build/generated/source/typedEvents/main` (wired into AGP `main` source set)

### Example

Given the YAML above, the generated file is:

```kotlin
package com.rohittp.plugables.analytics

// GENERATED FILE. Do not edit.
// Source: events.yaml

abstract class AnalyticsBase {

    protected open fun logEvent(eventName: String, params: Map<String, Any?>) = Unit

    /**
     * User completed a purchase
     * @param itemId The purchased item ID
     * @param price Final price paid
     */
    fun logPurchaseCompleted(itemId: String, price: Double) {
        logEvent("purchase_completed", mapOf("item_id" to itemId, "price" to price))
    }

    /** User viewed a screen */
    fun logScreenViewed() {
        logEvent("screen_viewed", emptyMap())
    }
}
```

### Param name sanitisation

YAML param keys (e.g., `item_id`) are converted to camelCase Kotlin identifiers (e.g., `itemId`) using the same algorithm as the reference project:
- Split on non-alphanumeric characters
- Lowercase the first segment, title-case the rest
- Prefix `_` if the result starts with a digit or is empty

---

## File Structure

```
typed-events/
  build.gradle.kts
  src/main/kotlin/com/rohittp/plugables/typedevents/
    TypedEventsPlugin.kt       # Plugin<Project> — wires extension, task, AGP source set
    TypedEventsExtension.kt    # DSL: specFile + outputDir (with defaults)
    GenerateTypedEventsTask.kt # DefaultTask — orchestrates parse → validate → render
    EventSchema.kt             # Data classes: EventSpec, ParamSpec
    YamlParser.kt              # Parses full + shorthand YAML forms via SnakeYAML
    ClassRenderer.kt           # Renders AnalyticsBase.kt source string
```

---

## Task & Plugin Wiring

- Task name: `generateTypedEvents`
- Runs before all `compile*Kotlin` tasks (same pattern as `viewmodel-stub`)
- Output dir added to AGP `main` source set via `srcDirs(provider)` (config-cache safe)
- If AGP is absent: logs a warning and skips source set wiring (does not fail the build)

---

## Validation

The task fails with a clear error message if:

| Condition | Error |
|-----------|-------|
| `specFile` not set | "typedEvents.specFile is required" |
| Spec file does not exist | "Spec file not found: <path>" |
| Duplicate event names | "Duplicate event name: <name>" |
| Duplicate function names | "Duplicate function name: <name>" |
| Param `type` missing (full form) | "Param '<name>' in '<event>' is missing 'type'" |
| `info` missing on event or param | "Event/Param '<name>' is missing 'info'" |
| Invalid derived Kotlin identifier | "Cannot derive valid Kotlin identifier from event name: <name>" |

---

## Dependencies

- **SnakeYAML** — already on Gradle's classpath; no extra `build.gradle.kts` dependency needed.
- **AGP** — `compileOnly("com.android.tools.build:gradle:8.5.0")`, same as `viewmodel-stub`.

---

## Usage Example (consuming project)

```kotlin
// build.gradle.kts
plugins {
    id("com.rohittp.plugables.typed-events") version "1.0.0"
}

typedEvents {
    specFile.set(file("src/main/analytics/events.yaml"))
}
```

```kotlin
// MyAnalytics.kt
class MyAnalytics : AnalyticsBase() {
    override fun logEvent(eventName: String, params: Map<String, Any?>) {
        FirebaseAnalytics.getInstance(context).logEvent(eventName, params.toBundle())
    }
}
```