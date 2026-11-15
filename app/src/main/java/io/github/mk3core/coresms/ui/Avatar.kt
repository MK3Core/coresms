package io.github.mk3core.coresms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.mk3core.coresms.data.Contact
import io.github.mk3core.coresms.ui.theme.LocalChatStyle
import kotlin.math.abs

private val AvatarColors = listOf(
    Color(0xFF5B8DEF), Color(0xFF4FB59B), Color(0xFFE57E5C), Color(0xFFB57EDC),
    Color(0xFFE0A63E), Color(0xFF5FB2D8), Color(0xFFDE6E95), Color(0xFF7C8FA6),
)

// Retro: terminal colors, drawn as colored initials in a dim box.
private val RetroAvatarColors = listOf(
    Color(0xFF3DFF7A), Color(0xFFFFB443), Color(0xFF4FE3F0), Color(0xFFFF7AD9),
    Color(0xFFB8F05A), Color(0xFF9C8CFF), Color(0xFFFF8A5C), Color(0xFFE8E8E8),
)

/** A contact's photo, their initials on a soft color, or a person glyph for unsaved numbers. */
@Composable
fun Avatar(contact: Contact, size: Dp, modifier: Modifier = Modifier) {
    val style = LocalChatStyle.current
    val palette = if (style.retro) RetroAvatarColors else AvatarColors
    val color = palette[abs(contact.address.hashCode()) % palette.size]
    val shape = style.roundShape
    val box = if (style.retro) {
        modifier.size(size).clip(shape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.55f), shape)
    } else {
        modifier.size(size).clip(shape).background(color)
    }
    val ink = if (style.retro) color else Color.White
    Box(
        modifier = box,
        contentAlignment = Alignment.Center,
    ) {
        val initials = contact.name?.let(::initials)
        if (initials != null) {
            Text(
                initials,
                color = ink,
                fontWeight = if (style.retro) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = (size.value * if (style.retro) 0.34f else 0.38f).sp,
                fontFamily = if (style.retro) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(size * 0.6f),
            )
        }
        if (contact.photoUri != null) {
            AsyncImage(
                model = contact.photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** One avatar, or two overlapping ones for a group. */
@Composable
fun ConversationAvatar(people: List<Contact>, size: Dp, modifier: Modifier = Modifier) {
    if (people.size <= 1) {
        Avatar(people.firstOrNull() ?: Contact("", null, null), size, modifier)
        return
    }
    val small = size * 0.68f
    Box(modifier.size(size)) {
        Avatar(people[1], small, Modifier.align(Alignment.TopEnd))
        Avatar(
            people[0],
            small,
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-1).dp, y = 1.dp)
                .border(2.dp, MaterialTheme.colorScheme.background, LocalChatStyle.current.roundShape),
        )
    }
}

private fun initials(name: String): String? {
    val words = name.trim().split(Regex("\\s+")).filter { it.firstOrNull()?.isLetter() == true }
    if (words.isEmpty()) return null
    val first = words.first().first().uppercaseChar()
    val last = if (words.size > 1) words.last().first().uppercaseChar().toString() else ""
    return "$first$last"
}
