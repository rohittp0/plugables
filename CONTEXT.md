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
