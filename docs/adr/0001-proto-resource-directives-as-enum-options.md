# Resource directives are custom enum options, not comments

proto-extended decides which enums get generated Android resource accessors by
reading a custom `extend google.protobuf.EnumOptions` field
(`option (gen.resources) = { display_name: true, icon: true };`). Its predecessor
— `GenerateProtoExtensionsTask` in travel-animator's `buildSrc/` — read a
`// gen:string+drawable` leading comment instead. We switched because a comment
is invisible to protoc and validated by nothing: `// gen:sting` generates no
property, reports no error, and surfaces as `Unresolved reference` at a call site
far from the cause. A custom option makes the same typo a schema linker error
naming the file and line.

## Considered Options

Keeping the comment form was genuinely attractive: it costs no migration, and it
was the established contract this plugin was meant to be a drop-in replacement
for. A hybrid — option as primary, comment as deprecated fallback — was also on
the table.

We rejected both because the migration is small and bounded (one new
`gen_options.proto`, two imports, six directive lines across `animation_style.proto`
and `animation_state.proto`), and because supporting two spellings of one concept
means two code paths forever. The hybrid buys compatibility with a schema we
control and were about to edit anyway.

## Consequences

The flags are named for the **Kotlin property** they generate (`display_name`,
`icon`) rather than the Android resource folder (`string`, `drawable`). The
property name is the part that stays meaningful now that the plugin is
multiplatform-aware, and `bool string = 1;` is a poor field name regardless.

Reading the option is structurally identical to how the metadata half already
reads `EnumValueOptions`, one descriptor level up — `Options.ENUM_OPTIONS` and
`EnumType.options`. Both halves of the plugin became the same operation, and
`parseGenDirective` with its `documentation` string-munging was deleted.

The plugin no longer depends on Wire preserving proto comments as
`Type.documentation`, which was never a documented guarantee.
