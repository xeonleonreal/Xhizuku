package moe.shizuku.manager.ui.compose

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.console.ConsoleActivity
import moe.shizuku.manager.extras.ExtrasActivity
import moe.shizuku.manager.settings.SettingsActivity

/**
 * Magisk-style bottom navigation: Status / Extras / Settings.
 * Each tab is an Activity; selecting a tab brings its Activity to front.
 */
enum class MainTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    STATUS(R.string.tab_status, Icons.Rounded.Shield),
    EXTRAS(R.string.tab_extras, Icons.Rounded.Apps),
    CONSOLE(R.string.tab_console, Icons.Rounded.Terminal),
    SETTINGS(R.string.tab_settings, Icons.Rounded.Settings)
}

@Composable
fun MainTabBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit
) {
    NavigationBar {
        for (tab in MainTab.entries) {
            NavigationBarItem(
                selected = selected == tab,
                onClick = { if (tab != selected) onSelect(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = {
                    Text(text = androidx.compose.ui.res.stringResource(tab.labelRes))
                }
            )
        }
    }
}

fun Activity.openMainTab(tab: MainTab) {
    val target = when (tab) {
        MainTab.STATUS -> MainActivity::class.java
        MainTab.EXTRAS -> ExtrasActivity::class.java
        MainTab.CONSOLE -> ConsoleActivity::class.java
        MainTab.SETTINGS -> SettingsActivity::class.java
    }
    if (this::class.java == target) return
    val intent = Intent(this, target)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    startActivity(intent)
    overridePendingTransition(0, 0)
}
