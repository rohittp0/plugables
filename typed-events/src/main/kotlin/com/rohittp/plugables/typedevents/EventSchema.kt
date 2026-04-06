package com.rohittp.plugables.typedevents

data class ParamSpec(val type: String, val info: String)

data class EventSpec(
    val eventName: String,
    val info: String,
    val function: String,
    val params: Map<String, ParamSpec>
)
