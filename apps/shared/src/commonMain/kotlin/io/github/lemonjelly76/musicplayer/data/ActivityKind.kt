package io.github.lemonjelly76.musicplayer.data

/**
 * What an activity is for, read from the words in its name, so the selector can show a fitting icon for categories
 * Claude creates without the server knowing about icons.
 */
enum class ActivityKind(private val words: Set<String>) {
    MAINSTREAM(emptySet()),
    RUNNING(setOf("run", "runs", "running", "jog", "jogging", "marathon")),
    WORKOUT(setOf("workout", "gym", "training", "lifting", "cycling", "ride")),
    CHILL(setOf("chill", "relax", "relaxing", "calm", "unwind", "sunday", "lazy")),
    DINNER(setOf("dinner", "dining", "cooking", "kitchen", "lunch", "breakfast", "meal")),
    DRIVING(setOf("drive", "driving", "car", "road", "commute")),
    FOCUS(setOf("focus", "work", "study", "studying", "coding", "reading")),
    PARTY(setOf("party", "dance", "dancing", "friday")),
    SLEEP(setOf("sleep", "bedtime", "night")),
    OTHER(emptySet());

    companion object {
        fun of(name: String): ActivityKind {
            val nameWords = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).toSet()
            return entries.firstOrNull { kind -> kind.words.any { it in nameWords } } ?: OTHER
        }
    }
}
