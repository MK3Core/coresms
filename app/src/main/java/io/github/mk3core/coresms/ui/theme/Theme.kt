package io.github.mk3core.coresms.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.mk3core.coresms.data.Prefs

/** The look picked in Settings. */
enum class ThemeStyle(val key: String) {
    Retro("retro"),
    Modern("modern"),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: Retro
    }
}

/** The chosen style, shared by every open screen so a change applies everywhere at once. */
object ThemeChoice {
    private val state = mutableStateOf<ThemeStyle?>(null)

    @Composable
    fun current(): ThemeStyle {
        val context = LocalContext.current
        return state.value ?: remember { ThemeStyle.of(Prefs.themeStyle(context)) }
    }

    fun set(context: Context, style: ThemeStyle) {
        Prefs.setThemeStyle(context, style.key)
        state.value = style
    }
}

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
    /** A thin outline around bubbles, or transparent for none. */
    val bubbleOutline: Color = Color.Transparent,
)

/** Shapes and small touches that differ between the styles. */
@Immutable
data class ChatStyle(
    val retro: Boolean,
    val bubbleRadius: Dp,
    val bubbleTightRadius: Dp,
    val mediaRadius: Dp,
    val fieldShape: Shape,
    val roundShape: Shape,
    val sheetRadius: Dp,
)

private val ModernStyle = ChatStyle(
    retro = false,
    bubbleRadius = 20.dp,
    bubbleTightRadius = 6.dp,
    mediaRadius = 18.dp,
    fieldShape = RoundedCornerShape(22.dp),
    roundShape = CircleShape,
    sheetRadius = 24.dp,
)

private val RetroStyle = ChatStyle(
    retro = true,
    bubbleRadius = 6.dp,
    bubbleTightRadius = 2.dp,
    mediaRadius = 6.dp,
    fieldShape = RoundedCornerShape(6.dp),
    roundShape = RoundedCornerShape(4.dp),
    sheetRadius = 6.dp,
)

val LocalChatStyle = staticCompositionLocalOf { RetroStyle }

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

// Retro: plain white-on-black terminal text. Color only where it means something.
private val Ink = Color(0xFFEDEDED)
private val InkDim = Color(0xFF8C8C8C)
private val TerminalRed = Color(0xFFFF5F57)

private val RetroChat = ChatColors(
    outgoingBubble = Color(0xFF262626),
    onOutgoingBubble = Color.White,
    incomingBubble = Color.Black,
    onIncomingBubble = Ink,
    failed = TerminalRed,
    unreadDot = Color.White,
    subtle = InkDim,
    bubbleOutline = Color(0xFF3A3A3A),
)

val LocalChatColors = staticCompositionLocalOf { RetroChat }

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
    surfaceContainerLow = Color(0xFFF7F8FA),
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
    surfaceContainerLow = Color(0xFF101113),
    surfaceContainerHigh = Color(0xFF1F2023),
    outlineVariant = Color(0xFF2A2C30),
    error = Color(0xFFFF6B5E),
)

private val RetroScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Ink,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Ink,
    surface = Color.Black,
    onSurface = Ink,
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFFB5B5B5),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    outline = InkDim,
    outlineVariant = Color(0xFF2E2E2E),
    error = TerminalRed,
)

private val ModernTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
    )
}

/** Monospace everywhere, a touch smaller since every character is full width. */
private val RetroTypography = Typography().let { base ->
    fun TextStyle.mono(size: Int? = null, weight: FontWeight? = null, line: Int? = null) = copy(
        fontFamily = FontFamily.Monospace,
        fontSize = size?.sp ?: fontSize,
        fontWeight = weight ?: fontWeight,
        lineHeight = line?.sp ?: lineHeight,
        letterSpacing = 0.sp,
    )
    Typography(
        displayLarge = base.displayLarge.mono(),
        displayMedium = base.displayMedium.mono(),
        displaySmall = base.displaySmall.mono(),
        headlineLarge = base.headlineLarge.mono(),
        headlineMedium = base.headlineMedium.mono(size = 22, weight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.mono(),
        titleLarge = base.titleLarge.mono(size = 19, weight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.mono(size = 15, weight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.mono(),
        bodyLarge = base.bodyLarge.mono(size = 15, line = 21),
        bodyMedium = base.bodyMedium.mono(size = 14, line = 19),
        bodySmall = base.bodySmall.mono(),
        labelLarge = base.labelLarge.mono(),
        labelMedium = base.labelMedium.mono(size = 12),
        labelSmall = base.labelSmall.mono(size = 11),
    )
}

private val RetroShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(3.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** Text style for message bubbles: slightly larger and airy, for easy reading. */
val BubbleText: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = if (LocalChatStyle.current.retro) {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 21.sp)
    } else {
        TextStyle(fontSize = 16.5.sp, lineHeight = 22.sp)
    }

@Composable
fun CoreSmsTheme(content: @Composable () -> Unit) {
    val style = ThemeChoice.current()
    val retro = style == ThemeStyle.Retro
    // Retro is a terminal: always dark.
    val dark = retro || isSystemInDarkTheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalChatColors provides when {
            retro -> RetroChat
            dark -> DarkChat
            else -> LightChat
        },
        LocalChatStyle provides if (retro) RetroStyle else ModernStyle,
    ) {
        MaterialTheme(
            colorScheme = when {
                retro -> RetroScheme
                dark -> DarkScheme
                else -> LightScheme
            },
            typography = if (retro) RetroTypography else ModernTypography,
            shapes = if (retro) RetroShapes else Shapes(),
            content = content,
        )
    }
}
