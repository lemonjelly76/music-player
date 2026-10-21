package io.github.lemonjelly76.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** A person's first letter on a colour of their own, standing in for a profile picture. */
@Composable
fun Initial(name: String, size: Dp) {
    val hue = (name.hashCode() and 0x7fffffff) % 360f
    Box(Modifier.size(size).background(Color.hsl(hue, 0.55f, 0.6f), CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp)
    }
}
