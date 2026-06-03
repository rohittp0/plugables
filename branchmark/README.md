# Branchmark

Stamps your Android app's **debug** launcher icon with the current **git branch**, so you can tell at
a glance which branch a debug build came from.

Branches are read as `.../<type>/<name>` — **only the final two `/`-segments matter**, so
`fix/login-crash`, `john/fix/login-crash`, and `team/john/fix/login-crash` all behave identically:

- A diagonal **ribbon** across the top-right corner shows the name uppercased (`LOGIN-CRASH`).
- A color **emoji** for the type (`fix` → 🔧) is drawn in the lower-left.

For a single-segment or undetectable branch (`main`, detached `HEAD`, no git), it draws a plain
**"DEBUG"** ribbon with no emoji — debug builds always look distinct from release.

Branchmark **reads the launcher icon your app already has** and generates only the banner overlay — no
base image to supply, no resources to migrate.

```
   ┌───────────────────┐        ┌───────────────────┐
   │              ╱DEBUG│        │              ╱ BUG │   ← suffix ribbon
   │   (your icon)      │        │   (your icon)      │
   │                    │        │  🔧                │   ← prefix emoji
   └───────────────────┘        └───────────────────┘
        branch: main                 branch: fix/bug
```

## How it works

1. Reads your `src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and resolves its `<foreground>`
   (vector foregrounds are rasterized with Android's own `VdPreview`).
2. Draws the ribbon (Java2D) and the prefix emoji (a bundled [Twemoji](https://github.com/jdecked/twemoji)
   color SVG, rasterized with Apache Batik) onto a copy of the foreground, per density.
3. Writes `mipmap-<dpi>/ic_launcher_foreground_debug.png` and a debug `mipmap-anydpi-v26/ic_launcher.xml`
   that points `<foreground>` at the stamped image while preserving your `<background>`/`<monochrome>`.
4. Wires the generated `res` into the debug variant via the AGP Variant API, so it overrides the icon
   for debug builds only.

## Setup

Apply the plugin in your app module (after `com.android.application`):

```kotlin
plugins {
    id("com.android.application")
    id("com.rohittp.plugables.branchmark") version "<version>"
}
```

That's it — zero config. Ensure your app has a standard adaptive icon
(`mipmap-anydpi-v26/ic_launcher.xml` with a `<foreground>`), which Android Studio's New Project wizard
generates by default.

### CI

Detached or shallow CI checkouts can't read the branch from git — pass it explicitly:

```yaml
# GitHub Actions
- run: ./gradlew assembleDebug -PgitBranch=${{ github.head_ref || github.ref_name }}
```

Without it, CI debug builds fall back to the static "DEBUG" ribbon — no failure.

## Configuration

All options have sensible defaults; configure only what you need:

```kotlin
branchmark {
    buildType.set("debug")                                  // build type to stamp
    densities.set(listOf("hdpi", "xhdpi", "xxhdpi", "xxxhdpi"))
    launcherIconName.set("ic_launcher")                     // existing adaptive icon to read
    foregroundResourceName.set("ic_launcher_foreground_debug")
    ribbonColor.set("#D32F2F")                              // #RRGGBB or #AARRGGBB
    ribbonTextColor.set("#FFFFFF")
    defaultEmoji.set("🌿")                                  // for unknown prefixes
    fallbackRibbonText.set("DEBUG")                         // slashless / undetectable branches
    branchOverride.set("feat/manual")                       // force a branch (highest precedence)

    // Additive emoji overrides (use put/putAll, not set):
    emojiByPrefix.put("spike", "🧪")
}
```

Built-in prefix → emoji map: `feat ✨`, `fix 🔧`, `bug 🐛`, `hotfix 🚑`, `chore 🧹`, `refactor ♻️`,
`docs 📝`, `test 🧪`, `perf ⚡`, `ci 🔁`, `claude 🤖` (unknown → `defaultEmoji`).

Branch resolution precedence:
`branchOverride` → `-PgitBranch` → `GITHUB_HEAD_REF` → `GIT_BRANCH` → `git rev-parse --abbrev-ref HEAD`.

## Limitations

- **Adaptive foreground only.** On `minSdk < 26` devices, the legacy square `ic_launcher.png` is shown
  unstamped — branchmark only regenerates the adaptive-icon foreground.
- **Emoji coverage.** Emoji outside the built-in map fall back to a (best-effort) system-font glyph
  unless their Twemoji SVG happens to be bundled.

## License

Plugin code: MIT. Bundled emoji graphics: [Twemoji](https://github.com/jdecked/twemoji), CC-BY 4.0
(see `src/main/resources/branchmark/emoji/NOTICE`).
