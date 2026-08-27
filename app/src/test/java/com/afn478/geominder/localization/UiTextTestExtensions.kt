package com.afn478.geominder.localization

fun UiText.resourceId(): Int = when (this) {
    is UiText.Resource -> id
    is UiText.Plain -> throw AssertionError("Expected a string resource, got '$value'")
}

fun UiText.plainValue(): String = when (this) {
    is UiText.Resource -> throw AssertionError("Expected plain text, got resource $id")
    is UiText.Plain -> value
}
