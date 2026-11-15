package io.github.mk3core.coresms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colors specific to messaging that Material's scheme doesn't cover. */
@Immutable
data class ChatColors(
    val outgoingBubble: Color,
    val onOutgoingBubble: Color,
    val incomingBubble: Color,
    val onIncomingBubble: Color,
    val failed: Color,
    val unreadDot: Color,
    val subtle: Color,
)

private val Accent = Color(0xFF0A74DA)
private val AccentDark = Color(0xFF3A93F5)

private val LightChat = ChatColors(
    outgoingBubble = Accent,
    onOutgoingBubble = Color.White,
    incomingBubble = Color(0xFFEDEFF2),
    onIncomingBubble = Color(0xFF0B0D10),
    failed = Color(0xFFD93025),
    unreadDot = Accent,
    subtle = Color(0xFF8A9099),
)

private val DarkChat = ChatColors(
    outgoingBubble = AccentDark,
    onOutgoingBubble = Color.White,
    incomingBubble = Color(0xFF26282C),
    onIncomingBubble = Color(0xFFF2F3F5),
    failed = Color(0xFFFF6B5E),
    unreadDot = AccentDark,
    subtle = Color(0xFF7D838C),
)

val LocalChatColors = staticCompositionLocalOf { LightChat }

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0B0D10),
    surface = Color.White,
    onSurface = Color(0xFF0B0D10),
    surfaceVariant = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFF5F6670),
    surfaceContainer = Color(0xFFF4F5F7),
    surfaceContainerHigh = Color(0xFFEDEFF2),
    outlineVariant = Color(0xFFE3E5E8),
    error = Color(0xFFD93025),
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFF2F3F5),
    surface = Color.Black,
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF1C1D20),
    onSurfaceVariant = Color(0xFF9AA0A8),
    surfaceContainer = Color(0xFF151618),
    surfaceContainerHigh = Color(0xFF1F2023),
    outlineVariant = Color(0xFF2A2C30),
    error = Color(0xFFFF6B5E),
)

private val CoreTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
    )
}

/** Text style for message bubbles: slightly larger and airy, for easy reading. */
val BubbleText = TextStyle(fontSize = 16.5.sp, lineHeight = 22.sp)

@Composable
fun CoreSmsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalChatColors provides if (dark) DarkChat else LightChat) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = CoreTypography,
            content = content,
        )
    }
}
