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

    /**
     * Defaults to `build/generated/source/protoExtended/<block>`; rarely overridden.
     *
     * **Warning:** the generating task deletes this directory recursively before every
     * write (so a stale file from a previous `basePackage` never lingers as compiled
     * source). It must therefore point at a directory owned exclusively by that task —
     * never a directory containing hand-written sources, such as `src/main/kotlin`.
     * Pointing it there will delete those sources.
     */
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
