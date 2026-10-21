package io.github.lemonjelly76.musicplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityKindTest {

    @Test
    fun recognisesActivitiesByTheWordsInTheirName() {
        assertEquals(ActivityKind.RUNNING, ActivityKind.of("Running"))
        assertEquals(ActivityKind.RUNNING, ActivityKind.of("Long runs"))
        assertEquals(ActivityKind.CHILL, ActivityKind.of("Chill"))
        assertEquals(ActivityKind.CHILL, ActivityKind.of("Rainy Sunday relaxing"))
        assertEquals(ActivityKind.DINNER, ActivityKind.of("Dinner"))
        assertEquals(ActivityKind.DINNER, ActivityKind.of("Cooking"))
        assertEquals(ActivityKind.DRIVING, ActivityKind.of("Driving"))
        assertEquals(ActivityKind.FOCUS, ActivityKind.of("Focus"))
    }

    @Test
    fun anythingElseIsPlainMusic() {
        assertEquals(ActivityKind.OTHER, ActivityKind.of("90s Britpop deep cuts"))
    }

    @Test
    fun doesNotMatchInsideOtherWords() {
        assertEquals(ActivityKind.OTHER, ActivityKind.of("Brunch"))
    }
}
