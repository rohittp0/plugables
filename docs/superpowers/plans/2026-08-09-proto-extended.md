# proto-extended Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Gradle plugin `com.rohittp.plugables.proto-extended` that generates Kotlin extension properties from proto enums — pure-Kotlin metadata properties for `commonMain`, and Android `R`-bound `displayName`/`icon` accessors for whichever module owns the resources.

**Architecture:** Two independent generators under one extension, each a separate Gradle task applied in the module that owns its input. Internals split parse → model → render: `ProtoSchemaReader` turns `.proto` files into a Gradle-free, Wire-free data model (`EnumModel.kt`), and two pure renderer objects turn that model into Kotlin source. Renderers are unit-testable with no Gradle and no filesystem.

**Tech Stack:** Kotlin 2.3.0 / JVM 21, Gradle `kotlin-dsl` plugin, `com.squareup.wire:wire-schema:6.4.5`, `kotlin.test` on JUnit Platform, Gradle TestKit.

**Reference documents:**
- Spec: `docs/superpowers/specs/2026-08-09-proto-extended-design.md`
- ADR: `docs/adr/0001-proto-resource-directives-as-enum-options.md`
- Domain terms: `CONTEXT.md` § proto-extended

## Global Constraints

- Plugin id `com.rohittp.plugables.proto-extended`; implementation class `com.rohittp.plugables.protoextended.ProtoExtendedPlugin`; Kotlin package `com.rohittp.plugables.protoextended`.
- Version `1.0.0` in `proto-extended/build.gradle.kts`. **CI publishes on a `version = "…"` line change**, so do not touch that line after the first commit until the work is complete and green.
- JVM target 21 via `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }`, matching every other plugin.
- Never declare `google()` / `mavenCentral()` in the subproject — the root `subprojects {}` block owns repositories.
- Neither task may reference `Project` at execution time (configuration cache compatibility). Only `Property`/`DirectoryProperty`/`ConfigurableFileCollection` cross into `@TaskAction`.
- Generated output must be deterministic: enums sorted by qualified name, imports sorted.
- `EnumModel.kt` must import neither Gradle nor wire-schema. This is the tested boundary.
- Tests gate publishing (`build` → `check` → `test`). No network-dependent or KMP-ProjectBuilder tests.

## Deviation from the approved spec — read before Task 6

The spec lists an optional `sourceSet` property on both blocks. **It is dropped in v1** because it cannot work as specified.

Plugin application order is: `plugins { }` block first, then the rest of the build script. So when `ProtoExtendedPlugin.apply()` registers `plugins.withId("org.jetbrains.kotlin.multiplatform") { … }`, KMP is *already* applied and the action runs immediately — during `apply()`, before the consumer's `protoExtended { }` block has executed. Any `sourceSet.get()` inside that block reads an unset property.

The two ways out are wiring inside `afterEvaluate` (contradicting the spec's "one permitted afterEvaluate, purely diagnostic") or hardcoding the source-set names. v1 hardcodes them: `commonMain` for KMP metadata, `main` for Kotlin JVM metadata, `androidMain` for KMP resources. A consumer needing another source set can wire `srcDir(tasks.named("generateProtoMetadata"))` by hand — which the unwired diagnostic explicitly tolerates.

Task 8 records this in the spec's non-goals.

## File Structure

| File | Responsibility |
|---|---|
| `proto-extended/build.gradle.kts` | Subproject config, deps, `gradlePlugin` + `mavenPublishing` blocks |
| `src/main/kotlin/…/EnumModel.kt` | Plain data model. No Gradle, no Wire. The boundary. |
| `src/main/kotlin/…/MetadataRenderer.kt` | `ProtoEnumInfo` list → `ProtoEnumMetadata.kt` source |
| `src/main/kotlin/…/AndroidResourceRenderer.kt` | `ProtoEnumInfo` list → `ProtoEnumResources.kt` source |
| `src/main/kotlin/…/ProtoSchemaReader.kt` | wire-schema → model; owns all five validation rules |
| `src/main/kotlin/…/ProtoExtendedExtension.kt` | `protoExtended { metadata { } androidResources { } }` DSL |
| `src/main/kotlin/…/GenerateProtoMetadataTask.kt` | Gradle task: read → render → write |
| `src/main/kotlin/…/GenerateProtoAndroidResourcesTask.kt` | Gradle task: read → render → write |
| `src/main/kotlin/…/ProtoExtendedPlugin.kt` | `apply()`, five wiring branches, unwired diagnostic |
| `src/test/kotlin/…/ProtoFixtures.kt` | Shared helper: writes `.proto` text into a temp dir |
| `src/test/kotlin/…/MetadataRendererTest.kt` | Golden-string tests, no filesystem |
| `src/test/kotlin/…/AndroidResourceRendererTest.kt` | Golden-string tests, no filesystem |
| `src/test/kotlin/…/ProtoSchemaReaderTest.kt` | Fixture protos → model; ordering, packages, nesting |
| `src/test/kotlin/…/ProtoSchemaValidationTest.kt` | One test per validation rule |
| `src/test/kotlin/…/ProtoExtendedTestKitTest.kt` | Functional: execution, UP_TO_DATE, config cache, NO-SOURCE |

---

### Task 1: Scaffold the subproject, the model, and the metadata renderer

**Files:**
- Create: `proto-extended/build.gradle.kts`
- Modify: `settings.gradle.kts` (append one line)
- Create: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/EnumModel.kt`
- Create: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/MetadataRenderer.kt`
- Test: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/MetadataRendererTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class KotlinScalar(val kotlinName: String)` — `STRING("String")`, `DOUBLE("Double")`, `FLOAT("Float")`, `INT("Int")`, `LONG("Long")`, `BOOLEAN("Boolean")`
  - `data class ConstantValue(val constantName: String, val rawValue: String)`
  - `data class MetaProperty(val name: String, val type: KotlinScalar, val values: List<ConstantValue>)`
  - `data class ResourceFlags(val displayName: Boolean, val icon: Boolean)` with `val any: Boolean`
  - `data class ProtoEnumInfo(val qualifiedName: String, val kotlinRef: String, val kotlinImport: String, val constantNames: List<String>, val metaProperties: List<MetaProperty>, val resourceFlags: ResourceFlags)`
  - `object MetadataRenderer { fun render(basePackage: String, enums: List<ProtoEnumInfo>): String }`

- [ ] **Step 1: Create the subproject build file**

Create `proto-extended/build.gradle.kts`. This is copied from `viewmodel-stub/build.gradle.kts` with the coordinates and dependencies changed. Do not add `repositories { }`.

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    id("com.vanniktech.maven.publish")
}

version = "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.2.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("com.squareup.wire:wire-schema:6.4.5")
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("protoExtended") {
            id = "com.rohittp.plugables.proto-extended"
            displayName = "ProtoExtended"
            description = "Generates Kotlin extension properties from proto enums — multiplatform metadata properties plus Android string/drawable accessors."
            tags = listOf("kotlin", "kotlin-multiplatform", "protobuf", "wire", "codegen")
            implementationClass = "com.rohittp.plugables.protoextended.ProtoExtendedPlugin"
        }
    }
}

mavenPublishing {
    // publishToMavenCentral(automaticRelease = true) and signing are configured centrally in
    // the root build.gradle.kts `subprojects { }` block.

    pom {
        name.set("ProtoExtended")
        description.set("Generates Kotlin extension properties from proto enums — multiplatform metadata properties plus Android string/drawable accessors.")
        url.set("https://github.com/rohittp0/plugables")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("rohittp0")
                name.set("Rohit T P")
                url.set("https://rohittp.com")
            }
        }
        scm {
            url.set("https://github.com/rohittp0/plugables")
            connection.set("scm:git:git://github.com/rohittp0/plugables.git")
            developerConnection.set("scm:git:ssh://git@github.com/rohittp0/plugables.git")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Register the subproject**

Append to `settings.gradle.kts`, after the existing `include(":branchmark")` line:

```kotlin
include(":proto-extended")
```

- [ ] **Step 3: Verify the empty subproject configures**

Run: `./gradlew :proto-extended:build`
Expected: `BUILD SUCCESSFUL`. No sources yet, so nothing is compiled.

- [ ] **Step 4: Write the model**

Create `EnumModel.kt`. Import nothing — that is the point of this file.

```kotlin
package com.rohittp.plugables.protoextended

/** The Kotlin type a proto scalar maps to. */
enum class KotlinScalar(val kotlinName: String) {
    STRING("String"),
    DOUBLE("Double"),
    FLOAT("Float"),
    INT("Int"),
    LONG("Long"),
    BOOLEAN("Boolean"),
}

/**
 * One enum constant's value for a metadata property, as the raw string wire-schema
 * reports it. Converting to a Kotlin literal is the renderer's job, not the reader's.
 */
data class ConstantValue(val constantName: String, val rawValue: String)

/** A generated metadata extension property, e.g. `val AspectRatio.width: Int`. */
data class MetaProperty(
    val name: String,
    val type: KotlinScalar,
    /** One entry per enum constant, in declaration order. */
    val values: List<ConstantValue>,
)

/** Which Android resource accessors an enum asked for via `(gen.resources)`. */
data class ResourceFlags(
    val displayName: Boolean = false,
    val icon: Boolean = false,
) {
    val any: Boolean get() = displayName || icon
}

/**
 * A proto enum flattened into everything the renderers need.
 *
 * [kotlinRef] is how generated code refers to the enum: `AspectRatio` for a top-level
 * enum, `Distance.Unit` for a nested one. [kotlinImport] is the outermost class to
 * import so that reference resolves — for a nested enum that is the *enclosing message*
 * (`…Distance`), never the nested enum itself, which would shadow `kotlin.Unit`.
 */
data class ProtoEnumInfo(
    /** Fully-qualified proto name. Sort key only; never emitted. */
    val qualifiedName: String,
    val kotlinRef: String,
    val kotlinImport: String,
    val constantNames: List<String>,
    val metaProperties: List<MetaProperty>,
    val resourceFlags: ResourceFlags,
)
```

- [ ] **Step 5: Write the failing renderer tests**

Create `MetadataRendererTest.kt`.

```kotlin
package com.rohittp.plugables.protoextended

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MetadataRendererTest {

    private fun aspectRatio(
        metaProperties: List<MetaProperty> = listOf(
            MetaProperty(
                name = "width",
                type = KotlinScalar.INT,
                values = listOf(
                    ConstantValue("RATIO_1_1", "1"),
                    ConstantValue("RATIO_16_9", "16"),
                ),
            ),
        ),
    ) = ProtoEnumInfo(
        qualifiedName = "ta.AspectRatio",
        kotlinRef = "AspectRatio",
        kotlinImport = "com.travelanimator.routemap.AspectRatio",
        constantNames = listOf("RATIO_1_1", "RATIO_16_9"),
        metaProperties = metaProperties,
        resourceFlags = ResourceFlags(),
    )

    @Test
    fun `emits generated banner and package`() {
        val out = MetadataRenderer.render("com.example.generated", listOf(aspectRatio()))
        assertContains(out, "// GENERATED — do not edit. Source: proto enum definitions.")
        assertContains(out, "package com.example.generated")
    }

    @Test
    fun `emits an extension property per meta field`() {
        val out = MetadataRenderer.render("com.example.generated", listOf(aspectRatio()))
        assertContains(out, "val AspectRatio.width: Int")
        assertContains(out, "        AspectRatio.RATIO_1_1 -> 1")
        assertContains(out, "        AspectRatio.RATIO_16_9 -> 16")
    }

    @Test
    fun `imports the enum only when it contributes a property`() {
        val withProps = MetadataRenderer.render("com.example.generated", listOf(aspectRatio()))
        assertContains(withProps, "import com.travelanimator.routemap.AspectRatio")

        val withoutProps = MetadataRenderer.render(
            "com.example.generated",
            listOf(aspectRatio(metaProperties = emptyList())),
        )
        assertFalse(withoutProps.contains("import com.travelanimator.routemap.AspectRatio"))
    }

    @Test
    fun `contains no android imports`() {
        val out = MetadataRenderer.render("com.example.generated", listOf(aspectRatio()))
        assertFalse(out.contains("androidx"))
        assertFalse(out.contains(".R"))
    }

    @Test
    fun `renders each scalar type as a valid kotlin literal`() {
        val enum = ProtoEnumInfo(
            qualifiedName = "ta.Sample",
            kotlinRef = "Sample",
            kotlinImport = "com.example.Sample",
            constantNames = listOf("ONE"),
            metaProperties = listOf(
                MetaProperty("s", KotlinScalar.STRING, listOf(ConstantValue("ONE", "km"))),
                MetaProperty("d", KotlinScalar.DOUBLE, listOf(ConstantValue("ONE", "0.621371"))),
                MetaProperty("f", KotlinScalar.FLOAT, listOf(ConstantValue("ONE", "1.5"))),
                MetaProperty("i", KotlinScalar.INT, listOf(ConstantValue("ONE", "1080"))),
                MetaProperty("l", KotlinScalar.LONG, listOf(ConstantValue("ONE", "9"))),
                MetaProperty("b", KotlinScalar.BOOLEAN, listOf(ConstantValue("ONE", "true"))),
            ),
            resourceFlags = ResourceFlags(),
        )
        val out = MetadataRenderer.render("com.example.generated", listOf(enum))
        assertContains(out, "Sample.ONE -> \"km\"")
        assertContains(out, "Sample.ONE -> 0.621371")
        assertContains(out, "Sample.ONE -> 1.5f")
        assertContains(out, "Sample.ONE -> 1080")
        assertContains(out, "Sample.ONE -> 9L")
        assertContains(out, "Sample.ONE -> true")
    }

    @Test
    fun `escapes strings that would break kotlin source`() {
        val enum = ProtoEnumInfo(
            qualifiedName = "ta.Sample",
            kotlinRef = "Sample",
            kotlinImport = "com.example.Sample",
            constantNames = listOf("ONE"),
            metaProperties = listOf(
                MetaProperty(
                    "s",
                    KotlinScalar.STRING,
                    listOf(ConstantValue("ONE", "a\"b\\c\nd\$e")),
                ),
            ),
            resourceFlags = ResourceFlags(),
        )
        val out = MetadataRenderer.render("com.example.generated", listOf(enum))
        assertContains(out, """Sample.ONE -> "a\"b\\c\nd\${'$'}e"""")
    }

    @Test
    fun `sorts enums by qualified name regardless of input order`() {
        val a = aspectRatio()
        val z = ProtoEnumInfo(
            qualifiedName = "ta.Zebra",
            kotlinRef = "Zebra",
            kotlinImport = "com.example.Zebra",
            constantNames = listOf("ONE"),
            metaProperties = listOf(
                MetaProperty("n", KotlinScalar.INT, listOf(ConstantValue("ONE", "1"))),
            ),
            resourceFlags = ResourceFlags(),
        )
        assertEquals(
            MetadataRenderer.render("p", listOf(a, z)),
            MetadataRenderer.render("p", listOf(z, a)),
        )
    }

    @Test
    fun `emits header-only file when nothing to generate`() {
        val out = MetadataRenderer.render("com.example.generated", emptyList())
        assertContains(out, "package com.example.generated")
        assertFalse(out.contains("import "))
        assertFalse(out.contains("val "))
    }
}
```

- [ ] **Step 6: Run the tests to verify they fail**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.MetadataRendererTest"`
Expected: FAIL — compilation error, `Unresolved reference: MetadataRenderer`.

- [ ] **Step 7: Write the renderer**

Create `MetadataRenderer.kt`.

```kotlin
package com.rohittp.plugables.protoextended

/**
 * Renders the pure-Kotlin half: one extension property per metadata field.
 *
 * Nothing here is Android-specific, so the output compiles for every KMP target.
 * Enums are sorted by qualified name and imports are sorted so that reordering
 * proto files does not churn the output and bust the build cache.
 */
object MetadataRenderer {

    fun render(basePackage: String, enums: List<ProtoEnumInfo>): String {
        val body = StringBuilder()
        val imports = sortedSetOf<String>()

        for (enum in enums.sortedBy { it.qualifiedName }) {
            if (enum.metaProperties.isEmpty()) continue
            imports.add(enum.kotlinImport)
            for (property in enum.metaProperties) {
                body.appendLine("val ${enum.kotlinRef}.${property.name}: ${property.type.kotlinName}")
                body.appendLine("    get() = when (this) {")
                for (value in property.values) {
                    body.appendLine("        ${enum.kotlinRef}.${value.constantName} -> ${literal(property.type, value.rawValue)}")
                }
                body.appendLine("    }")
                body.appendLine()
            }
        }

        return buildString {
            appendLine("// GENERATED — do not edit. Source: proto enum definitions.")
            appendLine("package $basePackage")
            if (imports.isNotEmpty()) {
                appendLine()
                for (import in imports) appendLine("import $import")
            }
            if (body.isNotEmpty()) {
                appendLine()
                append(body)
            }
        }
    }

    /**
     * Converts a raw proto option value into a Kotlin literal. Int tolerates a
     * decimal-looking source value (`"1080.0"`), which protoc permits for integer
     * option fields.
     */
    private fun literal(type: KotlinScalar, raw: String): String = when (type) {
        KotlinScalar.STRING -> "\"${raw.escapeKotlin()}\""
        KotlinScalar.DOUBLE -> (raw.toDoubleOrNull() ?: 0.0).toString()
        KotlinScalar.FLOAT -> "${raw.toFloatOrNull() ?: 0.0f}f"
        KotlinScalar.INT -> "${raw.toDoubleOrNull()?.toInt() ?: 0}"
        KotlinScalar.LONG -> "${raw.toLongOrNull() ?: 0L}L"
        KotlinScalar.BOOLEAN -> raw.toBooleanStrictOrNull()?.toString() ?: "false"
    }

    private fun String.escapeKotlin(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("$", "\\$")
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.MetadataRendererTest"`
Expected: PASS, 8 tests.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts proto-extended/
git commit -m "feat(proto-extended): scaffold plugin with enum model and metadata renderer"
```

---

### Task 2: Android resource renderer

**Files:**
- Create: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/AndroidResourceRenderer.kt`
- Test: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/AndroidResourceRendererTest.kt`

**Interfaces:**
- Consumes: `ProtoEnumInfo`, `ResourceFlags` from Task 1.
- Produces: `object AndroidResourceRenderer { fun render(basePackage: String, rPackage: String, enums: List<ProtoEnumInfo>): String }`

- [ ] **Step 1: Write the failing tests**

Create `AndroidResourceRendererTest.kt`.

```kotlin
package com.rohittp.plugables.protoextended

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidResourceRendererTest {

    private fun enum(flags: ResourceFlags, ref: String = "AspectRatio") = ProtoEnumInfo(
        qualifiedName = "ta.$ref",
        kotlinRef = ref,
        kotlinImport = "com.travelanimator.routemap.$ref",
        constantNames = listOf("RATIO_1_1", "RATIO_16_9"),
        metaProperties = emptyList(),
        resourceFlags = flags,
    )

    @Test
    fun `emits banner package and R import`() {
        val out = AndroidResourceRenderer.render(
            "com.example.generated", "com.travelanimator.routemap",
            listOf(enum(ResourceFlags(displayName = true))),
        )
        assertContains(out, "// GENERATED — do not edit. Source: proto enum definitions.")
        assertContains(out, "package com.example.generated")
        assertContains(out, "import com.travelanimator.routemap.R")
    }

    @Test
    fun `displayName flag emits a StringRes property over lowercased constants`() {
        val out = AndroidResourceRenderer.render(
            "com.example.generated", "com.travelanimator.routemap",
            listOf(enum(ResourceFlags(displayName = true))),
        )
        assertContains(out, "import androidx.annotation.StringRes")
        assertContains(out, "@get:StringRes")
        assertContains(out, "val AspectRatio.displayName: Int")
        assertContains(out, "        AspectRatio.RATIO_1_1 -> R.string.ratio_1_1")
        assertContains(out, "        AspectRatio.RATIO_16_9 -> R.string.ratio_16_9")
    }

    @Test
    fun `icon flag emits a DrawableRes property`() {
        val out = AndroidResourceRenderer.render(
            "com.example.generated", "com.travelanimator.routemap",
            listOf(enum(ResourceFlags(icon = true))),
        )
        assertContains(out, "import androidx.annotation.DrawableRes")
        assertContains(out, "@get:DrawableRes")
        assertContains(out, "val AspectRatio.icon: Int")
        assertContains(out, "        AspectRatio.RATIO_1_1 -> R.drawable.ratio_1_1")
    }

    @Test
    fun `only imports the annotation it actually uses`() {
        val stringOnly = AndroidResourceRenderer.render(
            "p", "r", listOf(enum(ResourceFlags(displayName = true))),
        )
        assertFalse(stringOnly.contains("import androidx.annotation.DrawableRes"))

        val drawableOnly = AndroidResourceRenderer.render(
            "p", "r", listOf(enum(ResourceFlags(icon = true))),
        )
        assertFalse(drawableOnly.contains("import androidx.annotation.StringRes"))
    }

    @Test
    fun `skips enums with no flags and does not import them`() {
        val out = AndroidResourceRenderer.render(
            "p", "r", listOf(enum(ResourceFlags())),
        )
        assertFalse(out.contains("val AspectRatio"))
        assertFalse(out.contains("import com.travelanimator.routemap.AspectRatio"))
    }

    @Test
    fun `sorts enums by qualified name regardless of input order`() {
        val a = enum(ResourceFlags(displayName = true), ref = "AspectRatio")
        val z = enum(ResourceFlags(displayName = true), ref = "Zebra")
        assertEquals(
            AndroidResourceRenderer.render("p", "r", listOf(a, z)),
            AndroidResourceRenderer.render("p", "r", listOf(z, a)),
        )
    }

    @Test
    fun `emits header-only file when nothing to generate`() {
        val out = AndroidResourceRenderer.render("com.example.generated", "r", emptyList())
        assertContains(out, "package com.example.generated")
        assertFalse(out.contains("import "))
        assertFalse(out.contains("val "))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.AndroidResourceRendererTest"`
Expected: FAIL — `Unresolved reference: AndroidResourceRenderer`.

- [ ] **Step 3: Write the renderer**

Create `AndroidResourceRenderer.kt`.

```kotlin
package com.rohittp.plugables.protoextended

/**
 * Renders the Android half: `@get:StringRes val X.displayName` and
 * `@get:DrawableRes val X.icon`, resolving `R.string.<constant>` and
 * `R.drawable.<constant>` with the constant name lowercased.
 *
 * This is the only generated output that cannot live in `commonMain`, because
 * `R` belongs to the module that owns the resources.
 */
object AndroidResourceRenderer {

    fun render(basePackage: String, rPackage: String, enums: List<ProtoEnumInfo>): String {
        val body = StringBuilder()
        val enumImports = sortedSetOf<String>()
        var usesStringRes = false
        var usesDrawableRes = false

        for (enum in enums.sortedBy { it.qualifiedName }) {
            if (!enum.resourceFlags.any) continue
            enumImports.add(enum.kotlinImport)

            if (enum.resourceFlags.displayName) {
                usesStringRes = true
                appendAccessor(body, enum, "displayName", "@get:StringRes", "R.string")
            }
            if (enum.resourceFlags.icon) {
                usesDrawableRes = true
                appendAccessor(body, enum, "icon", "@get:DrawableRes", "R.drawable")
            }
        }

        return buildString {
            appendLine("// GENERATED — do not edit. Source: proto enum definitions.")
            appendLine("package $basePackage")
            if (body.isNotEmpty()) {
                appendLine()
                if (usesDrawableRes) appendLine("import androidx.annotation.DrawableRes")
                if (usesStringRes) appendLine("import androidx.annotation.StringRes")
                appendLine("import $rPackage.R")
                for (import in enumImports) appendLine("import $import")
                appendLine()
                append(body)
            }
        }
    }

    private fun appendAccessor(
        body: StringBuilder,
        enum: ProtoEnumInfo,
        propertyName: String,
        annotation: String,
        resourcePrefix: String,
    ) {
        body.appendLine(annotation)
        body.appendLine("val ${enum.kotlinRef}.$propertyName: Int")
        body.appendLine("    get() = when (this) {")
        for (constant in enum.constantNames) {
            body.appendLine("        ${enum.kotlinRef}.$constant -> $resourcePrefix.${constant.lowercase()}")
        }
        body.appendLine("    }")
        body.appendLine()
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.AndroidResourceRendererTest"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add proto-extended/
git commit -m "feat(proto-extended): add android resource renderer"
```

---

### Task 3: Schema reader — enum discovery, package resolution, nesting

**Files:**
- Create: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/ProtoSchemaReader.kt`
- Create: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/ProtoFixtures.kt`
- Test: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/ProtoSchemaReaderTest.kt`

**Interfaces:**
- Consumes: `ProtoEnumInfo`, `MetaProperty`, `ResourceFlags` from Task 1.
- Produces:
  - `class ProtoSchemaException(message: String) : RuntimeException(message)`
  - `class ProtoSchemaReader(private val protoDir: File) { fun read(): List<ProtoEnumInfo> }` — returns enums sorted by `qualifiedName`. Tasks 4 and 5 extend this same class; do not create new entry points.
  - `object ProtoFixtures { fun write(dir: File, name: String, content: String) }`

Background for someone new to wire-schema: `SchemaLoader` parses `.proto` text into a `Schema`. `schema.protoFiles` includes Wire's bundled `google/protobuf/descriptor.proto`, which is why the reader filters out anything in the `google.protobuf` package. Nested enums live under a message's `nestedTypes`, so `Type.typesAndNestedTypes()` is needed to reach them — a plain `flatMap { it.types }` would miss `Distance.Unit`.

- [ ] **Step 1: Write the fixture helper**

Create `ProtoFixtures.kt`.

```kotlin
package com.rohittp.plugables.protoextended

import java.io.File

/** Writes `.proto` source into a temp directory for reader tests. */
object ProtoFixtures {

    fun write(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.parentFile.mkdirs()
        file.writeText(content.trimIndent())
        return file
    }

    /**
     * The `(gen.resources)` extension the Android generator looks for.
     * Task 5 depends on this being available to fixture protos.
     */
    const val GEN_OPTIONS = """
        syntax = "proto3";
        package gen;
        import "google/protobuf/descriptor.proto";

        message ResourceGen {
          bool display_name = 1;
          bool icon = 2;
        }

        extend google.protobuf.EnumOptions {
          optional ResourceGen resources = 50100;
        }
    """
}
```

- [ ] **Step 2: Write the failing reader tests**

Create `ProtoSchemaReaderTest.kt`.

```kotlin
package com.rohittp.plugables.protoextended

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoSchemaReaderTest {

    @Test
    fun `finds top-level enums and their constants in declaration order`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            option java_package = "com.travelanimator.routemap";

            enum Intro {
              INTRO_DEFAULT = 0;
              INTRO_GLOBE = 1;
            }
            """,
        )

        val enums = ProtoSchemaReader(tmp).read()

        assertEquals(1, enums.size)
        assertEquals("ta.Intro", enums[0].qualifiedName)
        assertEquals("Intro", enums[0].kotlinRef)
        assertEquals(listOf("INTRO_DEFAULT", "INTRO_GLOBE"), enums[0].constantNames)
    }

    @Test
    fun `excludes google protobuf built-in enums`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            enum Intro { INTRO_DEFAULT = 0; }
            """,
        )

        val enums = ProtoSchemaReader(tmp).read()

        assertTrue(enums.none { it.qualifiedName.startsWith("google.protobuf") })
        assertEquals(listOf("ta.Intro"), enums.map { it.qualifiedName })
    }

    @Test
    fun `nested enum is referenced through its enclosing message and imports it`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            option java_package = "com.travelanimator.routemap";

            message Distance {
              enum Unit {
                KM = 0;
                MILE = 1;
              }
              Unit unit = 1;
            }
            """,
        )

        val unit = ProtoSchemaReader(tmp).read().single { it.kotlinRef.contains(".") }

        assertEquals("Distance.Unit", unit.kotlinRef)
        assertEquals("com.travelanimator.routemap.Distance", unit.kotlinImport)
    }

    @Test
    fun `java_package drives the import when wire_package is absent`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            option java_package = "com.travelanimator.routemap";

            enum Intro { INTRO_DEFAULT = 0; }
            """,
        )

        assertEquals(
            "com.travelanimator.routemap.Intro",
            ProtoSchemaReader(tmp).read().single().kotlinImport,
        )
    }

    @Test
    fun `wire_package takes precedence over java_package`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";
            option java_package = "com.example.java";
            option (wire.wire_package) = "com.example.wire";

            enum Intro { INTRO_DEFAULT = 0; }
            """,
        )

        assertEquals(
            "com.example.wire.Intro",
            ProtoSchemaReader(tmp).read().single().kotlinImport,
        )
    }

    @Test
    fun `falls back to the proto package when no package option is set`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;

            enum Intro { INTRO_DEFAULT = 0; }
            """,
        )

        assertEquals("ta.Intro", ProtoSchemaReader(tmp).read().single().kotlinImport)
    }

    @Test
    fun `result is sorted by qualified name across files`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "zebra.proto",
            """
            syntax = "proto3";
            package ta;
            enum Zebra { Z = 0; }
            """,
        )
        ProtoFixtures.write(
            tmp, "alpha.proto",
            """
            syntax = "proto3";
            package ta;
            enum Alpha { A = 0; }
            """,
        )

        assertEquals(
            listOf("ta.Alpha", "ta.Zebra"),
            ProtoSchemaReader(tmp).read().map { it.qualifiedName },
        )
    }
}
```

> Note on the `wire_package` test: `(wire.wire_package)` is defined in Wire's own bundled
> `wire/extensions.proto`, which `SchemaLoader` resolves without the fixture declaring it.
> If that import turns out to be required, add `import "wire/extensions.proto";` to the
> fixture — do not change the production precedence logic.

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.ProtoSchemaReaderTest"`
Expected: FAIL — `Unresolved reference: ProtoSchemaReader`.

- [ ] **Step 4: Write the reader**

Create `ProtoSchemaReader.kt`. Metadata and resource options are added in Tasks 4 and 5 — this task returns empty `metaProperties` and default `resourceFlags`.

```kotlin
package com.rohittp.plugables.protoextended

import com.squareup.wire.schema.EnumType
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.ProtoFile
import com.squareup.wire.schema.ProtoType
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaLoader
import okio.FileSystem
import java.io.File

/** A proto schema the plugin refuses to generate from. Message is shown to the user verbatim. */
class ProtoSchemaException(message: String) : RuntimeException(message)

/**
 * Turns a directory of `.proto` files into the plugin's model.
 *
 * Everything Wire-specific stops here: [ProtoEnumInfo] and friends carry no
 * wire-schema types, so the renderers stay pure and independently testable.
 */
class ProtoSchemaReader(private val protoDir: File) {

    fun read(): List<ProtoEnumInfo> {
        val schema = loadSchema()

        return schema.protoFiles
            .filter { !it.packageName.orEmpty().startsWith("google.protobuf") }
            .flatMap { it.types }
            .flatMap { it.typesAndNestedTypes() }
            .filterIsInstance<EnumType>()
            .map { enumType ->
                ProtoEnumInfo(
                    qualifiedName = enumType.type.toString(),
                    kotlinRef = relativeName(schema, enumType.type),
                    kotlinImport = kotlinImport(schema, enumType.type),
                    constantNames = enumType.constants.map { it.name },
                    metaProperties = emptyList(),
                    resourceFlags = ResourceFlags(),
                )
            }
            .sortedBy { it.qualifiedName }
    }

    private fun loadSchema(): Schema {
        val root = Location.get(protoDir.absolutePath)
        val loader = SchemaLoader(FileSystem.SYSTEM)
        loader.initRoots(sourcePath = listOf(root), protoPath = listOf(root))
        return loader.loadSchema()
    }

    /**
     * Mirrors Wire's own `KotlinGenerator` precedence (`JvmLanguages.javaPackage`):
     * `wire_package` beats `java_package` beats the proto package. Reimplemented rather
     * than called, because Wire's version lives in a `.internal.` package with no
     * compatibility promise.
     */
    private fun kotlinPackage(protoFile: ProtoFile?): String =
        protoFile?.wirePackage()
            ?: protoFile?.javaPackage()
            ?: protoFile?.packageName.orEmpty()

    /** `AspectRatio` for a top-level enum, `Distance.Unit` for a nested one. */
    private fun relativeName(schema: Schema, protoType: ProtoType): String {
        val pkg = schema.protoFile(protoType)?.packageName.orEmpty()
        val full = protoType.toString()
        return if (pkg.isNotEmpty() && full.startsWith("$pkg.")) full.removePrefix("$pkg.") else full
    }

    /**
     * The outermost class to import. For `Distance.Unit` that is `…Distance`, never
     * `…Unit` — importing the nested enum would shadow `kotlin.Unit` and would not
     * match the dotted reference emitted by the renderers.
     */
    private fun kotlinImport(schema: Schema, protoType: ProtoType): String {
        val pkg = kotlinPackage(schema.protoFile(protoType))
        val outer = relativeName(schema, protoType).substringBefore('.')
        return if (pkg.isNotEmpty()) "$pkg.$outer" else outer
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.ProtoSchemaReaderTest"`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add proto-extended/
git commit -m "feat(proto-extended): read proto enums into the model with wire package precedence"
```

---

### Task 4: Schema reader — metadata options and validation rules 1–4

**Files:**
- Modify: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/ProtoSchemaReader.kt`
- Test: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/ProtoSchemaValidationTest.kt`
- Modify: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/ProtoSchemaReaderTest.kt` (append two tests)

**Interfaces:**
- Consumes: `ProtoSchemaReader.read()`, `ProtoSchemaException`, `MetaProperty`, `KotlinScalar`, `ConstantValue`.
- Produces: `read()` now populates `ProtoEnumInfo.metaProperties`. No new public types.

Background: a `MetaOptionSpec` is one `extend google.protobuf.EnumValueOptions` field. Reading a constant's value for it gives a `Map<ProtoMember, Any?>` keyed by the meta message's fields, with scalar values as `String`. `ProtoMember.get(Options.ENUM_VALUE_OPTIONS, field.qualifiedName)` builds the outer key; `ProtoMember.get(metaType, fieldName)` builds the inner one.

- [ ] **Step 1: Write the failing validation tests**

Create `ProtoSchemaValidationTest.kt`.

```kotlin
package com.rohittp.plugables.protoextended

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ProtoSchemaValidationTest {

    private fun protoWithMeta(body: String) = """
        syntax = "proto3";
        package ta;
        import "google/protobuf/descriptor.proto";

        message RatioMeta {
          int32 width = 1;
          int32 height = 2;
        }

        extend google.protobuf.EnumValueOptions {
          optional RatioMeta ratio_meta = 50001;
        }

        $body
    """

    @Test
    fun `rule 1 - option on some constants but not all fails`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            protoWithMeta(
                """
                enum AspectRatio {
                  RATIO_1_1 = 0 [(ratio_meta) = { width: 1, height: 1 }];
                  RATIO_16_9 = 1;
                }
                """,
            ),
        )

        val error = assertFailsWith<ProtoSchemaException> { ProtoSchemaReader(tmp).read() }

        assertContains(error.message!!, "AspectRatio")
        assertContains(error.message!!, "ratio_meta")
        assertContains(error.message!!, "RATIO_16_9")
    }

    @Test
    fun `rule 2 - field set on some constants but not others fails`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            protoWithMeta(
                """
                enum AspectRatio {
                  RATIO_1_1 = 0 [(ratio_meta) = { width: 1, height: 1 }];
                  RATIO_16_9 = 1 [(ratio_meta) = { width: 16 }];
                }
                """,
            ),
        )

        val error = assertFailsWith<ProtoSchemaException> { ProtoSchemaReader(tmp).read() }

        assertContains(error.message!!, "height")
        assertContains(error.message!!, "RATIO_16_9")
    }

    @Test
    fun `rule 2 - field set on no constant is silently skipped`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            protoWithMeta(
                """
                enum AspectRatio {
                  RATIO_1_1 = 0 [(ratio_meta) = { width: 1 }];
                  RATIO_16_9 = 1 [(ratio_meta) = { width: 16 }];
                }
                """,
            ),
        )

        val enum = ProtoSchemaReader(tmp).read().single()

        assertContains(enum.metaProperties.map { it.name }, "width")
        kotlin.test.assertFalse(enum.metaProperties.any { it.name == "height" })
    }

    @Test
    fun `rule 3 - meta field named name is reserved`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            message BadMeta { string name = 1; }

            extend google.protobuf.EnumValueOptions {
              optional BadMeta bad_meta = 50001;
            }

            enum Sample { ONE = 0 [(bad_meta) = { name: "x" }]; }
            """,
        )

        val error = assertFailsWith<ProtoSchemaException> { ProtoSchemaReader(tmp).read() }

        assertContains(error.message!!, "name")
        assertContains(error.message!!, "reserved")
    }

    @Test
    fun `rule 3 - two meta messages contributing the same field name collide`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            message MetaA { string label = 1; }
            message MetaB { string label = 1; }

            extend google.protobuf.EnumValueOptions {
              optional MetaA meta_a = 50001;
              optional MetaB meta_b = 50002;
            }

            enum Sample {
              ONE = 0 [(meta_a) = { label: "a" }, (meta_b) = { label: "b" }];
            }
            """,
        )

        val error = assertFailsWith<ProtoSchemaException> { ProtoSchemaReader(tmp).read() }

        assertContains(error.message!!, "label")
    }

    @Test
    fun `rule 4 - repeated meta field fails instead of stringifying`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            message ListMeta { repeated string tags = 1; }

            extend google.protobuf.EnumValueOptions {
              optional ListMeta list_meta = 50001;
            }

            enum Sample { ONE = 0 [(list_meta) = { tags: ["a"] }]; }
            """,
        )

        val error = assertFailsWith<ProtoSchemaException> { ProtoSchemaReader(tmp).read() }

        assertContains(error.message!!, "tags")
    }

    @Test
    fun `rule 4 - message-typed meta field fails`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            message Inner { string v = 1; }
            message OuterMeta { Inner inner = 1; }

            extend google.protobuf.EnumValueOptions {
              optional OuterMeta outer_meta = 50001;
            }

            enum Sample { ONE = 0 [(outer_meta) = { inner: { v: "x" } }]; }
            """,
        )

        val error = assertFailsWith<ProtoSchemaException> { ProtoSchemaReader(tmp).read() }

        assertContains(error.message!!, "inner")
    }
}
```

- [ ] **Step 2: Append the happy-path metadata tests**

Append these two tests to `ProtoSchemaReaderTest.kt`, inside the existing class:

```kotlin
    @Test
    fun `reads metadata properties with mapped scalar types`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            message UnitMeta {
              string symbol = 1;
              double multiplier = 2;
            }

            extend google.protobuf.EnumValueOptions {
              optional UnitMeta unit_meta = 50001;
            }

            enum Unit {
              KM = 0 [(unit_meta) = { symbol: "km", multiplier: 1.0 }];
              MILE = 1 [(unit_meta) = { symbol: "mi", multiplier: 0.621371 }];
            }
            """,
        )

        val enum = ProtoSchemaReader(tmp).read().single()
        val symbol = enum.metaProperties.single { it.name == "symbol" }
        val multiplier = enum.metaProperties.single { it.name == "multiplier" }

        assertEquals(KotlinScalar.STRING, symbol.type)
        assertEquals(KotlinScalar.DOUBLE, multiplier.type)
        assertEquals(listOf("KM", "MILE"), symbol.values.map { it.constantName })
        assertEquals(listOf("km", "mi"), symbol.values.map { it.rawValue })
    }

    @Test
    fun `enum without any meta option yields no metadata properties`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            enum Plain { ONE = 0; TWO = 1; }
            """,
        )

        assertTrue(ProtoSchemaReader(tmp).read().single().metaProperties.isEmpty())
    }
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.ProtoSchema*"`
Expected: FAIL — the validation tests fail because nothing throws yet, and the metadata tests fail because `metaProperties` is empty.

- [ ] **Step 4: Add metadata reading and validation to the reader**

In `ProtoSchemaReader.kt`, add these imports:

```kotlin
import com.squareup.wire.schema.EnumConstant
import com.squareup.wire.schema.Field
import com.squareup.wire.schema.MessageType
import com.squareup.wire.schema.Options
import com.squareup.wire.schema.ProtoMember
```

Replace the `metaProperties = emptyList(),` line in `read()` with a call to the new helper, and pass the discovered specs in. The `read()` body becomes:

```kotlin
    fun read(): List<ProtoEnumInfo> {
        val schema = loadSchema()
        val metaSpecs = discoverMetaSpecs(schema)

        return schema.protoFiles
            .filter { !it.packageName.orEmpty().startsWith("google.protobuf") }
            .flatMap { it.types }
            .flatMap { it.typesAndNestedTypes() }
            .filterIsInstance<EnumType>()
            .map { enumType ->
                ProtoEnumInfo(
                    qualifiedName = enumType.type.toString(),
                    kotlinRef = relativeName(schema, enumType.type),
                    kotlinImport = kotlinImport(schema, enumType.type),
                    constantNames = enumType.constants.map { it.name },
                    metaProperties = metaProperties(enumType, metaSpecs),
                    resourceFlags = ResourceFlags(),
                )
            }
            .sortedBy { it.qualifiedName }
    }
```

Then append these members to the class:

```kotlin
    /** One `extend google.protobuf.EnumValueOptions` field and the message it points at. */
    private data class MetaOptionSpec(
        val optionName: String,
        val optionMember: ProtoMember,
        val metaType: ProtoType,
        val metaMessage: MessageType,
    )

    private fun discoverMetaSpecs(schema: Schema): List<MetaOptionSpec> =
        schema.protoFiles
            .flatMap { it.extendList }
            .filter { it.type == Options.ENUM_VALUE_OPTIONS }
            .flatMap { it.fields }
            .mapNotNull { field ->
                val fieldType = field.type ?: return@mapNotNull null
                val message = schema.getType(fieldType) as? MessageType ?: return@mapNotNull null
                MetaOptionSpec(
                    optionName = field.name,
                    optionMember = ProtoMember.get(Options.ENUM_VALUE_OPTIONS, field.qualifiedName),
                    metaType = fieldType,
                    metaMessage = message,
                )
            }

    @Suppress("UNCHECKED_CAST")
    private fun metaProperties(
        enumType: EnumType,
        specs: List<MetaOptionSpec>,
    ): List<MetaProperty> {
        val properties = mutableListOf<MetaProperty>()
        val seenNames = mutableMapOf<String, String>()

        for (spec in specs) {
            val optionsByConstant: Map<EnumConstant, Map<ProtoMember, Any?>?> =
                enumType.constants.associateWith { constant ->
                    constant.options.get(spec.optionMember) as? Map<ProtoMember, Any?>
                }

            val carrying = optionsByConstant.filterValues { it != null }
            if (carrying.isEmpty()) continue

            // Rule 1 — all-or-nothing option presence.
            val missing = optionsByConstant.filterValues { it == null }.keys.map { it.name }
            if (missing.isNotEmpty()) {
                throw ProtoSchemaException(
                    "Enum `${enumType.type}` declares (${spec.optionName}) on " +
                        "${carrying.size} of ${enumType.constants.size} constants. Missing on:\n" +
                        missing.joinToString("\n") { "  - $it" } +
                        "\n\nEvery constant must set the option, or none.",
                )
            }

            for (field in spec.metaMessage.declaredFields) {
                // Rule 4 — supported scalars only.
                val scalar = scalarFor(field)
                    ?: throw ProtoSchemaException(
                        "Field `${field.name}` of `${spec.metaType}` has unsupported type " +
                            "`${field.type}`${if (field.isRepeated) " (repeated)" else ""}. " +
                            "proto-extended supports string, double, float, int32, uint32, " +
                            "int64, uint64 and bool.",
                    )

                val member = ProtoMember.get(spec.metaType, field.name)
                val rawByConstant = optionsByConstant.mapValues { (_, options) ->
                    options?.get(member)?.toString()
                }

                val setCount = rawByConstant.count { it.value != null }
                if (setCount == 0) continue

                // Rule 2 — all-or-nothing field presence.
                if (setCount != enumType.constants.size) {
                    val unset = rawByConstant.filterValues { it == null }.keys.map { it.name }
                    throw ProtoSchemaException(
                        "Field `${field.name}` of (${spec.optionName}) is set on $setCount of " +
                            "${enumType.constants.size} constants of `${enumType.type}`. Missing on:\n" +
                            unset.joinToString("\n") { "  - $it" } +
                            "\n\nSet it on every constant, or none.",
                    )
                }

                // Rule 3 — reserved names and cross-message collisions.
                if (field.name in RESERVED_NAMES) {
                    throw ProtoSchemaException(
                        "Field `${field.name}` of `${spec.metaType}` is reserved: an extension " +
                            "property cannot shadow the enum member of the same name, so the " +
                            "generated property would silently never be called. " +
                            "Reserved names: ${RESERVED_NAMES.joinToString()}.",
                    )
                }
                seenNames[field.name]?.let { previous ->
                    throw ProtoSchemaException(
                        "Field `${field.name}` is contributed to enum `${enumType.type}` by both " +
                            "`$previous` and `${spec.metaType}`. Rename one of them.",
                    )
                }
                seenNames[field.name] = spec.metaType.toString()

                properties += MetaProperty(
                    name = field.name,
                    type = scalar,
                    values = enumType.constants.map { constant ->
                        ConstantValue(constant.name, rawByConstant.getValue(constant)!!)
                    },
                )
            }
        }
        return properties
    }

    private fun scalarFor(field: Field): KotlinScalar? {
        if (field.isRepeated) return null
        return when (field.type) {
            ProtoType.STRING -> KotlinScalar.STRING
            ProtoType.DOUBLE -> KotlinScalar.DOUBLE
            ProtoType.FLOAT -> KotlinScalar.FLOAT
            ProtoType.INT32, ProtoType.UINT32 -> KotlinScalar.INT
            ProtoType.INT64, ProtoType.UINT64 -> KotlinScalar.LONG
            ProtoType.BOOL -> KotlinScalar.BOOLEAN
            else -> null
        }
    }

    private companion object {
        /**
         * Names an extension property must not take. `name` and `ordinal` are Kotlin
         * `Enum` members; `value` is Wire's `WireEnum.value`. A real member always wins
         * over an extension, so a clash is dead code with no compiler error.
         */
        val RESERVED_NAMES = setOf("name", "ordinal", "value")
    }
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.ProtoSchema*"`
Expected: PASS, 16 tests (9 reader, 7 validation).

- [ ] **Step 6: Commit**

```bash
git add proto-extended/
git commit -m "feat(proto-extended): read enum value option metadata with strict validation"
```

---

### Task 5: Schema reader — resource options and validation rule 5

**Files:**
- Modify: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/ProtoSchemaReader.kt`
- Modify: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/ProtoSchemaReaderTest.kt` (append three tests)
- Modify: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/ProtoSchemaValidationTest.kt` (append two tests)

**Interfaces:**
- Consumes: `ProtoSchemaReader.read()`, `ResourceFlags`, `ProtoFixtures.GEN_OPTIONS`.
- Produces: `read()` now populates `ProtoEnumInfo.resourceFlags`. No new public types.

**Recognition rule** — this refines the spec, which only said "a `ResourceGen` field the plugin does not recognise fails". Matching by option name would hardcode `gen.resources`; matching *every* `EnumOptions` extension would reject a consumer's unrelated ones. So: an `EnumOptions` extension is *ours* if its message declares at least one of `display_name` / `icon`. Within such a message, any other field is an error. Unrelated `EnumOptions` extensions are ignored entirely, and two competing recognised messages are an error.

- [ ] **Step 1: Write the failing resource-flag tests**

Append to `ProtoSchemaReaderTest.kt`:

```kotlin
    @Test
    fun `reads display_name and icon flags from the enum option`(@TempDir tmp: File) {
        ProtoFixtures.write(tmp, "gen_options.proto", ProtoFixtures.GEN_OPTIONS)
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "gen_options.proto";

            enum AspectRatio {
              option (gen.resources) = { display_name: true, icon: true };
              RATIO_1_1 = 0;
            }

            enum Intro {
              option (gen.resources) = { display_name: true };
              INTRO_DEFAULT = 0;
            }

            enum Plain { ONE = 0; }
            """,
        )

        val byRef = ProtoSchemaReader(tmp).read().associateBy { it.kotlinRef }

        assertEquals(ResourceFlags(displayName = true, icon = true), byRef.getValue("AspectRatio").resourceFlags)
        assertEquals(ResourceFlags(displayName = true, icon = false), byRef.getValue("Intro").resourceFlags)
        assertEquals(ResourceFlags(), byRef.getValue("Plain").resourceFlags)
    }

    @Test
    fun `flag set to false is treated as absent`(@TempDir tmp: File) {
        ProtoFixtures.write(tmp, "gen_options.proto", ProtoFixtures.GEN_OPTIONS)
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "gen_options.proto";

            enum Intro {
              option (gen.resources) = { display_name: true, icon: false };
              INTRO_DEFAULT = 0;
            }
            """,
        )

        assertEquals(
            ResourceFlags(displayName = true, icon = false),
            ProtoSchemaReader(tmp).read().single { it.kotlinRef == "Intro" }.resourceFlags,
        )
    }

    @Test
    fun `unrelated enum options extension is ignored`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            message Unrelated { string note = 1; }

            extend google.protobuf.EnumOptions {
              optional Unrelated unrelated = 50200;
            }

            enum Intro {
              option (ta.unrelated) = { note: "hi" };
              INTRO_DEFAULT = 0;
            }
            """,
        )

        assertEquals(ResourceFlags(), ProtoSchemaReader(tmp).read().single().resourceFlags)
    }
```

- [ ] **Step 2: Write the failing rule-5 tests**

Append to `ProtoSchemaValidationTest.kt`:

```kotlin
    @Test
    fun `rule 5 - unknown field in the resource option message fails`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "gen_options.proto",
            """
            syntax = "proto3";
            package gen;
            import "google/protobuf/descriptor.proto";

            message ResourceGen {
              bool display_name = 1;
              bool icon = 2;
              bool tint = 3;
            }

            extend google.protobuf.EnumOptions {
              optional ResourceGen resources = 50100;
            }
            """,
        )

        val error = assertFailsWith<ProtoSchemaException> { ProtoSchemaReader(tmp).read() }

        assertContains(error.message!!, "tint")
        assertContains(error.message!!, "display_name")
        assertContains(error.message!!, "icon")
    }

    @Test
    fun `rule 5 - non-bool resource flag fails`(@TempDir tmp: File) {
        ProtoFixtures.write(
            tmp, "gen_options.proto",
            """
            syntax = "proto3";
            package gen;
            import "google/protobuf/descriptor.proto";

            message ResourceGen {
              string display_name = 1;
            }

            extend google.protobuf.EnumOptions {
              optional ResourceGen resources = 50100;
            }
            """,
        )

        val error = assertFailsWith<ProtoSchemaException> { ProtoSchemaReader(tmp).read() }

        assertContains(error.message!!, "display_name")
        assertContains(error.message!!, "bool")
    }
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.ProtoSchema*"`
Expected: FAIL — resource flags come back as the default `ResourceFlags()` and nothing throws for rule 5.

- [ ] **Step 4: Add resource-option reading to the reader**

In `read()`, replace `resourceFlags = ResourceFlags(),` and add the spec lookup. The body becomes:

```kotlin
    fun read(): List<ProtoEnumInfo> {
        val schema = loadSchema()
        val metaSpecs = discoverMetaSpecs(schema)
        val resourceSpec = discoverResourceSpec(schema)

        return schema.protoFiles
            .filter { !it.packageName.orEmpty().startsWith("google.protobuf") }
            .flatMap { it.types }
            .flatMap { it.typesAndNestedTypes() }
            .filterIsInstance<EnumType>()
            .map { enumType ->
                ProtoEnumInfo(
                    qualifiedName = enumType.type.toString(),
                    kotlinRef = relativeName(schema, enumType.type),
                    kotlinImport = kotlinImport(schema, enumType.type),
                    constantNames = enumType.constants.map { it.name },
                    metaProperties = metaProperties(enumType, metaSpecs),
                    resourceFlags = resourceFlags(enumType, resourceSpec),
                )
            }
            .sortedBy { it.qualifiedName }
    }
```

Append these members:

```kotlin
    /** The recognised `extend google.protobuf.EnumOptions` field carrying resource flags. */
    private data class ResourceOptionSpec(
        val optionMember: ProtoMember,
        val messageType: ProtoType,
    )

    /**
     * An `EnumOptions` extension is ours if its message declares at least one known flag.
     * Matching on the option's name would hardcode `gen.resources`; matching every
     * `EnumOptions` extension would reject a consumer's unrelated ones.
     */
    private fun discoverResourceSpec(schema: Schema): ResourceOptionSpec? {
        val candidates = schema.protoFiles
            .flatMap { it.extendList }
            .filter { it.type == Options.ENUM_OPTIONS }
            .flatMap { it.fields }
            .mapNotNull { field ->
                val fieldType = field.type ?: return@mapNotNull null
                val message = schema.getType(fieldType) as? MessageType ?: return@mapNotNull null
                if (message.declaredFields.none { it.name in RESOURCE_FLAGS }) return@mapNotNull null
                message to ResourceOptionSpec(
                    optionMember = ProtoMember.get(Options.ENUM_OPTIONS, field.qualifiedName),
                    messageType = fieldType,
                )
            }

        if (candidates.isEmpty()) return null
        if (candidates.size > 1) {
            throw ProtoSchemaException(
                "Found ${candidates.size} google.protobuf.EnumOptions extensions declaring " +
                    "resource flags: ${candidates.joinToString { it.second.messageType.toString() }}. " +
                    "Declare exactly one.",
            )
        }

        val (message, spec) = candidates.single()

        // Rule 5 — every declared field must be a bool the plugin understands.
        for (field in message.declaredFields) {
            if (field.name !in RESOURCE_FLAGS) {
                throw ProtoSchemaException(
                    "Field `${field.name}` of `${spec.messageType}` is not a resource flag " +
                        "proto-extended understands. Supported flags: ${RESOURCE_FLAGS.joinToString()}.",
                )
            }
            if (field.type != ProtoType.BOOL || field.isRepeated) {
                throw ProtoSchemaException(
                    "Resource flag `${field.name}` of `${spec.messageType}` must be a bool, " +
                        "but is `${field.type}`.",
                )
            }
        }

        return spec
    }

    @Suppress("UNCHECKED_CAST")
    private fun resourceFlags(enumType: EnumType, spec: ResourceOptionSpec?): ResourceFlags {
        if (spec == null) return ResourceFlags()
        val flags = enumType.options.get(spec.optionMember) as? Map<ProtoMember, Any?>
            ?: return ResourceFlags()
        fun flag(name: String) = flags[ProtoMember.get(spec.messageType, name)]?.toString() == "true"
        return ResourceFlags(displayName = flag("display_name"), icon = flag("icon"))
    }
```

Add to the existing `private companion object`:

```kotlin
        /** Recognised `ResourceGen` flag field names, in the order they are reported. */
        val RESOURCE_FLAGS = setOf("display_name", "icon")
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.ProtoSchema*"`
Expected: PASS, 21 tests (12 reader, 9 validation).

- [ ] **Step 6: Commit**

```bash
git add proto-extended/
git commit -m "feat(proto-extended): read resource flags from a custom enum option"
```

---

### Task 6: Gradle extension and both tasks

**Files:**
- Create: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/ProtoExtendedExtension.kt`
- Create: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/GenerateProtoMetadataTask.kt`
- Create: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/GenerateProtoAndroidResourcesTask.kt`
- Create: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/ProtoExtendedPlugin.kt` (registration only; wiring in Task 7)
- Test: `proto-extended/src/test/kotlin/com/rohittp/plugables/protoextended/ProtoExtendedTestKitTest.kt`

**Interfaces:**
- Consumes: `ProtoSchemaReader`, `MetadataRenderer`, `AndroidResourceRenderer`.
- Produces:
  - `abstract class ProtoSpec` with `protoDir: DirectoryProperty`, `basePackage: Property<String>`, `outputDir: DirectoryProperty`
  - `abstract class MetadataSpec : ProtoSpec()`
  - `abstract class AndroidResourcesSpec : ProtoSpec()` adding `rPackage: Property<String>`
  - `abstract class ProtoExtendedExtension` with `val metadata: MetadataSpec`, `val androidResources: AndroidResourcesSpec`, and `fun metadata(Action<MetadataSpec>)` / `fun androidResources(Action<AndroidResourcesSpec>)`
  - `abstract class GenerateProtoMetadataTask : DefaultTask()` and `abstract class GenerateProtoAndroidResourcesTask : DefaultTask()`
  - `class ProtoExtendedPlugin : Plugin<Project>` registering `generateProtoMetadata` and `generateProtoAndroidResources`

- [ ] **Step 1: Write the extension**

Create `ProtoExtendedExtension.kt`. There is no `sourceSet` property — see the deviation note at the top of this plan.

```kotlin
package com.rohittp.plugables.protoextended

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/** Properties shared by both generator blocks. */
abstract class ProtoSpec {
    /** Directory containing the `.proto` sources. Setting it is what enables the block. */
    abstract val protoDir: DirectoryProperty

    /** Package of the generated file. */
    abstract val basePackage: Property<String>

    /** Defaults to `build/generated/source/protoExtended/<block>`; rarely overridden. */
    abstract val outputDir: DirectoryProperty
}

/** Pure-Kotlin metadata extension properties. Safe for `commonMain`. */
abstract class MetadataSpec : ProtoSpec()

/** Android `R`-bound accessors. Must be applied in the module that owns the resources. */
abstract class AndroidResourcesSpec : ProtoSpec() {
    /** Package holding the generated `R` class, e.g. `com.travelanimator.routemap`. */
    abstract val rPackage: Property<String>
}

abstract class ProtoExtendedExtension @Inject constructor(objects: ObjectFactory) {

    val metadata: MetadataSpec = objects.newInstance(MetadataSpec::class.java)

    val androidResources: AndroidResourcesSpec = objects.newInstance(AndroidResourcesSpec::class.java)

    fun metadata(action: Action<MetadataSpec>) = action.execute(metadata)

    fun androidResources(action: Action<AndroidResourcesSpec>) = action.execute(androidResources)
}
```

- [ ] **Step 2: Write both task classes**

Create `GenerateProtoMetadataTask.kt`.

```kotlin
package com.rohittp.plugables.protoextended

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates pure-Kotlin extension properties from proto enum metadata options.
 *
 * [protoFiles] rather than [protoDir] carries the input tracking: a required
 * `@InputDirectory` would fail at snapshot time when the block is unconfigured,
 * defeating the `NO-SOURCE` skip that makes an unused block harmless.
 */
abstract class GenerateProtoMetadataTask : DefaultTask() {

    @get:Internal
    abstract val protoDir: DirectoryProperty

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoFiles: ConfigurableFileCollection

    @get:Input
    abstract val basePackage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val enums = ProtoSchemaReader(protoDir.get().asFile).read()
        val source = MetadataRenderer.render(basePackage.get(), enums)
        writeGenerated(outputDir.get().asFile, basePackage.get(), "ProtoEnumMetadata.kt", source)
    }
}

/**
 * Clears the output directory and writes [fileName] under the package path.
 *
 * The wipe matters because `basePackage` is an input: changing it would otherwise
 * leave the previous package's file behind as a stale, still-compiled source.
 */
internal fun writeGenerated(outputDir: File, basePackage: String, fileName: String, source: String) {
    outputDir.deleteRecursively()
    val packageDir = File(outputDir, basePackage.replace('.', '/'))
    packageDir.mkdirs()
    File(packageDir, fileName).writeText(source)
}
```

Create `GenerateProtoAndroidResourcesTask.kt`.

```kotlin
package com.rohittp.plugables.protoextended

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * Generates `@get:StringRes val X.displayName` / `@get:DrawableRes val X.icon`
 * for enums carrying the `(gen.resources)` option.
 *
 * Must run in the module that owns the resources — a KMP library cannot see the
 * consuming app's `R`.
 */
abstract class GenerateProtoAndroidResourcesTask : DefaultTask() {

    @get:Internal
    abstract val protoDir: DirectoryProperty

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoFiles: ConfigurableFileCollection

    @get:Input
    abstract val basePackage: Property<String>

    @get:Input
    abstract val rPackage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val enums = ProtoSchemaReader(protoDir.get().asFile).read()
        val source = AndroidResourceRenderer.render(basePackage.get(), rPackage.get(), enums)
        writeGenerated(outputDir.get().asFile, basePackage.get(), "ProtoEnumResources.kt", source)
    }
}
```

- [ ] **Step 3: Write the plugin with registration only**

Create `ProtoExtendedPlugin.kt`. Source-set wiring is Task 7 — this step only registers the extension and tasks so TestKit can drive them.

```kotlin
package com.rohittp.plugables.protoextended

import org.gradle.api.Plugin
import org.gradle.api.Project

class ProtoExtendedPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("protoExtended", ProtoExtendedExtension::class.java)

        project.tasks.register(
            "generateProtoMetadata",
            GenerateProtoMetadataTask::class.java,
        ) { task ->
            task.description = "Generates Kotlin extension properties from proto enum metadata options."
            task.protoDir.set(extension.metadata.protoDir)
            task.protoFiles.from(protoFilesOf(extension.metadata))
            task.basePackage.set(extension.metadata.basePackage)
            task.outputDir.set(
                extension.metadata.outputDir.convention(
                    project.layout.buildDirectory.dir("generated/source/protoExtended/metadata"),
                ),
            )
        }

        project.tasks.register(
            "generateProtoAndroidResources",
            GenerateProtoAndroidResourcesTask::class.java,
        ) { task ->
            task.description = "Generates Android string/drawable accessors from proto enum resource options."
            task.protoDir.set(extension.androidResources.protoDir)
            task.protoFiles.from(protoFilesOf(extension.androidResources))
            task.basePackage.set(extension.androidResources.basePackage)
            task.rPackage.set(extension.androidResources.rPackage)
            task.outputDir.set(
                extension.androidResources.outputDir.convention(
                    project.layout.buildDirectory.dir("generated/source/protoExtended/androidResources"),
                ),
            )
        }
    }

    /**
     * An absent [ProtoSpec.protoDir] yields an absent provider, which contributes no
     * files — so an unconfigured block leaves its task with empty `@SkipWhenEmpty`
     * inputs and it reports `NO-SOURCE` instead of failing.
     */
    private fun protoFilesOf(spec: ProtoSpec) =
        spec.protoDir.map { dir -> dir.asFileTree.matching { it.include("**/*.proto") } }
}
```

- [ ] **Step 4: Write the failing TestKit tests**

Create `ProtoExtendedTestKitTest.kt`. This mirrors `BranchmarkTestKitTest`: the fixture applies only this plugin, so no AGP, no KGP and no network are involved.

```kotlin
package com.rohittp.plugables.protoextended

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional tests. Both tasks are registered unconditionally and only the source-set
 * wiring is gated on the Kotlin/Android plugins, so the real tasks can be driven with
 * no other plugin applied.
 */
class ProtoExtendedTestKitTest {

    private fun scaffold(tmp: File, buildScript: String) {
        File(tmp, "settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        File(tmp, "build.gradle.kts").writeText(buildScript.trimIndent())

        val proto = File(tmp, "proto").apply { mkdirs() }
        ProtoFixtures.write(proto, "gen_options.proto", ProtoFixtures.GEN_OPTIONS)
        ProtoFixtures.write(
            proto, "sample.proto",
            """
            syntax = "proto3";
            package ta;
            import "gen_options.proto";
            import "google/protobuf/descriptor.proto";
            option java_package = "com.example.model";

            message RatioMeta {
              int32 width = 1;
            }

            extend google.protobuf.EnumValueOptions {
              optional RatioMeta ratio_meta = 50001;
            }

            enum AspectRatio {
              option (gen.resources) = { display_name: true, icon: true };
              RATIO_1_1 = 0 [(ratio_meta) = { width: 1 }];
              RATIO_16_9 = 1 [(ratio_meta) = { width: 16 }];
            }
            """,
        )
    }

    private val bothBlocks = """
        plugins { id("com.rohittp.plugables.proto-extended") }
        protoExtended {
            metadata {
                protoDir.set(layout.projectDirectory.dir("proto"))
                basePackage.set("com.example.generated")
            }
            androidResources {
                protoDir.set(layout.projectDirectory.dir("proto"))
                basePackage.set("com.example.generated.res")
                rPackage.set("com.example.app")
            }
        }
    """

    private fun runner(tmp: File, vararg args: String) =
        GradleRunner.create()
            .withProjectDir(tmp)
            .withPluginClasspath()
            .withArguments(*args)

    @Test
    fun `metadata task generates the expected file`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        val result = runner(tmp, "generateProtoMetadata").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateProtoMetadata")!!.outcome)
        val generated = File(
            tmp,
            "build/generated/source/protoExtended/metadata/com/example/generated/ProtoEnumMetadata.kt",
        )
        assertTrue(generated.isFile, "expected generated file at ${generated.path}")
        val text = generated.readText()
        assertTrue(text.contains("val AspectRatio.width: Int"), text)
        assertTrue(text.contains("import com.example.model.AspectRatio"), text)
    }

    @Test
    fun `android resources task generates the expected file`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        val result = runner(tmp, "generateProtoAndroidResources").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateProtoAndroidResources")!!.outcome)
        val generated = File(
            tmp,
            "build/generated/source/protoExtended/androidResources/com/example/generated/res/ProtoEnumResources.kt",
        )
        assertTrue(generated.isFile, "expected generated file at ${generated.path}")
        val text = generated.readText()
        assertTrue(text.contains("import com.example.app.R"), text)
        assertTrue(text.contains("AspectRatio.RATIO_1_1 -> R.string.ratio_1_1"), text)
        assertTrue(text.contains("AspectRatio.RATIO_1_1 -> R.drawable.ratio_1_1"), text)
    }

    @Test
    fun `second run is up-to-date and a proto edit re-executes`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        assertEquals(
            TaskOutcome.SUCCESS,
            runner(tmp, "generateProtoMetadata").build().task(":generateProtoMetadata")!!.outcome,
        )
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            runner(tmp, "generateProtoMetadata").build().task(":generateProtoMetadata")!!.outcome,
        )

        File(tmp, "proto/sample.proto").appendText("\nenum Extra { E = 0; }\n")

        assertEquals(
            TaskOutcome.SUCCESS,
            runner(tmp, "generateProtoMetadata").build().task(":generateProtoMetadata")!!.outcome,
        )
    }

    @Test
    fun `unconfigured block reports NO-SOURCE`(@TempDir tmp: File) {
        scaffold(
            tmp,
            """
            plugins { id("com.rohittp.plugables.proto-extended") }
            protoExtended {
                metadata {
                    protoDir.set(layout.projectDirectory.dir("proto"))
                    basePackage.set("com.example.generated")
                }
            }
            """,
        )

        val result = runner(tmp, "generateProtoAndroidResources").build()

        assertEquals(
            TaskOutcome.NO_SOURCE,
            result.task(":generateProtoAndroidResources")!!.outcome,
        )
    }

    @Test
    fun `configuration cache is reused on the second run`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)

        runner(tmp, "generateProtoMetadata", "--configuration-cache").build()
        val second = runner(tmp, "generateProtoMetadata", "--configuration-cache").build()

        assertTrue(
            second.output.contains("Reusing configuration cache"),
            "expected configuration cache reuse, output was:\n${second.output}",
        )
    }

    @Test
    fun `validation failure fails the build with a readable message`(@TempDir tmp: File) {
        scaffold(tmp, bothBlocks)
        File(tmp, "proto/sample.proto").writeText(
            """
            syntax = "proto3";
            package ta;
            import "google/protobuf/descriptor.proto";

            message RatioMeta { int32 width = 1; }

            extend google.protobuf.EnumValueOptions {
              optional RatioMeta ratio_meta = 50001;
            }

            enum AspectRatio {
              RATIO_1_1 = 0 [(ratio_meta) = { width: 1 }];
              RATIO_16_9 = 1;
            }
            """.trimIndent(),
        )

        val result = runner(tmp, "generateProtoMetadata").buildAndFail()

        assertTrue(result.output.contains("RATIO_16_9"), result.output)
        assertTrue(result.output.contains("Every constant must set the option"), result.output)
    }
}
```

- [ ] **Step 5: Run to verify failure, then pass**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.ProtoExtendedTestKitTest"`

If Steps 1–3 are already written, this should PASS on the first run — the tests were written after the production code in this task because a Gradle plugin cannot be driven at all until it is registered. If any test fails, fix the production code, not the test.

Expected: PASS, 6 tests.

- [ ] **Step 6: Run the whole module**

Run: `./gradlew :proto-extended:build`
Expected: `BUILD SUCCESSFUL`, 42 tests.

- [ ] **Step 7: Commit**

```bash
git add proto-extended/
git commit -m "feat(proto-extended): add gradle extension, both generator tasks and functional tests"
```

---

### Task 7: Source-set wiring and the unwired diagnostic

**Files:**
- Modify: `proto-extended/src/main/kotlin/com/rohittp/plugables/protoextended/ProtoExtendedPlugin.kt`

**Interfaces:**
- Consumes: the task providers registered in Task 6.
- Produces: no new public API. Five wiring branches plus a configuration-time warning.

Per the Global Constraints there are no automated tests for this task — the reasoning is in the spec's Testing section. Verification is Task 9, against travel-animator.

- [ ] **Step 1: Add the wiring**

Rewrite `ProtoExtendedPlugin.kt`, keeping the registration from Task 6 and adding wiring. Note `findByName` rather than `getByName` for `androidMain`: a KMP project with no Android target has no such source set, and `getByName` would throw during `apply()`.

```kotlin
package com.rohittp.plugables.protoextended

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class ProtoExtendedPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("protoExtended", ProtoExtendedExtension::class.java)

        val metadataTask = project.tasks.register(
            "generateProtoMetadata",
            GenerateProtoMetadataTask::class.java,
        ) { task ->
            task.description = "Generates Kotlin extension properties from proto enum metadata options."
            task.protoDir.set(extension.metadata.protoDir)
            task.protoFiles.from(protoFilesOf(extension.metadata))
            task.basePackage.set(extension.metadata.basePackage)
            task.outputDir.set(
                extension.metadata.outputDir.convention(
                    project.layout.buildDirectory.dir("generated/source/protoExtended/metadata"),
                ),
            )
        }

        val resourcesTask = project.tasks.register(
            "generateProtoAndroidResources",
            GenerateProtoAndroidResourcesTask::class.java,
        ) { task ->
            task.description = "Generates Android string/drawable accessors from proto enum resource options."
            task.protoDir.set(extension.androidResources.protoDir)
            task.protoFiles.from(protoFilesOf(extension.androidResources))
            task.basePackage.set(extension.androidResources.basePackage)
            task.rPackage.set(extension.androidResources.rPackage)
            task.outputDir.set(
                extension.androidResources.outputDir.convention(
                    project.layout.buildDirectory.dir("generated/source/protoExtended/androidResources"),
                ),
            )
        }

        var metadataWired = false
        var resourcesWired = false

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.sourceSets.findByName("commonMain")?.let {
                it.kotlin.srcDir(metadataTask)
                metadataWired = true
            }
            kotlin.sourceSets.findByName("androidMain")?.let {
                it.kotlin.srcDir(resourcesTask)
                resourcesWired = true
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            val kotlin = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
            kotlin.sourceSets.findByName("main")?.let {
                it.kotlin.srcDir(metadataTask)
                metadataWired = true
            }
        }

        for (androidPluginId in listOf("com.android.application", "com.android.library")) {
            project.plugins.withId(androidPluginId) {
                val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
                components.onVariants { variant ->
                    variant.sources.kotlin?.addGeneratedSourceDirectory(
                        metadataTask,
                        GenerateProtoMetadataTask::outputDir,
                    )
                    variant.sources.kotlin?.addGeneratedSourceDirectory(
                        resourcesTask,
                        GenerateProtoAndroidResourcesTask::outputDir,
                    )
                }
                metadataWired = true
                resourcesWired = true
            }
        }

        // The one permitted afterEvaluate: purely diagnostic, nothing in the task graph
        // depends on it. Without this, a consumer with no recognised plugin gets a
        // successful generate task whose output nothing ever compiles.
        project.afterEvaluate {
            warnIfUnwired(project, "metadata", extension.metadata, metadataWired, metadataTask)
            warnIfUnwired(project, "androidResources", extension.androidResources, resourcesWired, resourcesTask)
        }
    }

    private fun warnIfUnwired(
        project: Project,
        blockName: String,
        spec: ProtoSpec,
        wired: Boolean,
        task: TaskProvider<*>,
    ) {
        if (wired || !spec.protoDir.isPresent) return
        project.logger.warn(
            "w: protoExtended { $blockName { … } } is configured, but no Kotlin Multiplatform, " +
                "Kotlin JVM or Android plugin was found to wire it into. Generated sources in " +
                "${spec.outputDir.get().asFile.relativeTo(project.projectDir)} are not on any " +
                "source set. Add them manually with " +
                "kotlin.srcDir(tasks.named(\"${task.name}\")) if that is intentional.",
        )
    }

    /**
     * An absent [ProtoSpec.protoDir] yields an absent provider, which contributes no
     * files — so an unconfigured block leaves its task with empty `@SkipWhenEmpty`
     * inputs and it reports `NO-SOURCE` instead of failing.
     */
    private fun protoFilesOf(spec: ProtoSpec) =
        spec.protoDir.map { dir -> dir.asFileTree.matching { it.include("**/*.proto") } }
}
```

- [ ] **Step 2: Verify the module still builds and all tests pass**

Run: `./gradlew :proto-extended:build`
Expected: `BUILD SUCCESSFUL`, 42 tests. The TestKit fixture applies no Kotlin or Android plugin, so it now also exercises the unwired warning path without failing.

- [ ] **Step 3: Confirm the warning appears**

Run: `./gradlew :proto-extended:test --tests "com.rohittp.plugables.protoextended.ProtoExtendedTestKitTest" --info`
Expected: the output contains `is configured, but no Kotlin Multiplatform`. This is an observation, not an assertion — no test asserts on it.

- [ ] **Step 4: Commit**

```bash
git add proto-extended/
git commit -m "feat(proto-extended): wire generated sources into kotlin and android source sets"
```

---

### Task 8: Documentation

**Files:**
- Create: `docs/proto-extended.html`
- Modify: `docs/index.html`, `docs/sitemap.xml`, `docs/llms.txt`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-09-proto-extended-design.md` (record the dropped `sourceSet`)

**Interfaces:**
- Consumes: the final DSL from Task 6.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Record the spec deviation**

In the spec's "Non-goals for v1" section, add:

```markdown
- **No configurable `sourceSet`** — dropped from the approved design. `plugins.withId`
  fires during `apply()`, before the consumer's `protoExtended { }` block runs, so the
  property is always unset when the wiring needs it. Source sets are hardcoded:
  `commonMain` (KMP metadata), `main` (Kotlin JVM metadata), `androidMain` (KMP
  resources). Wire a different one by hand with
  `kotlin.srcDir(tasks.named("generateProtoMetadata"))`; the unwired diagnostic
  tolerates it.
```

Then delete the now-inaccurate paragraph in "Task input/output properties" beginning "Both blocks expose an optional `sourceSet` property", the `sourceSet.set("commonMain")` line from the DSL example, and the sentence "`sourceSet` is left unset by default so each branch supplies its own default." in "Source-set wiring".

- [ ] **Step 2: Read the design system and an existing page**

Read `docs/DESIGN.md` in full, then `docs/typed-events.html` as the closest structural precedent (it is also a codegen plugin with a spec-file-driven DSL). Match its section order, class names and copy tone exactly. Do not invent new CSS — `docs/style.css` is shared and must not be edited.

- [ ] **Step 3: Write the docs page**

Create `docs/proto-extended.html` following that structure. It must cover:

- What it generates, with a before/after: the `.proto` enum on one side, the generated Kotlin on the other.
- The `gen_options.proto` the consumer must declare, verbatim from the spec.
- Both DSL blocks, with the `shared` / `app` two-module example from the spec — including `isolated.rootProject.projectDirectory` in the `app` block.
- A short "why two blocks" note: a KMP library module cannot see the consuming app's `R`.
- The five validation rules as a list, each with the one-line reason.
- Non-goals, copied from the spec.

- [ ] **Step 4: Add the cross-references**

- `docs/index.html` — add a plugin card matching the existing ones.
- `docs/sitemap.xml` — add a `<url>` entry for `proto-extended.html` copying the shape of the existing entries.
- `docs/llms.txt` — add a line matching the existing per-plugin format.
- `README.md` — add proto-extended to the plugin list in the same format as the others.

- [ ] **Step 5: Verify no page is broken**

Run: `grep -c "proto-extended" docs/index.html docs/sitemap.xml docs/llms.txt README.md`
Expected: a non-zero count for each of the four files.

- [ ] **Step 6: Commit**

```bash
git add docs/ README.md
git commit -m "docs(proto-extended): add plugin page and cross-references"
```

---

### Task 9: Migrate travel-animator

**Files (all in `/Users/rohittp/Data/Lascade/travel-animator-android`, a separate repository):**
- Create: `shared/src/commonMain/proto/gen_options.proto`
- Modify: `shared/src/commonMain/proto/animation_style.proto`
- Modify: `shared/src/commonMain/proto/animation_state.proto`
- Modify: `shared/build.gradle.kts`, `app/build.gradle.kts`
- Delete: `buildSrc/src/main/kotlin/com/lascade/proto/GenerateProtoExtensionsTask.kt`

**Interfaces:**
- Consumes: the published plugin, or `includeBuild` against the local plugables checkout.
- Produces: nothing.

This task verifies the source-set wiring that Task 7 deliberately ships untested. **Commit in the plugables repo first** — this task's commits belong to a different repository and must be made there.

- [ ] **Step 1: Publish the plugin locally**

Run (in plugables): `./gradlew :proto-extended:publishToMavenLocal`
Expected: `BUILD SUCCESSFUL`. Signing is skipped because `ORG_GRADLE_PROJECT_signingInMemoryKey` is unset.

- [ ] **Step 2: Add the options proto**

Create `shared/src/commonMain/proto/gen_options.proto`:

```proto
syntax = "proto3";

package gen;

import "google/protobuf/descriptor.proto";

// Per-enum opt-in for generated Android resource accessors. Flags are named for
// the Kotlin property they produce, not the resource folder, because the property
// name is what stays meaningful across platforms.
message ResourceGen {
    bool display_name = 1;   // val X.displayName: Int -> R.string.<constant>
    bool icon = 2;           // val X.icon: Int        -> R.drawable.<constant>
}

extend google.protobuf.EnumOptions {
    optional ResourceGen resources = 50100;
}
```

- [ ] **Step 3: Migrate animation_style.proto**

Add `import "gen_options.proto";` after the `syntax` line. Then, for each of the three enums, replace the `// gen:string` comment with an option line inside the enum body:

```proto
// gen:string          <- delete this line
enum Intro {
    option (gen.resources) = { display_name: true };   // <- add this line
    INTRO_DEFAULT = 0;
    …
}
```

Apply the identical change to `Running` and `Outro`.

- [ ] **Step 4: Migrate animation_state.proto**

Add `import "gen_options.proto";` alongside the existing imports. Then:

- `AspectRatio` — delete `// gen:string+drawable`, add `option (gen.resources) = { display_name: true, icon: true };` as the first line of the enum body.
- `Resolution` — delete `// gen:string`, add `option (gen.resources) = { display_name: true };`.
- `Distance.Unit` — delete `// gen:string`, add `option (gen.resources) = { display_name: true };`.

Leave every `[(distance_meta) = …]`, `[(aspect_ratio_meta) = …]` and `[(resolution_meta) = …]` on the constants untouched — metadata options do not change.

- [ ] **Step 5: Apply the metadata block in the shared module**

In `shared/build.gradle.kts`, add `id("com.rohittp.plugables.proto-extended")` to the `plugins { }` block, and add at the end of the file:

```kotlin
protoExtended {
    metadata {
        protoDir.set(layout.projectDirectory.dir("src/commonMain/proto"))
        basePackage.set("com.lascade.ta.shared.generated")
    }
}
```

Ensure `mavenLocal()` is available to `pluginManagement` and `dependencyResolutionManagement` for the local-publish flow.

- [ ] **Step 6: Replace the app-module task**

In `app/build.gradle.kts`:
- Delete the `import com.lascade.proto.GenerateProtoExtensionsTask` line.
- Delete the `val generateProtoExtensions = tasks.register<GenerateProtoExtensionsTask>("generateProtoExtensions") { … }` block.
- Delete the `variant.sources.kotlin?.addGeneratedSourceDirectory(generateProtoExtensions, GenerateProtoExtensionsTask::outputDir)` call from `androidComponents { onVariants { … } }`.
- Delete `dependsOn("generateProtoExtensions")` from the `tasks.named("preBuild")` block.
- Add `id("com.rohittp.plugables.proto-extended")` to the `plugins { }` block and:

```kotlin
protoExtended {
    androidResources {
        protoDir.set(isolated.rootProject.projectDirectory.dir("shared/src/commonMain/proto"))
        basePackage.set("com.travelanimator.routemap.generated")
        rPackage.set("com.travelanimator.routemap")
    }
}
```

- [ ] **Step 7: Delete the buildSrc task**

```bash
rm buildSrc/src/main/kotlin/com/lascade/proto/GenerateProtoExtensionsTask.kt
```

Leave `buildSrc/src/main/kotlin/com/lascade/mcp/` alone — `GenerateMcpSchemaTask` is unrelated and still in use. Leave `libs.wire.schema` in `buildSrc/build.gradle.kts`; the MCP task needs it too.

- [ ] **Step 8: Verify the wiring end to end**

Run (in travel-animator): `./gradlew :shared:generateProtoMetadata :app:generateProtoAndroidResources`
Expected: `BUILD SUCCESSFUL`, with both files written under each module's `build/generated/source/protoExtended/`.

Then run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. This is the real check — it proves the generated sources were compiled, which is exactly what Task 7 ships untested. `Unresolved reference: displayName` here means the wiring is wrong, not the generator.

- [ ] **Step 9: Confirm no call site changed**

Run: `git diff --stat` in travel-animator.
Expected: only the proto files, two build scripts and the deleted buildSrc task. No `.kt` file under `app/src` or `shared/src` should appear — the generated API is identical to what the buildSrc task produced.

- [ ] **Step 10: Commit in travel-animator**

```bash
git add -A
git commit -m "refactor: replace buildSrc proto extension task with proto-extended plugin"
```

---

## Self-Review

**Spec coverage.** Every spec section maps to a task: identity and DSL → Tasks 1/6; internal structure → Tasks 1–7 file-by-file; metadata semantics → Tasks 1/4; resource semantics → Tasks 2/5; all five validation rules → Tasks 4/5; package precedence, nested enums, determinism → Tasks 1/3; source-set wiring and the diagnostic → Task 7; dependencies → Task 1; testing → Tasks 1–6; repo housekeeping → Tasks 1/8; consumer migration → Task 9; non-goals → Task 8. `CONTEXT.md` was already written during the grill, so it is not a task here.

**Two spec claims this plan deliberately contradicts**, both flagged inline where they matter:
1. `sourceSet` is dropped — see the deviation note at the top and Task 8 Step 1.
2. Task 6's tests are written after its production code, breaking the usual TDD order, because a Gradle plugin cannot be executed at all until it is registered. Tasks 1–5 keep strict test-first order, and they hold the logic worth testing first.

**Type consistency.** `ProtoEnumInfo`, `MetaProperty`, `ConstantValue`, `KotlinScalar` and `ResourceFlags` are defined once in Task 1 and used unchanged in Tasks 2–6. `ProtoSchemaReader.read()` keeps one signature across Tasks 3, 4 and 5 — later tasks fill in fields rather than adding entry points. `writeGenerated` is declared once in Task 6 and shared by both tasks. `RESERVED_NAMES` (Task 4) and `RESOURCE_FLAGS` (Task 5) both live in the same `private companion object`.
