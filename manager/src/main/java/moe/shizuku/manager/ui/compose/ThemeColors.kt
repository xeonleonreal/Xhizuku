package moe.shizuku.manager.ui.compose

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import moe.shizuku.manager.R
import moe.shizuku.manager.app.ThemeHelper

/**
 * Hand-built Material3 schemes so the app can be tinted without dynamic color.
 * Red is the default (light red style).
 */
enum class ThemeColorOption(
    val key: String,
    @param:StringRes val labelRes: Int,
    val seed: Color
) {
    SYSTEM(ThemeHelper.THEME_COLOR_SYSTEM, R.string.theme_color_system, Color.Unspecified),
    RED(ThemeHelper.THEME_COLOR_RED, R.string.theme_color_red, Color(0xFFF44336)),
    BLUE("blue", R.string.theme_color_blue, Color(0xFF2196F3)),
    GREEN("green", R.string.theme_color_green, Color(0xFF4CAF50)),
    PURPLE("purple", R.string.theme_color_purple, Color(0xFF9C27B0)),
    ORANGE("orange", R.string.theme_color_orange, Color(0xFFFF9800));

    companion object {
        fun fromKey(key: String?): ThemeColorOption {
            return entries.firstOrNull { it.key == key } ?: RED
        }
    }

    fun lightScheme(): ColorScheme {
    val base = lightColorScheme()
    return when (this) {
        SYSTEM -> base
        RED -> base.copy(
            primary = Color(0xFFC62828),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDAD4),
            onPrimaryContainer = Color(0xFF410001),
            secondary = Color(0xFF775651),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDAD4),
            onSecondaryContainer = Color(0xFF2C1512),
            tertiary = Color(0xFF705C2E),
            tertiaryContainer = Color(0xFFF9DDAE),
            surface = Color(0xFFFFF8F6),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFF1EE),
            surfaceContainer = Color(0xFFFCE8E5),
            surfaceContainerHigh = Color(0xFFF8DCD7),
            surfaceContainerHighest = Color(0xFFF2CFC9),
            surfaceTint = Color(0xFFC62828),
            inversePrimary = Color(0xFFFFB4A8)
        )
        BLUE -> base.copy(
            primary = Color(0xFF0B57D0),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD3E3FD),
            onPrimaryContainer = Color(0xFF001D35),
            secondary = Color(0xFF575F71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFDCE2F9),
            onSecondaryContainer = Color(0xFF131C2B),
            tertiary = Color(0xFF715573),
            tertiaryContainer = Color(0xFFFBD7FC),
            surface = Color(0xFFF8FAFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF1F4F9),
            surfaceContainer = Color(0xFFECEFF4),
            surfaceContainerHigh = Color(0xFFE6EAF0),
            surfaceContainerHighest = Color(0xFFE0E4EC),
            surfaceTint = Color(0xFF0B57D0),
            inversePrimary = Color(0xFFA8C7FA)
        )
        GREEN -> base.copy(
            primary = Color(0xFF2E7D32),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFBEF0C0),
            onPrimaryContainer = Color(0xFF002106),
            secondary = Color(0xFF55624C),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD9E7CA),
            onSecondaryContainer = Color(0xFF131F0D),
            tertiary = Color(0xFF386663),
            tertiaryContainer = Color(0xFFBCECE7),
            surface = Color(0xFFF7FAF2),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF0F4EA),
            surfaceContainer = Color(0xFFEBEFE5),
            surfaceContainerHigh = Color(0xFFE5E9DF),
            surfaceContainerHighest = Color(0xFFDFE4D9),
            surfaceTint = Color(0xFF2E7D32),
            inversePrimary = Color(0xFF92DA94)
        )
        PURPLE -> base.copy(
            primary = Color(0xFF7B1FA2),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF280096),
            secondary = Color(0xFF65586B),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1E192B),
            tertiary = Color(0xFF7E525D),
            tertiaryContainer = Color(0xFFFFD9E0),
            surface = Color(0xFFFBF7FF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF4EEFA),
            surfaceContainer = Color(0xFFEFE9F5),
            surfaceContainerHigh = Color(0xFFE9E2F0),
            surfaceContainerHighest = Color(0xFFE3DCEB),
            surfaceTint = Color(0xFF7B1FA2),
            inversePrimary = Color(0xFFD0BCFF)
        )
        ORANGE -> base.copy(
            primary = Color(0xFF9A4600),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDBCB),
            onPrimaryContainer = Color(0xFF341100),
            secondary = Color(0xFF6F5B40),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF5D9B8),
            onSecondaryContainer = Color(0xFF2A1700),
            tertiary = Color(0xFF576032),
            tertiaryContainer = Color(0xFFD8E7CB),
            surface = Color(0xFFFFF8F4),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFF1E8),
            surfaceContainer = Color(0xFFF9ECE2),
            surfaceContainerHigh = Color(0xFFF3E5D8),
            surfaceContainerHighest = Color(0xFFEDDCD0),
            surfaceTint = Color(0xFF9A4600),
            inversePrimary = Color(0xFFFFB77E)
        )
    }
}

    fun darkScheme(): ColorScheme {
    val base = darkColorScheme()
    return when (this) {
        SYSTEM -> base
        RED -> base.copy(
            primary = Color(0xFFFFB4A8),
            onPrimary = Color(0xFF690005),
            primaryContainer = Color(0xFF93000A),
            onPrimaryContainer = Color(0xFFFFDAD4),
            secondary = Color(0xFFE7BDB8),
            tertiary = Color(0xFFEBC56D),
            surfaceTint = Color(0xFFFFB4A8),
            inversePrimary = Color(0xFFC62828)
        )
        BLUE -> base.copy(
            primary = Color(0xFFA8C7FA),
            onPrimary = Color(0xFF0A305F),
            primaryContainer = Color(0xFF004A77),
            onPrimaryContainer = Color(0xFFD3E3FD),
            secondary = Color(0xFFBEC6DC),
            tertiary = Color(0xFFD6BDE2),
            surfaceTint = Color(0xFFA8C7FA),
            inversePrimary = Color(0xFF0B57D0)
        )
        GREEN -> base.copy(
            primary = Color(0xFF92DA94),
            onPrimary = Color(0xFF093B12),
            primaryContainer = Color(0xFF246B1F),
            onPrimaryContainer = Color(0xFFBEF0C0),
            secondary = Color(0xFFBDCBA9),
            tertiary = Color(0xFFA0D0C8),
            surfaceTint = Color(0xFF92DA94),
            inversePrimary = Color(0xFF2E7D32)
        )
        PURPLE -> base.copy(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            tertiary = Color(0xFFEFB8C8),
            surfaceTint = Color(0xFFD0BCFF),
            inversePrimary = Color(0xFF7B1FA2)
        )
        ORANGE -> base.copy(
            primary = Color(0xFFFFB77E),
            onPrimary = Color(0xFF4E2200),
            primaryContainer = Color(0xFF7A2E00),
            onPrimaryContainer = Color(0xFFFFDBCB),
            secondary = Color(0xFFE4C07C),
            tertiary = Color(0xFFBCD094),
            surfaceTint = Color(0xFFFFB77E),
            inversePrimary = Color(0xFF9A4600)
        )
    }
}

}

@Composable
fun appColorScheme(dark: Boolean): ColorScheme {
    val option = ThemeColorOption.fromKey(ThemeHelper.getThemeColor())
    return if (dark) option.darkScheme() else option.lightScheme()
}
