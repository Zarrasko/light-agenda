package com.thelightphone.kaginews

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Keep each category to a handful of top stories rather than an endless scroll.
internal const val TOP_STORIES_PER_CATEGORY = 3

@Serializable
internal data class NewsIndex(
    val timestamp: Long = 0,
    val categories: List<NewsCategory> = emptyList(),
)

@Serializable
data class NewsCategory(
    val name: String,
    val file: String,
)

@Serializable
internal data class CategoryFeed(
    val category: String = "",
    val timestamp: Long = 0,
    val clusters: List<NewsCluster> = emptyList(),
)

@Serializable
data class NewsCluster(
    @SerialName("cluster_number") val clusterNumber: Int = 0,
    val title: String = "",
    @SerialName("short_summary") val shortSummary: String = "",
    @SerialName("did_you_know") val didYouKnow: String? = null,
    val emoji: String = "",
    val domains: List<NewsDomain> = emptyList(),
)

@Serializable
data class NewsDomain(
    val name: String = "",
)

fun NewsCluster.sourceSummary(): String = when (domains.size) {
    0 -> "No sources listed"
    1 -> domains.first().name
    else -> "${domains.size} sources: " + domains.joinToString(", ") { it.name }
}

private val CITATION_MARKER = Regex("""\[[^\[\]]+#\d+]""")
// Deliberately [ \t], not \s: short_summary already contains real "\n\n" paragraph
// breaks (see splitIntoParagraphs below), and a \s-based cleanup here would flatten them.
private val SPACE_BEFORE_PUNCTUATION = Regex("""[ \t]+([.,;:!?])""")
private val REPEATED_SPACE = Regex("""[ \t]{2,}""")

// Kite's short_summary/did_you_know text embeds inline citation markers like
// "[aljazeera.com#1][firstpost.com#3]" for its own web frontend to turn into footnote
// links. There's nowhere to click through to on a Light Phone, so they just read as noise.
fun String.stripCitationMarkers(): String = this
    .replace(CITATION_MARKER, "")
    .replace(SPACE_BEFORE_PUNCTUATION, "$1")
    .replace(REPEATED_SPACE, " ")
    .trim()

private val PARAGRAPH_BREAK = Regex("""\n\s*\n""")
private val SENTENCE_BOUNDARY = Regex("""(?<=[.!?])\s+(?=[A-Z0-9"'(])""")
private const val FALLBACK_SENTENCES_PER_PARAGRAPH = 6

// short_summary is already authored as a few "\n\n"-separated paragraphs - split on
// those first. Only a summary with no natural break at all (rare) falls back to
// chunking by sentence count, so nothing renders as one dense wall of text.
fun String.splitIntoParagraphs(): List<String> {
    val authored = PARAGRAPH_BREAK.split(trim()).map { it.trim() }.filter { it.isNotEmpty() }
    if (authored.size > 1) return authored

    val sentences = SENTENCE_BOUNDARY.split(trim()).map { it.trim() }.filter { it.isNotEmpty() }
    if (sentences.size <= FALLBACK_SENTENCES_PER_PARAGRAPH) return authored

    val chunks = sentences.chunked(FALLBACK_SENTENCES_PER_PARAGRAPH).toMutableList()
    if (chunks.size >= 2 && chunks.last().size <= FALLBACK_SENTENCES_PER_PARAGRAPH / 2) {
        val tail = chunks.removeAt(chunks.lastIndex)
        chunks[chunks.lastIndex] = chunks.last() + tail
    }
    return chunks.map { it.joinToString(" ") }
}
