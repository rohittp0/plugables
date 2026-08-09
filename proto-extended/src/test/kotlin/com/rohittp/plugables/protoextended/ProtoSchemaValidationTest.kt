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
