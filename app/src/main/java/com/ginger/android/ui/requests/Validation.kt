package com.ginger.android.ui.requests

import com.ginger.android.R

sealed class ValidationError {
    object TitleEmpty : ValidationError()
    object DescriptionTooShort : ValidationError()
    object ContactMissing : ValidationError()
    object ContactInvalid : ValidationError()
}

fun ValidationError.toMessageRes(): Int = when (this) {
    ValidationError.TitleEmpty -> R.string.error_fill_all_fields
    ValidationError.DescriptionTooShort -> R.string.error_description_short
    ValidationError.ContactMissing -> R.string.error_contact_required
    ValidationError.ContactInvalid -> R.string.error_invalid_owner_phone
}
