package com.example.myapplication

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import kotlin.math.abs

class MaskedInputWatcher(
    private val editText: EditText?
) : TextWatcher {
    private var isEditing: Boolean = false
    private var isDeleting = false
    private var isCopying = false
    private var previousCursor = 0
    private var charCount = 0
    var mask: String? = null

    private val inputPattern = '_'
    private val maskInputPattern = Regex("${inputPattern}+")

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (isEditing) return
        isDeleting = count > after
        isCopying = after > 0 && count > 0

        previousCursor = start
        charCount = abs(after - count)
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (isEditing) return
    }

    override fun afterTextChanged(s: Editable?) {
        if (mask == null || editText == null) return
        val mask = mask!!
        if (isEditing) return
        try {
            isEditing = true
            val rawText = s.toString()
            val formatted = formatStructReference(rawText, mask)
            if (rawText == formatted) return

            editText.setText(formatted)

            var nextCursor = if (isCopying) previousCursor + charCount
            else if (isDeleting) previousCursor - charCount
            else previousCursor + charCount

            while (isCursorValid(nextCursor)) {
                if (!isDeleting || isCopying) {
                    if (mask[nextCursor] != inputPattern) {
                        nextCursor += 1
                    } else {
                        if (formatted[nextCursor] != inputPattern) {
                            nextCursor += 1
                        } else break
                    }
                } else {
                    if (mask[nextCursor] != inputPattern) {
                        nextCursor -= 1
                    } else {
                        break
                    }
                }
            }
            if (isDeleting) {
                nextCursor += 1
            }

            editText.setSelection(maxOf(minOf(nextCursor, mask.length), 0))
        } finally {
            isEditing = false
        }
    }

    private fun isCursorValid(nextCursor: Int): Boolean {
        return nextCursor >= 0 && nextCursor <= mask!!.length - 1
    }

    private fun formatStructReference(value: String, mask: String): String {
        val cleaned = value.filter { it.isLetterOrDigit() }

        // Extract the placeholder structure from the mask
        val groupLengths = maskInputPattern.findAll(mask).map { it.value.length }.toList()
        val totalLength = groupLengths.sum()

        // Pad or trim cleaned value to fit mask
        val padded = cleaned.padEnd(totalLength, '_').take(totalLength)

        // Replace each group of underscores with corresponding part of the value
        var index = 0
        val formatted = maskInputPattern.replace(mask) { match ->
            val len = match.value.length
            val part = padded.substring(index, index + len)
            index += len
            part
        }

        return formatted
    }

}