package io.github.qingshu.mcptool.ksp

import io.github.qingshu.mcptool.annotations.Required

internal fun inferRequiredness(
    nullable: Boolean,
    hasDefault: Boolean,
    explicit: Required,
): Boolean = when (explicit) {
    Required.UNSPECIFIED -> !nullable && !hasDefault

    Required.FALSE -> false

    Required.TRUE -> {
        require(!nullable && !hasDefault) {
            "Parameter cannot be required when its Kotlin type is nullable or has a default value."
        }
        true
    }
}
