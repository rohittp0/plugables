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
