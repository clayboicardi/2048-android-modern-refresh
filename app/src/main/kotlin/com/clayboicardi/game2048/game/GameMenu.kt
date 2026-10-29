package com.clayboicardi.game2048.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.clayboicardi.game2048.AboutKey
import com.clayboicardi.game2048.HowToPlayKey
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.SettingsKey
import com.clayboicardi.game2048.StatsKey
import com.clayboicardi.game2048.menuItemTag

/**
 * The game menu that the More button opens (DESIGN_SYSTEM.md §6.1; FEATURES.md F21 item 2; D-023):
 * a `DropdownMenu` with the classic `DropdownMenuItem(text, onClick, leadingIcon)` items Statistics
 * (`ic_bar_chart`), Settings (`ic_settings`, the migrated legacy key, D-101), How to play
 * (`ic_help`) and About (`ic_info`), each tagged `menuItemTag(key)`. There is no Share item
 * (F10-AC7). [menuOpen] is the screen's saveable menu state (ARCHITECTURE.md §7.2); the menu's own
 * dismissal (Back, a tap outside) closes it through [onMenuOpenChange], and an item closes it and
 * then pushes its destination with [onNavigate].
 */
@Composable
fun GameMoreMenu(
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { onMenuOpenChange(false) },
        modifier = modifier,
    ) {
        for (item in MenuItems) {
            DropdownMenuItem(
                text = { Text(stringResource(item.label)) },
                onClick = {
                    onMenuOpenChange(false)
                    onNavigate(item.key)
                },
                modifier = Modifier.testTag(menuItemTag(item.key)),
                leadingIcon = { Icon(painterResource(item.icon), contentDescription = null) },
            )
        }
    }
}

/** One game-menu item: its destination, label and leading icon (DESIGN_SYSTEM.md §6.1, §8.1). */
private class MenuItem(val key: NavKey, @StringRes val label: Int, @DrawableRes val icon: Int)

/** The menu's items in DESIGN_SYSTEM.md §6.1 order. */
private val MenuItems =
    listOf(
        MenuItem(StatsKey, R.string.menu_statistics, R.drawable.ic_bar_chart),
        MenuItem(SettingsKey, R.string.settings, R.drawable.ic_settings),
        MenuItem(HowToPlayKey, R.string.menu_how_to_play, R.drawable.ic_help),
        MenuItem(AboutKey, R.string.menu_about, R.drawable.ic_info),
    )
