package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.runtime.Composable

/** The phone's back gesture; the desktop has none, so Settings closes with its own back arrow there. */
@Composable
expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)
