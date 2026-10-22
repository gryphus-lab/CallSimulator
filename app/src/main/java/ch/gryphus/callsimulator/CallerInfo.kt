package ch.gryphus.callsimulator

/**
 * Framework-free caller details and the pure helpers that derive them.
 *
 * All logic here is deliberately independent of the Android framework (no
 * Context, Activity, Cursor, or Uri) so it can be covered by fast JVM unit
 * tests. The Activities handle only the Android plumbing (intents, pickers,
 * ContentResolver) and delegate every decision to these functions.
 */
data class CallerInfo(
    val name: String,
    val number: String,
    /** String form of the caller photo URI, or null for the default avatar. */
    val photo: String? = null,
)

object CallerDetails {

    /**
     * Resolve the caller name: trimmed user input, or [default] when blank.
     */
    fun resolveName(input: String?, default: String): String =
        input?.trim().orEmpty().ifEmpty { default }

    /**
     * Resolve the caller number: trimmed user input, or [default] when blank.
     */
    fun resolveNumber(input: String?, default: String): String =
        input?.trim().orEmpty().ifEmpty { default }

    /**
     * A contact display name is only usable if it is non-blank; otherwise the
     * existing field value should be kept.
     */
    fun isUsableContactName(name: String?): Boolean = !name.isNullOrBlank()

    /** A contact has a dialable number when its HAS_PHONE_NUMBER flag is > 0. */
    fun hasPhoneNumber(hasPhoneFlag: Int?): Boolean = (hasPhoneFlag ?: 0) > 0

    /**
     * Normalize a raw contact photo URI value to the string the call screens
     * expect: a non-blank URI string, or null when absent/blank.
     */
    fun normalizePhoto(rawPhotoUri: String?): String? =
        rawPhotoUri?.trim()?.ifEmpty { null }

    /**
     * Choose the best photo for the FULL-SCREEN caller ID.
     *
     * The native dialer shows the large "Calling Card" (full-size display)
     * photo, not the small circular "Contact picture" thumbnail. Prefer, in
     * order: the full-size display photo, then the standard display photo
     * (PHOTO_URI), then the thumbnail (PHOTO_THUMBNAIL_URI). Each candidate is
     * normalized and the first non-blank one wins; null if none is set.
     *
     * @param displayPhotoUri the full-size "calling card" photo (DISPLAY_PHOTO),
     *   or null if the contact has no full-size photo / it couldn't be opened.
     * @param photoUri the contact's PHOTO_URI (display photo).
     * @param thumbnailUri the contact's PHOTO_THUMBNAIL_URI (inset thumbnail).
     */
    fun preferredContactPhoto(
        displayPhotoUri: String?,
        photoUri: String?,
        thumbnailUri: String?,
    ): String? =
        normalizePhoto(displayPhotoUri)
            ?: normalizePhoto(photoUri)
            ?: normalizePhoto(thumbnailUri)

    /**
     * Build the fields to apply to the form from a resolved contact row.
     * Returns only the parts that should overwrite the current form values;
     * null members mean "leave the current value untouched".
     *
     * [displayPhotoUri] is the full-size "Calling Card" photo and takes priority
     * over [rawPhotoUri] (the standard display photo) for the full-screen UI.
     */
    fun fieldsFromContact(
        displayName: String?,
        hasPhoneFlag: Int?,
        primaryNumber: String?,
        rawPhotoUri: String?,
        displayPhotoUri: String? = null,
        thumbnailUri: String? = null,
    ): ContactFields = ContactFields(
        name = if (isUsableContactName(displayName)) displayName!!.trim() else null,
        number = if (hasPhoneNumber(hasPhoneFlag)) primaryNumber?.trim()?.ifEmpty { null } else null,
        photo = preferredContactPhoto(displayPhotoUri, rawPhotoUri, thumbnailUri),
    )
}

/** Form fields derived from a picked contact; null = keep current value. */
data class ContactFields(
    val name: String?,
    val number: String?,
    val photo: String?,
)
