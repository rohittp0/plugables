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

Items 1 and 2 move from the comment directive to a real custom enum option (see
[generateProtoAndroidResources](#generateprotoandroidresources)); this is the
one breaking change for existing protos.

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
    }
}

// app/build.gradle.kts — the Android module that owns strings/drawables
protoExtended {
    androidResources {
        protoDir.set(isolated.rootProject.projectDirectory.dir("shared/src/commonMain/proto"))
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

Reads a **custom enum option**, not a comment. This replaces the buildSrc task's
`// gen:string` / `// gen:drawable` / `// gen:string+drawable` leading-comment
directives, which protoc never sees and nothing validates — a typo like
`// gen:sting` silently generates nothing and surfaces at the call site.

The consumer declares the extension once, in the house style already set by
`mcp_options.proto`:

```proto
// gen_options.proto
syntax = "proto3";
package gen;
import "google/protobuf/descriptor.proto";

message ResourceGen {
  bool display_name = 1;   // generate `val X.displayName: Int` -> R.string.*
  bool icon = 2;           // generate `val X.icon: Int`        -> R.drawable.*
}

extend google.protobuf.EnumOptions {
  optional ResourceGen resources = 50100;
}
```

and applies it per enum:

```proto
import "gen_options.proto";

enum AspectRatio {
  option (gen.resources) = { display_name: true, icon: true };
  RATIO_1_1 = 0 [(aspect_ratio_meta) = { width: 1, height: 1, scale: 0.5 }];
  …
}
```

This extends `EnumOptions` (the enum itself), not `EnumValueOptions` (its
constants) — the difference from the three existing `*_meta` extensions.
Extension field numbers only need uniqueness per extended type, so 50100 does
not collide with `mcp.meta`'s 50000 or `*_meta`'s 50001-50003; it is chosen only
to stay visually distinct.

Flags are named for the **generated Kotlin property** (`display_name`, `icon`)
rather than the Android resource folder (`string`, `drawable`), because the
property name is the part that is stable now that the plugin is multiplatform-
aware. `bool string = 1;` would also be a poor field name.

Reading it is structurally identical to the metadata half, one descriptor level
up — `Options.ENUM_OPTIONS` instead of `Options.ENUM_VALUE_OPTIONS`, and
`EnumType.options` instead of `EnumConstant.options`. Both verified present in
wire-schema 6.4.5. `parseGenDirective` and its `documentation` string-munging
are deleted outright.

Resource name is `constant.name.lowercase()`.

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
generate — no enum carries a resource option, or no enum carries a metadata
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

Enum `ta.AspectRatio` declares (aspect_ratio_meta) on 2 of 3 constants. Missing on:
  - RATIO_16_9

Every constant must set the option, or none.
```

Rules:

1. **All-or-nothing option presence** — if any constant of an enum carries the
   option, all constants must.
2. **All-or-nothing field presence** — a field set on some constants but not
   others fails. A field set on *no* constant is not generated, *provided its
   type is one Rule 4 supports* — type-checking runs unconditionally on every
   declared field before presence is counted, so an unsupported-typed field
   still fails the build even when no constant sets it.
3. **Reserved names** — a meta field named `name` or `ordinal` (Kotlin `Enum`
   members) or `value` (Wire's `WireEnum.value`), or two different meta messages
   contributing the same field name to the same enum.

   These three specifically, because an extension property cannot shadow a real
   member: the member silently wins and the generated property becomes dead code
   with no error anywhere. That is the hazard being guarded against. A meta field
   named `displayName` or `icon` colliding with a resource accessor is *not*
   reserved — that produces a loud "conflicting overloads" compile error, and only
   when both blocks share a `basePackage`, which they do not in travel-animator.
   Documented, not enforced.
4. **Unsupported field types** — `bytes`, `repeated`, `map`, message-typed and
   enum-typed meta fields fail. The buildSrc task has an `else -> "String"`
   fallback that silently stringifies these into plausible-looking but wrong
   properties.
5. **Unknown resource flags** — a `ResourceGen` field the plugin does not
   recognise fails, listing the supported flags. Protoc already rejects a
   misspelled flag, so this only catches a consumer who adds a field the plugin
   has no meaning for.

Verified against travel-animator's current schema: all three metadata-bearing
enums already satisfy rules 1 and 2 — `AspectRatio` (3/3 constants, all of
`width`/`height`/`scale`), `Resolution` (2/2, `pixels`/`label`), `Distance.Unit`
(3/3, `symbol`/`multiplier`). The strict rules cost nothing today; they only
catch future drift.

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

All wiring is lazy via `plugins.withId`.

| Consumer plugin | `generateProtoMetadata` → | `generateProtoAndroidResources` → |
|---|---|---|
| `org.jetbrains.kotlin.multiplatform` | `kotlin.sourceSets["commonMain"].kotlin.srcDir(task)` | `sourceSets["androidMain"]` |
| `org.jetbrains.kotlin.jvm` | `sourceSets["main"]` | — |
| `com.android.application` / `com.android.library` | variant API | `variant.sources.kotlin?.addGeneratedSourceDirectory(...)` |

All five branches ship, even though travel-animator only exercises two (`metadata`
→ KMP `commonMain` in `shared`, `androidResources` → AGP variants in `app`). With
no automated wiring tests, the risk is not a crash but a silent no-op: the task
runs, writes a valid `.kt` file, and nothing compiles it — you get
`Unresolved reference: displayName` at the call site while the generator reports
success. Trimming the surface would not help, since a consumer with no recognised
plugin hits the same no-op.

So the failure mode is fixed instead. Each `plugins.withId` branch flips a `wired`
flag; if a block was configured and nothing wired it, the plugin warns:

```
w: protoExtended { metadata { … } } is configured, but nothing wired it into
   a source set. Generated sources in
   build/generated/source/protoExtended/metadata are not on any source set.
```

This is the one permitted `afterEvaluate`, used purely as a diagnostic — no task
graph depends on it, so the ordering fragility that makes `afterEvaluate` a smell
does not apply. A warning rather than an error, so a consumer deliberately wiring
the srcDir by hand is not blocked.

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

**TestKit** — mirroring `BranchmarkTestKitTest`: both tasks execute and write
their file, a second run is `UP_TO_DATE`, `--configuration-cache` is reused, and
an unconfigured block reports `NO-SOURCE`. The fixture applies only this plugin —
no AGP, no KGP, no network.

**No automated wiring tests.** This follows the established precedent rather than
departing from it: `BranchmarkTestKitTest` says in its own doc comment that it
drives the task *without* AGP, and `TypedEventsPlugin` wires both AGP variants
and `compile*Kotlin` with no wiring test at all.

The reasoning is specific to this repo's CI. `.github/workflows/publish.yml` runs
`./gradlew :<plugin>:build :<plugin>:publishToMavenCentral`, and `build` depends
on `check` — so **tests are the publish gate**, and that gate fires exactly when a
`version = "…"` line changes. A ProjectBuilder test that applies
`org.jetbrains.kotlin.multiplatform` is the flakiest thing that could sit there:
KMP does substantial eager work at apply time and `kotlin.sourceSets` is not
predictable until a target is declared, which pulls in a real toolchain.

Source-set wiring is instead verified by hand against travel-animator during
rollout.

## Repo housekeeping

- `include(":proto-extended")` in `settings.gradle.kts`
- `gradlePlugin` and `mavenPublishing.pom` blocks copied from typed-events
- `docs/proto-extended.html`, plus entries in `docs/index.html`,
  `docs/sitemap.xml` and `docs/llms.txt`
- A `CONTEXT.md` section covering the terms that span files: *meta option spec*,
  *gen directive*, *metadata property*, *reserved name*

## Consumer migration

One breaking change relative to the buildSrc task. In travel-animator:

1. Add `shared/src/commonMain/proto/gen_options.proto` as above.
2. `animation_style.proto` — add `import "gen_options.proto";` and replace the
   `// gen:string` comment on `Intro`, `Running` and `Outro` with
   `option (gen.resources) = { display_name: true };`.
3. `animation_state.proto` — add the same import (it already imports
   `descriptor.proto`), then replace `// gen:string+drawable` on `AspectRatio`
   with `{ display_name: true, icon: true }`, and `// gen:string` on `Resolution`
   and `Distance.Unit` with `{ display_name: true }`.
4. Delete `buildSrc/src/main/kotlin/com/lascade/proto/GenerateProtoExtensionsTask.kt`
   and the `tasks.register<GenerateProtoExtensionsTask>` block plus its
   `androidComponents.onVariants` and `preBuild` wiring in `app/build.gradle.kts`.

Metadata options are untouched — the three `*_meta` extensions and every
`[(…_meta) = { … }]` on a constant stay exactly as they are.

## Non-goals for v1

- **No comment-directive fallback** — the `// gen:` form is not supported
  alongside the option. Supporting both would mean two code paths and two ways
  to express the same thing, and the migration is a handful of lines.
- **No resource-existence validation** — Android's compiler already catches it.
- **No iOS or linux output** — `Enum.name` already gives Swift what it needs.
- **No enum- or message-typed metadata fields** — enum-typed is the obvious
  future addition.
- **No configurable property names** — `displayName` and `icon` stay fixed.
- **Wire Kotlin codegen is assumed** — protobuf-lite and pbandk generate
  different class names and are out of scope.
- **Wire is assumed to generate every enum on the proto path** — a consumer using
  Wire's `prune` or `exclude` to drop an enum still gets extension properties
  generated for it, which then fail to compile against a class that does not
  exist. Both generators read `.proto` sources directly and never inspect Wire's
  output.
- **No configurable `sourceSet`** — dropped from the approved design. `plugins.withId`
  fires during `apply()`, before the consumer's `protoExtended { }` block runs, so the
  property is always unset when the wiring needs it. Source sets are hardcoded:
  `commonMain` (KMP metadata), `main` (Kotlin JVM metadata), `androidMain` (KMP
  resources). Wire a different one by hand with
  `kotlin.srcDir(tasks.named("generateProtoMetadata"))`; the unwired diagnostic
  tolerates it.

## Task ordering

Neither task depends on Wire's codegen task, and no ordering is needed. Both read
`.proto` sources directly, so their outputs are just additional source dirs on the
same source set as Wire's — the Kotlin compiler resolves the cross-references when
it compiles them together. There is no cycle and no `mustRunAfter`.
