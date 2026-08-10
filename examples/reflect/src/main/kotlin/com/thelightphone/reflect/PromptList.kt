package com.thelightphone.reflect

import kotlinx.datetime.LocalDate

data class ReflectPrompt(
    val id: Int,
    val text: String,
)

object PromptList {
    val prompts: List<ReflectPrompt> = listOf(
        "What made you smile today?",
        "What's one thing you're grateful for right now?",
        "Describe a moment today when you felt at ease.",
        "What's something you're looking forward to?",
        "Who made a difference in your day, and how?",
        "What's a small win you had today?",
        "What's weighing on your mind right now?",
        "What would make tomorrow a good day?",
        "What did you learn about yourself today?",
        "Where did you notice beauty today?",
        "What's a challenge you handled well recently?",
        "What do you need more of in your life right now?",
        "What's a memory that's been on your mind lately?",
        "What are you proud of this week?",
        "What's something you'd like to let go of?",
        "How did you take care of yourself today?",
        "What conversation stuck with you today?",
        "What's one thing you want to remember about today?",
        "What's bringing you peace lately?",
        "What would you tell a friend who had your day?",
        "Look at the last picture you took. What memory does it bring back?",
        "What's the story behind the last photo you took?",
        "If you took a photo of today, what would it be of?",
        "Find an old photo on your phone. What do you remember about that day?",
    ).mapIndexed { index, text -> ReflectPrompt(id = index, text = text) }

    /** Deterministic prompt of the day: stable across the whole day, changes daily. */
    fun promptForDate(date: LocalDate): ReflectPrompt {
        val index = date.toEpochDays().mod(prompts.size)
        return prompts[index]
    }
}
