package com.jaivish.gowork.engine

object Messages {

    private val lines = listOf(
        "Scrolling won't edit your videos.",
        "Your future self is watching. Close it.",
        "Creators create. Consumers scroll. Pick one.",
        "That reel can wait. Your goals can't.",
        "Nobody got rich watching Shorts.",
        "The algorithm is winning. Take your time back.",
        "Close the app. Open your project.",
        "Discipline now, freedom later.",
        "Every minute here is a minute not building.",
        "Your competition isn't scrolling right now.",
        "Make content. Don't just consume it.",
        "One small task. Start it now.",
        "You came here to relax. It isn't working.",
        "Two minutes gone. Don't make it twenty.",
        "Be the one people scroll to, not the one scrolling.",
        "Your timeline will still be there. Your deadline won't wait.",
        "Dreams don't work unless you do.",
        "Put the phone down. Pick the work up.",
        "This feed is someone else's work. Go make yours.",
        "Focus is a superpower. Use it.",
        "Ten focused minutes beat two hours of scrolling.",
        "You don't need more inspiration. You need to start.",
        "Hard work today, easy life tomorrow.",
        "The edit won't finish itself.",
    )

    fun line(seed: Long): String = lines[Math.floorMod(seed, lines.size.toLong()).toInt()]

    fun headline(kind: LockKind): String = when (kind) {
        LockKind.OPEN_PAUSE -> "WAIT. WHY ARE YOU HERE?"
        LockKind.NUDGE -> "GO DO YOUR WORK."
        LockKind.LIMIT -> "THAT'S ENOUGH FOR TODAY."
        LockKind.FOCUS -> "IT'S FOCUS TIME."
        LockKind.SPRINT -> "YOU'RE IN A SPRINT."
        LockKind.SHORT_FORM -> "NO REELS. NO SHORTS."
    }

    /** What the voice says out loud. */
    fun spoken(kind: LockKind, name: String): String {
        val who = name.trim().takeIf { it.isNotEmpty() }?.let { "$it, " }.orEmpty()
        return when (kind) {
            LockKind.OPEN_PAUSE -> "${who}why are you opening this?"
            LockKind.NUDGE -> "${who}go do your work."
            LockKind.LIMIT -> "${who}that's enough for today. Go do your work."
            LockKind.FOCUS, LockKind.SPRINT -> "${who}it's focus time. Go do your work."
            LockKind.SHORT_FORM -> "${who}no reels. Go do your work."
        }
    }
}
