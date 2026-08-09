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
            import "wire/extensions.proto";
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
