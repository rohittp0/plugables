# proto-extended — Design

**Date:** 2026-08-09
**Status:** Approved, ready for implementation planning

## Summary

A new Gradle plugin in the plugables monorepo that generates Kotlin extension
properties from proto enum definitions. It is a Kotlin Multiplatform-compatible
successor to `GenerateProtoExtensionsTask.kt`, which currently lives in
travel-animator-android's `buildSrc/` and only works for Android.

The plugin ships **two independent generators** under one extension. Each is
applied in whichever module owns the input it needs.

| Block | Task | Output file | Requires | Wired into |
|---|---|---|---|---|
| `metadata { }` | `generateProtoMetadata` | `ProtoEnumMetadata.kt` | only the protos | `commonMain` — pure Kotlin, compiles for android/ios/linux |
| `androidResources { }` | `generateProtoAndroidResources` | `ProtoEnumResources.kt` | the module owning `R` | Android variant sources |

## Motivation

The existing buildSrc task generates three things from proto enums:

1. `displayName` (`@StringRes` → `R.string.*`) for enums marked `// gen:string`
2. `icon` (`@DrawableRes` → `R.drawable.*`) for enums marked `// gen:drawable`
3. Typed metadata properties from `extend google.protobuf.EnumValueOptions`

Item 3 is pure Kotlin and belongs in `commonMain` next to the Wire output.
Items 1 and 2 reference Android resource IDs and cannot exist in `commonMain`.

### Why nothing is generated for iOS or linux

Wire generates proto enums as plain Kotlin enums, so `Enum.name` is already
available to Swift through the exported framework. The iOS app writes
`Image(ratio.name)` and `String(localized: intro.name)` directly — generated
code adds nothing.

Android is the only platform that genuinely needs generation, because
`R.string."intro_default"` is impossible without `Resources.getIdentifier()`,
which is slow and breaks under R8.

Consequence the consumer must accept: Android resource names are lowercase
(`ratio_1_1`) while `Enum.name` is uppercase (`RATIO_1_1`), so iOS asset
catalog keys must be uppercase to match. Verifying those keys exist is the iOS
developer's manual responsibility.

### Why the split is across modules, not just source sets

In travel-animator, `shared/` is the KMP library (namespace
`com.lascade.ta.shared`) holding the protos and Wire output, but
`R.string.intro_default` belongs to the **app** module
(`com.travelanimator.routemap`). A KMP library module cannot see the app's `R`.
Two independent blocks let each module configure only what it can satisfy.

## Plugin identity

Follows the repo convention documented in `CLAUDE.md`.

- Directory: `proto-extended/`
- Plugin id: `com.rohittp.plugables.proto-extended`
- Implementation class: `com.rohittp.plugables.protoextended.ProtoExtendedPlugin`
- Kotlin package: `com.rohittp.plugables.protoextended`
- Version: `1.0.0`

## Consumer DSL

```kotlin
// shared/build.gradle.kts — the KMP module, next to the Wire output
protoExtended {
    metadata {
        protoDir.set(layout.projectDirectory.dir("src/commonMain/proto"))
        basePackage.set("com.lascade.ta.shared.generated")
        sourceSet.set("commonMain")   // optional; see defaults below
    }
}

// app/build.gradle.kts — the Android module that owns strings/drawables
protoExtended {
    androidResources {
        protoDir.set(rootProject.layout.projectDirectory.dir("shared/src/commonMain/proto"))
        basePackage.set("com.travelanimator.routemap.generated")
        rPackage.set("com.travelanimator.routemap")
    }
}
```

A block that is never invoked leaves its task with no proto inputs, so the task
reports `NO-SOURCE` and is skipped. There is no "you must configure both" trap
and no `afterEvaluate`.

### Task input/output properties

Both tasks share this input shape:

| Property | Annotation | Notes |
|---|---|---|
| `protoDir` | `@Internal` | Only needed as the `SchemaLoader` root |
| `protoFiles` | `@InputFiles @SkipWhenEmpty @PathSensitive(RELATIVE)` | Derived internally from `protoDir` as `**/*.proto`; not part of the consumer DSL. Drives up-to-date checks and the skip-when-unconfigured behaviour |
| `basePackage` | `@Input` | Package of the generated file |
| `outputDir` | `@OutputDirectory` | Defaults to `build/generated/source/protoExtended/metadata` and `…/androidResources` respectively |

`generateProtoAndroidResources` adds `rPackage` (`@Input`).

Both blocks expose an optional `sourceSet` property. It is only consulted for
Kotlin Multiplatform and Kotlin JVM consumers; the AGP variant API path ignores
it, since variant sources are not addressed by source-set name.

`protoDir` is `@Internal` rather than `@InputDirectory` deliberately: a required
`@InputDirectory` fails at input-snapshot time when unconfigured, which would
defeat `NO-SOURCE` skipping. `protoFiles` carries the real input tracking.

Neither task references `Project` at execution time, so both are configuration
cache compatible.

## Internal structure

The buildSrc original is a 317-line monolith mixing schema loading, validation,
string building and file IO. This plugin splits parse → model → render,
mirroring typed-events' `YamlParser` / `ClassRenderer` / task split. The
renderers take plain data, so their tests need neither Gradle nor a filesystem.

```
proto-extended/
  build.gradle.kts
  src/main/kotlin/com/rohittp/plugables/protoextended/
    ProtoExtendedPlugin.kt                  apply(), source-set wiring
    ProtoExtendedExtension.kt               metadata { } / androidResources { } blocks
    ProtoSchemaReader.kt                    wire-schema -> model (enum walk, package resolution)
    EnumModel.kt                            ProtoEnumInfo / MetaProperty / GenDirective — no Gradle, no Wire
    MetadataRenderer.kt                     model -> Kotlin source (pure function)
    AndroidResourceRenderer.kt              model -> Kotlin source (pure function)
    GenerateProtoMetadataTask.kt
    GenerateProtoAndroidResourcesTask.kt
  src/test/kotlin/com/rohittp/plugables/protoextended/
    ...
```

`EnumModel.kt` is the boundary: it depends on neither Gradle nor wire-schema, so
`ProtoSchemaReader` can be swapped or tested in isolation and the renderers stay
pure.

## Generation semantics

### generateProtoMetadata

Walks every proto file (skipping `google.protobuf.*`), collects top-level **and**
nested enums via `typesAndNestedTypes()`, discovers every
`extend google.protobuf.EnumValueOptions`, and emits one flat extension property
per field of each meta message that appears on the enum.

```kotlin
// commonMain — no Android, no androidx, compiles for ios/linux
val AspectRatio.width: Int get() = when (this) { … }
val AspectRatio.height: Int get() = when (this) { … }
val AspectRatio.scale: Double get() = when (this) { … }
val Distance.Unit.symbol: String get() = when (this) { … }
```

Scalar type mapping, carried over unchanged:

| Proto | Kotlin |
|---|---|
| `string` | `String` |
| `double` | `Double` |
| `float` | `Float` |
| `int32`, `uint32` | `Int` |
| `int64`, `uint64` | `Long` |
| `bool` | `Boolean` |

### generateProtoAndroidResources

Parses the existing leading-comment directives — `// gen:string`,
`// gen:drawable`, `// gen:string+drawable` — unchanged, so current protos work
without edits. Resource name is `constant.name.lowercase()`.

```kotlin
@get:StringRes
val Intro.displayName: Int get() = when (this) {
    Intro.INTRO_DEFAULT -> R.string.intro_default
    …
}

@get:DrawableRes
val AspectRatio.icon: Int get() = when (this) {
    AspectRatio.RATIO_1_1 -> R.drawable.ratio_1_1
    …
}
```

Imports `androidx.annotation.StringRes`, `androidx.annotation.DrawableRes`, and
`<rPackage>.R`.

Both generators emit a header-only file (banner comment plus `package`
declaration, no imports) when the protos parse successfully but yield nothing to
generate — no enum carries a `gen:` directive, or no enum carries a metadata
option. Writing the file unconditionally keeps the output directory a valid,
stable source root across incremental builds.

## Validation

All validation failures fail the build and name the enum, constant and field.

### Missing metadata values fail the build

The buildSrc task silently substitutes a type default (`0` / `""` / `false`) for
a constant that is missing an option its siblings have. proto3 scalars have no
field presence, so a defaulted `0` is indistinguishable at runtime from a
genuinely-set `0`. That is a silent wrong-value bug, so it now fails instead.

```
> Task :shared:generateProtoMetadata FAILED

Enum `AspectRatio` declares (aspect_ratio_meta) on 2 of 3 constants. Missing on:
  - RATIO_16_9

Every constant must set the option, or none.
```

Rules:

1. **All-or-nothing option presence** — if any constant of an enum carries the
   option, all constants must.
2. **All-or-nothing field presence** — a field set on some constants but not
   others fails. A field set on *no* constant is simply not generated (existing
   behaviour, and correct — nobody consumes it).
3. **Name collisions** — a meta field named `name` or `ordinal` (Kotlin `Enum`
   members) or `value` (Wire's `WireEnum.value`), or two different meta messages
   contributing the same field name to the same enum.
4. **Unsupported field types** — `bytes`, `repeated`, `map`, message-typed and
   enum-typed meta fields fail. The buildSrc task has an `else -> "String"`
   fallback that silently stringifies these into plausible-looking but wrong
   properties.

No resource-existence validation for the Android generator: a missing
`R.string.foo` is already a compile error, so a validator would only move the
error a few seconds earlier.

## Fixes carried into both generators

**Package resolution.** Follows Wire's own `KotlinGenerator` precedence —
`wire_package` → `java_package` → proto package. The buildSrc task only reads
`java_package`, so it silently emits wrong imports for any proto using
`wire_package`. Verified: `ProtoFile.wirePackage()` exists in wire-schema 6.4.5.

**Nested enums.** Keep the existing treatment — reference `Distance.Unit`,
import the outer `Distance`. Importing the nested `Unit` directly would shadow
`kotlin.Unit` and would not match the dotted reference.

**Deterministic output.** Enums sorted by qualified name, imports sorted. The
original follows schema file order, so unrelated proto reordering churns the
generated file and busts the build cache.

## Source-set wiring

All wiring is lazy via `plugins.withId`. `sourceSet` is left unset by default so
each branch supplies its own default.

| Consumer plugin | `generateProtoMetadata` → | `generateProtoAndroidResources` → |
|---|---|---|
| `org.jetbrains.kotlin.multiplatform` | `kotlin.sourceSets["commonMain"].kotlin.srcDir(task)` | `sourceSets["androidMain"]` |
| `org.jetbrains.kotlin.jvm` | `sourceSets["main"]` | — |
| `com.android.application` / `com.android.library` | variant API | `variant.sources.kotlin?.addGeneratedSourceDirectory(...)` |

`srcDir(taskProvider)` carries the task dependency itself. Unlike typed-events,
which additionally does
`tasks.matching { name.matches(Regex("compile.*Kotlin")) }.configureEach { dependsOn(...) }`,
no manual `dependsOn` is added. That is deliberate: the blanket regex also hooks
compile tasks unrelated to the generated sources.

## Dependencies

Verified against the local Gradle cache.

```kotlin
compileOnly("com.android.tools.build:gradle:9.2.0")            // AGP variant API
compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")  // KotlinMultiplatformExtension
implementation("com.squareup.wire:wire-schema:6.4.5")           // matches travel-animator
testImplementation(kotlin("test"))
testImplementation(gradleTestKit())
testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
```

`KotlinSourceSet` lives in `kotlin-gradle-plugin-api`, but `KotlinProjectExtension`
and `KotlinMultiplatformExtension` are only in `kotlin-gradle-plugin` proper — so
the full artifact is the required `compileOnly`, not the `-api` one.

## Testing

**Unit (no Gradle, no filesystem)** — the bulk of coverage:

- `MetadataRenderer` and `AndroidResourceRenderer` against golden strings
- `ProtoSchemaReader` over `.proto` fixtures in a temp dir
- One test per validation rule: partial option presence, partial field presence,
  reserved name (`name` / `ordinal` / `value`), cross-message field collision,
  unsupported field type
- Nested-enum `Distance.Unit` reference and import
- `wire_package` taking precedence over `java_package`
- Deterministic ordering: reordering input protos produces identical output

**ProjectBuilder** — apply KMP plus the plugin, assert the output dir lands in
`commonMain` srcDirs. Wiring is the riskiest part; this covers it without a
network build.

**TestKit** — mirroring `BranchmarkTestKitTest`: task executes and writes the
file, second run is `UP_TO_DATE`, `--configuration-cache` is reused, and an
unconfigured block reports `NO-SOURCE`. Like branchmark, the fixture applies only
this plugin, so no AGP or KGP resolution is needed at test time.

## Repo housekeeping

- `include(":proto-extended")` in `settings.gradle.kts`
- `gradlePlugin` and `mavenPublishing.pom` blocks copied from typed-events
- `docs/proto-extended.html`, plus entries in `docs/index.html`,
  `docs/sitemap.xml` and `docs/llms.txt`
- A `CONTEXT.md` section covering the terms that span files: *meta option spec*,
  *gen directive*, *metadata property*, *reserved name*

## Non-goals for v1

- **No resource-existence validation** — Android's compiler already catches it.
- **No iOS or linux output** — `Enum.name` already gives Swift what it needs.
- **No enum- or message-typed metadata fields** — enum-typed is the obvious
  future addition.
- **No configurable property names** — `displayName` and `icon` stay fixed.
- **Wire Kotlin codegen is assumed** — protobuf-lite and pbandk generate
  different class names and are out of scope.
