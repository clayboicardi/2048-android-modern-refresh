package com.clayboicardi.game2048.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.clayboicardi.game2048.NAVIGATE_UP_TAG
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.ui.theme.GameDimens

/**
 * The frame of every sub-screen (ARCHITECTURE.md §7.2; DESIGN_SYSTEM.md §6.8–§6.12; FEATURES.md F21
 * item 3; D-023): a `Scaffold` whose `TopAppBar` shows [title] and a back arrow (`ic_arrow_back`,
 * auto-mirrored; spoken `navigate_up`, tagged `NAVIGATE_UP_TAG`) that calls [onBack], above
 * [content] in a column at most `ContentMaxWidth` wide, centred. The caller tags the root
 * ([modifier]) with `screenTag(key)`.
 */
@Composable
fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    GameIconButton(
                        onClick = onBack,
                        icon = R.drawable.ic_arrow_back,
                        contentDescription = stringResource(R.string.navigate_up),
                        style = GameIconButtonStyle.PLAIN,
                        modifier = Modifier.testTag(NAVIGATE_UP_TAG),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = GameDimens.ContentMaxWidth).fillMaxWidth(),
                content = content,
            )
        }
    }
}
