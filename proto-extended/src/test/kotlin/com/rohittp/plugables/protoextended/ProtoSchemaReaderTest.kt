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
        // Fixture filenames deliberately run counter to the expected enum order:
        // okio's FileSystem.list() hands SchemaLoader its entries already path-sorted,
        // so naming these alpha/zebra would let this test pass with no sort at all.
        ProtoFixtures.write(
            tmp, "aaa.proto",
            """
            syntax = "proto3";
            package ta;
            enum Zebra { Z = 0; }
            """,
        )
        ProtoFixtures.write(
            tmp, "zzz.proto",
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
              float ratio = 3;
              int32 exponent = 4;
              int64 atomicWeight = 5;
              bool metric = 6;
            }

            extend google.protobuf.EnumValueOptions {
              optional UnitMeta unit_meta = 50001;
            }

            enum Unit {
              KM = 0 [(unit_meta) = {
                symbol: "km", multiplier: 1.0, ratio: 1.0, exponent: 3, atomicWeight: 1, metric: true
              }];
              MILE = 1 [(unit_meta) = {
                symbol: "mi", multiplier: 0.621371, ratio: 0.62, exponent: -1, atomicWeight: 2, metric: false
              }];
            }
            """,
        )

        val enum = ProtoSchemaReader(tmp).read().single()
        val symbol = enum.metaProperties.single { it.name == "symbol" }
        val multiplier = enum.metaProperties.single { it.name == "multiplier" }
        val ratio = enum.metaProperties.single { it.name == "ratio" }
        val exponent = enum.metaProperties.single { it.name == "exponent" }
        val atomicWeight = enum.metaProperties.single { it.name == "atomicWeight" }
        val metric = enum.metaProperties.single { it.name == "metric" }

        assertEquals(KotlinScalar.STRING, symbol.type)
        assertEquals(KotlinScalar.DOUBLE, multiplier.type)
        assertEquals(KotlinScalar.FLOAT, ratio.type)
        assertEquals(KotlinScalar.INT, exponent.type)
        assertEquals(KotlinScalar.LONG, atomicWeight.type)
        assertEquals(KotlinScalar.BOOLEAN, metric.type)
        assertEquals(listOf("KM", "MILE"), symbol.values.map { it.constantName })
        assertEquals(listOf("km", "mi"), symbol.values.map { it.rawValue })
        assertEquals(listOf("1.0", "0.621371"), multiplier.values.map { it.rawValue })
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
}
