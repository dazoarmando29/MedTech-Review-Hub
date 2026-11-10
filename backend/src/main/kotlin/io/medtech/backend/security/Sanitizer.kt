package io.medtech.backend.security

object Sanitizer {
    private val tagRegex = Regex("<[^>]*>")
    private val controlRegex = Regex("[\\p{Cntrl}&&[^\r\n\t]]")

    fun clean(input: String, maxLen: Int = 10000): String {
        val noTags = tagRegex.replace(input, "")
        val noControls = controlRegex.replace(noTags, "")
        val collapsed = noControls.replace(Regex("\n{3,}"), "\n\n").trim()
        return if (collapsed.length > maxLen) collapsed.substring(0, maxLen) else collapsed
    }

    fun cleanToken(input: String, maxLen: Int = 200): String = input.trim().take(maxLen)
}
