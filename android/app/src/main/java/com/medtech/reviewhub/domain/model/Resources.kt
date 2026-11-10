package com.medtech.reviewhub.domain.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Philippine ASCPi resources (shared/content/resources.json). The file is
// optional at build time; when it is absent the Resources screen shows an
// empty state and the app never fabricates content. Field names match the
// schema used by the website:
//   reviewCenters: name, location, program, description, feeNote, inclusions,
//                  contact
//   ascpLinks:     title, url, description
//   readingList:   title, authors, edition, publisher, year, description,
//                  licenseNote
//   examInfo:      title, body
//   disclaimer:    rendered at the bottom of the screen
// ---------------------------------------------------------------------------

@Serializable
data class ResourcesFile(
    val reviewCenters: List<ReviewCenter> = emptyList(),
    val ascpLinks: List<OfficialLink> = emptyList(),
    val readingList: List<ReadingListEntry> = emptyList(),
    val examInfo: List<ExamInfoEntry> = emptyList(),
    val disclaimer: String = ""
) {
    val isEmpty: Boolean
        get() = reviewCenters.isEmpty() && ascpLinks.isEmpty() &&
            readingList.isEmpty() && examInfo.isEmpty() && disclaimer.isBlank()

    companion object {
        val EMPTY = ResourcesFile()
    }
}

@Serializable
data class ReviewCenter(
    val name: String = "",
    val location: String = "",
    val program: String = "",
    val description: String = "",
    val feeNote: String = "",
    val inclusions: List<String> = emptyList(),
    val contact: String = ""
)

@Serializable
data class OfficialLink(
    val title: String = "",
    val url: String = "",
    val description: String = ""
)

@Serializable
data class ReadingListEntry(
    val title: String = "",
    val authors: List<String> = emptyList(),
    val edition: String = "",
    val publisher: String = "",
    val year: Int = 0,
    val description: String = "",
    val licenseNote: String = ""
)

@Serializable
data class ExamInfoEntry(
    val title: String = "",
    val body: String = ""
)
