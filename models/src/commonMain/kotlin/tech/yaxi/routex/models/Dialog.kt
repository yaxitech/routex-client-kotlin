package tech.yaxi.routex.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * User dialog returned by an interactive service call.
 *
 * Meant to be displayed in some user interface and consists of:
 *  - a way to cancel the dialog (typically an X symbol and / or a "Cancel" button);
 *  - a display part with an optional
 *    [message][tech.yaxi.routex.models.Dialog.message] and
 *    [image][tech.yaxi.routex.models.Dialog.image];
 *  - an interactive part defined by
 *    [input][tech.yaxi.routex.models.Dialog.input].
 *
 * The contained [DialogInput] carries a context that the client passes back when
 * continuing the process with `respond*` or `confirm*`.
 */
@ConsistentCopyVisibility
@Serializable
public data class Dialog internal constructor(
    public val context: DialogContext? = null,
    public val message: String? = null,
    public val image: Image? = null,
    public val input: DialogInput,
)

/**
 * Image data for a dialog.
 *
 * @property mimeType MIME type of the image (e.g. `image/png`, `image/gif`).
 * @property data Binary image data in the format defined by
 *   [mimeType][tech.yaxi.routex.models.Image.mimeType].
 * @property hhdUcData Raw HHD_UC data block for optical coupling with a
 *   HandHeld-Device (HHD) used to generate an OTP.
 *   [data][tech.yaxi.routex.models.Image.data] already provides a pre-rendered
 *   animated GIF to be presented at a width of 62.5 mm;
 *   [hhdUcData][tech.yaxi.routex.models.Image.hhdUcData] is only relevant for
 *   clients that want to render the optical pattern themselves.
 */
@Serializable
public class Image internal constructor(
    public val mimeType: String,
    @Serializable(with = Base64ByteArraySerializer::class)
    public val data: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    public val hhdUcData: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is Image &&
            mimeType == other.mimeType &&
            data.contentEquals(other.data) &&
            byteArraysEqual(hhdUcData, other.hhdUcData)

    override fun hashCode(): Int {
        var r = mimeType.hashCode()
        r = 31 * r + data.contentHashCode()
        r = 31 * r + (hhdUcData?.contentHashCode() ?: 0)
        return r
    }

    override fun toString(): String {
        val hhd = hhdUcData?.let { "<${it.size} bytes>" } ?: "null"
        return "Image(mimeType=$mimeType, data=<${data.size} bytes>, hhdUcData=$hhd)"
    }

    private fun byteArraysEqual(
        a: ByteArray?,
        b: ByteArray?,
    ): Boolean =
        when {
            a === b -> true
            a == null || b == null -> false
            else -> a.contentEquals(b)
        }
}

/**
 * Single option inside a [DialogInput.Selection].
 *
 * @property key Stable identifier to echo back as the response when the user picks this
 *   option.
 * @property label Localised, user-visible label.
 * @property explanation Optional additional guidance shown next to the label.
 */
@ConsistentCopyVisibility
@Serializable
public data class DialogOption internal constructor(
    public val key: String,
    public val label: String,
    public val explanation: String? = null,
)

/** Data defining the interactive part of a [Dialog]. */
@Serializable(with = DialogInputSerializer::class)
public sealed class DialogInput {
    /**
     * Just a primary action to confirm the dialog.
     *
     * @property context Context to pass back when confirming the dialog.
     * @property pollingDelaySecs If polling is acceptable, a delay in seconds the client
     *   has to wait before automatically confirming.
     */
    @ConsistentCopyVisibility
    @Serializable
    public data class Confirmation internal constructor(
        public val context: ConfirmationContext,
        public val pollingDelaySecs: UInt? = null,
    ) : DialogInput()

    /**
     * Selection of options the user can choose from.
     *
     * Options are meant to be rendered e.g. as radio buttons where the user must select
     * exactly one to enable a confirmation button. Another valid implementation is one
     * button per option that immediately confirms the selection.
     *
     * @property options Available choices.
     * @property context Context to pass back when responding to the dialog.
     */
    @ConsistentCopyVisibility
    @Serializable
    public data class Selection internal constructor(
        public val options: List<DialogOption>,
        public val context: InputContext,
    ) : DialogInput()

    /**
     * Free-form input field.
     *
     * @property type Type that may be used for showing hints or dedicated keyboard
     *   layouts and for applying input restrictions or validation.
     * @property secrecyLevel Whether and how the input should be masked.
     * @property minLength Minimum length accepted.
     * @property maxLength Maximum length accepted.
     * @property context Context to pass back when responding to the dialog.
     */
    @ConsistentCopyVisibility
    @Serializable
    public data class Field internal constructor(
        public val type: InputType,
        public val secrecyLevel: SecrecyLevel,
        public val minLength: UInt? = null,
        public val maxLength: UInt? = null,
        public val context: InputContext,
    ) : DialogInput()
}

/** @suppress */
public object DialogInputSerializer : ExternallyTaggedSerializer<DialogInput>("DialogInput") {
    override fun variantName(value: DialogInput): String =
        when (value) {
            is DialogInput.Confirmation -> "Confirmation"
            is DialogInput.Selection -> "Selection"
            is DialogInput.Field -> "Field"
        }

    override fun childSerializer(variant: String): KSerializer<out DialogInput> =
        when (variant) {
            "Confirmation" -> DialogInput.Confirmation.serializer()
            "Selection" -> DialogInput.Selection.serializer()
            "Field" -> DialogInput.Field.serializer()
            else -> throw SerializationException("Unknown DialogInput variant: $variant")
        }

    override fun encodePayload(value: DialogInput): JsonElement =
        when (value) {
            is DialogInput.Confirmation ->
                RoutexJson.encodeToJsonElement(DialogInput.Confirmation.serializer(), value) as JsonObject
            is DialogInput.Selection ->
                RoutexJson.encodeToJsonElement(DialogInput.Selection.serializer(), value) as JsonObject
            is DialogInput.Field ->
                RoutexJson.encodeToJsonElement(DialogInput.Field.serializer(), value) as JsonObject
        }
}
