# Plugables — Domain Context

A monorepo of standalone Gradle plugins. Each plugin is independent — there is
no shared domain across plugins. This file collects terminology that
spans more than one source file inside a single plugin, so future readers
(human or model) don't have to re-derive it from code.

Add a section per plugin as terminology stabilises. Skip plugins whose
language is fully self-explanatory.

## auto-assert

Bytecode instrumentation plugin that injects a static assertion call at
the entry of every non-skipped method on annotated classes.

| Term | Meaning |
|------|---------|
| **Target class** | A class annotated with `@AssertForAllCalls`. Triggers instrumentation. |
| **Asserter** | The class supplied via `klass = ...` on `@AssertForAllCalls`. Must expose a `@JvmStatic` or Java `static` method named `method` with descriptor `()V`. |
| **Assertion call** | The `INVOKESTATIC owner.method ()V` instruction emitted at every Host method entry. |
| **Host method** | A declared method on a Target class that receives an Assertion call at its entry — i.e. it is not in the built-in skip list and not annotated `@NoAssert`. |
| **Excluded method** | A method on a Target class that does NOT get an Assertion call — either because of the built-in skip list (constructors, synthetic/bridge, `lambda$`, `equals`/`hashCode`/`toString`, field accessors, Kotlin property accessors) or because it is annotated `@NoAssert`. |

### Non-goals (intentional limitations)

- **No inheritance.** Subclasses are not automatically Target classes; each class must annotate itself.
- **No asserter arguments.** The Asserter method must be `()V`. Caller context (host class/method name) is not passed.
- **No multi-module duplicate-class detection.** Apply the plugin in a single module, or in one shared base module everything else depends on, to avoid duplicate `AssertForAllCalls.class` files across modules.

## branchmark

Stamps the **debug** launcher icon with the current git branch. It reads the app's existing adaptive
launcher icon and generates only a banner overlay — it never asks the consumer to supply a separate
base or fallback image.

| Term | Meaning |
|------|---------|
| **Banner** | The overlay branchmark draws onto the foreground: a **ribbon** plus, for detectable branches, a **prefix glyph**. |
| **Ribbon** | The diagonal filled band across the **top-right** corner. Shows the uppercased branch suffix, or the **fallback text** ("DEBUG") for undetectable branches. |
| **Prefix glyph** | The color emoji drawn in the **lower-left** safe zone, chosen from the branch prefix via `emojiByPrefix`. Rendered from a bundled Twemoji color SVG (Batik), with a system-font fallback for unbundled emoji. Only drawn for detectable branches. |
| **Detectable branch** | A branch with at least two `/`-segments, read as `.../<type>/<name>` (e.g. `fix/login`, `john/fix/login`). Only the **final two segments** are used: the second-to-last is the **type/prefix** (chooses the emoji), the last is the **name** (the ribbon text). Leading segments are ignored. Produces the **dynamic** banner. |
| **Undetectable branch** | A slashless name (`main`), detached `HEAD`, or empty/unresolvable branch. Produces the **fallback** banner (fallback-text ribbon, no glyph). |
| **Configured icon** | The app's existing `mipmap-anydpi-v26/<launcherIconName>.xml` in `src/main/res`. branchmark parses its `<foreground>`/`<background>`/`<monochrome>`, rasterizes a **vector** foreground via Android's `VdPreview`, draws the banner, and emits a debug adaptive XML that re-points `<foreground>` at the stamped image while preserving background/monochrome. |
| **Safe zone** | The inner ~66% of the 108dp adaptive canvas that launcher masks never crop. The prefix glyph is inset ≥18% so it is never clipped; the ribbon corner intentionally bleeds, but its text stays inside. |

Branch resolution precedence: `branchOverride` → `-PgitBranch` → `GITHUB_HEAD_REF` → `GIT_BRANCH` →
`git rev-parse --abbrev-ref HEAD` (via a configuration-cache-safe `ValueSource`).

Configure additive emoji overrides with `emojiByPrefix.put("myprefix", "🌟")` — calling `.set(...)`
replaces the built-in convention map, though `EmojiResolver` still falls back to its internal defaults
for known prefixes.

### Non-goals (intentional limitations)

- **Adaptive foreground only.** On `minSdk < 26` devices the legacy square `mipmap-<dpi>/ic_launcher.png`
  is shown unstamped; branchmark only regenerates the adaptive-icon foreground (and its anydpi XML).
- **Consumer owns the base icon.** branchmark never edits the background color or the release icon, and
  does no runtime (in-app) icon switching.
- **CI checkouts must pass the branch.** Detached/shallow CI clones resolve to the fallback unless
  `-PgitBranch=$BRANCH` or `GIT_BRANCH` is supplied (e.g. `${{ github.head_ref || github.ref_name }}`).

## proto-extended

Generates Kotlin extension properties on proto enums. Everything it reads is a **custom proto option**,
never a comment — the plugin has no opinion the schema doesn't state explicitly.

Two independent generators, because their inputs live in different modules: the **metadata** half needs
only the protos and emits pure Kotlin into `commonMain`, while the **resource** half needs the module
that owns `R` and emits Android-only accessors. A KMP library module cannot see the consuming app's `R`,
so one generator could not serve both.

| Term | Meaning |
|------|---------|
| **Meta option** | An `extend google.protobuf.EnumValueOptions` field whose type is a message. Applied per **enum constant**. Each field of that message becomes one **metadata property**. |
| **Metadata property** | A generated extension property on the enum, named for a meta-message field, mapping every constant to that field's value — e.g. `val AspectRatio.width: Int`. Pure Kotlin, so it compiles for every KMP target. |
| **Resource option** | The `extend google.protobuf.EnumOptions` field (`gen.resources`) carrying `ResourceGen` flags. Applied per **enum**, not per constant. Replaces the buildSrc task's `// gen:string` comment directive. |
| **Resource flag** | A `bool` field of `ResourceGen`. Named for the Kotlin property it generates (`display_name`, `icon`), not the Android resource folder, since the property name is what stays stable across platforms. |
| **Resource accessor** | A generated `@get:StringRes val X.displayName: Int` or `@get:DrawableRes val X.icon: Int`, resolving `R.string.<constant>` / `R.drawable.<constant>` (constant name lowercased). Android only. |
| **Reserved name** | A meta-message field name that would collide with an existing member of the generated enum: `name` and `ordinal` (Kotlin `Enum`) or `value` (Wire's `WireEnum`). A build error. |

Package resolution for generated imports follows Wire's own `KotlinGenerator` precedence —
`wire_package` → `java_package` → proto package. A nested enum is referenced through its enclosing
message (`Distance.Unit`) and imports that message, never the nested enum itself, which would shadow
`kotlin.Unit`.

### Non-goals (intentional limitations)

- **Android is the only generated platform.** Wire emits plain Kotlin enums, so Swift already has
  `Enum.name` for `Image(ratio.name)` and `String(localized:)`. Android is generated because
  `R.string."intro_default"` is impossible without `Resources.getIdentifier()`. Consequence: iOS asset
  keys must be uppercase to match `Enum.name`, and verifying they exist is the iOS developer's job.
- **Absent values fail the build, never default.** proto3 scalars have no field presence, so a
  defaulted `0` is indistinguishable from a set `0`. If any constant of an enum carries a meta option,
  all must, and every field it uses must be set on all of them.
- **Scalar meta fields only.** `bytes`, `repeated`, `map`, message- and enum-typed meta fields are a
  build error rather than being silently stringified.
- **`oneof` meta fields are silently dropped.** `MessageType.declaredFields` excludes `oneof` members,
  so a `oneof` inside a meta message generates nothing and reports nothing — unlike the other
  unsupported shapes above, this one does not fail the build.
- **Wire Kotlin codegen is assumed.** protobuf-lite and pbandk produce different class names.
