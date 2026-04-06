package com.rohittp.plugables.viewmodelstub

/** Parsed representation of a single ViewModel class annotated with `@ViewModelStub`. */
data class ViewModelInfo(
    val packageName: String,
    val implClassName: String,
    val interfaceName: String,
    val stubClassName: String,
    val imports: List<String>,
    val properties: List<PropertyInfo>,
    val methods: List<MethodInfo>,
)

data class PropertyInfo(
    val name: String,
    /** Whether the property is declared with `var` (mutable) vs `val`. */
    val isMutable: Boolean,
    /** The full declared type string, e.g. `StateFlow<Boolean>` or `MutableStateFlow<Paywall?>`. */
    val type: String,
)

data class MethodInfo(
    val name: String,
    val parameters: List<ParameterInfo>,
    /** Return type string, or `null` when the method returns Unit implicitly. */
    val returnType: String?,
    val isSuspend: Boolean,
)

data class ParameterInfo(
    val name: String,
    val type: String,
    /** Original default value expression, or null if no default. */
    val defaultValue: String? = null,
)
