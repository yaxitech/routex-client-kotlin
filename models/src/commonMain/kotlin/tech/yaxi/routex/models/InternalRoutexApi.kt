package tech.yaxi.routex.models

/**
 * Marks declarations that are **internal** in the Routex client API. Their signatures
 * and semantics may change between releases without warnings and without providing
 * migration aids; external use is unsupported.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal Routex API. External use is unsupported.",
)
public annotation class InternalRoutexApi
