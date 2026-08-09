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
