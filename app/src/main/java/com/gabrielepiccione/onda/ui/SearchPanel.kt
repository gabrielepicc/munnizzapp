package com.gabrielepiccione.onda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielepiccione.onda.SearchResults
import com.gabrielepiccione.onda.data.AppInfo
import com.gabrielepiccione.onda.data.ContactHit

@Composable
fun SearchPanel(
    results: SearchResults,
    suggestions: List<AppInfo>,
    showIcons: Boolean,
    themedIcons: Boolean,
    contactsPermission: Boolean,
    onQueryChange: (String) -> Unit,
    onApp: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onContact: (ContactHit) -> Unit,
    onWebSearch: (String) -> Unit,
    onRequestContacts: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalOndaColors.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .blockTouches()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.panelHigh)
                .height(56.dp)
                .padding(start = 18.dp, end = 6.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onWallpaperMuted)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (results.query.isEmpty()) {
                    Text("Cerca app, contatti, web", color = colors.onWallpaperMuted, style = MaterialTheme.typography.bodyLarge, fontSize = 18.sp)
                }
                BasicTextField(
                    value = results.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onWallpaper, fontSize = 18.sp),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val first = results.apps.firstOrNull()
                        when {
                            first != null -> onApp(first)
                            results.query.isNotBlank() -> onWebSearch(results.query)
                        }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Chiudi", tint = colors.onWallpaperMuted)
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (results.query.isBlank() && suggestions.isNotEmpty()) {
                item(key = "sugg_label") { SectionLabel("Usate spesso") }
                items(suggestions, key = { "s_" + it.key }) { app ->
                    AppListRow(
                        app = app,
                        hasNotification = false,
                        showIcon = showIcons,
                        themedIcons = themedIcons,
                        onClick = { onApp(app) },
                        onLongClick = { onAppLongClick(app) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            results.calc?.let { value ->
                item(key = "calc") {
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.accentContainer)
                            .clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(value)) }
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Text("= $value", color = colors.onAccentContainer, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                        Text("Tocca per copiare", color = colors.onAccentContainer.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
            items(results.apps, key = { it.key }) { app ->
                AppListRow(
                    app = app,
                    hasNotification = false,
                    showIcon = showIcons,
                    themedIcons = themedIcons,
                    highlighted = app == results.apps.firstOrNull(),
                    onClick = { onApp(app) },
                    onLongClick = { onAppLongClick(app) },
                )
            }
            if (results.query.isNotBlank()) {
                if (results.contacts.isNotEmpty()) {
                    item { SectionLabel("Contatti") }
                    items(results.contacts, key = { "c_${it.id}" }) { c ->
                        SimpleRow(icon = { Icon(Icons.Filled.Person, null, tint = colors.accent) }, label = c.name) { onContact(c) }
                    }
                } else if (!contactsPermission) {
                    item {
                        SimpleRow(icon = { Icon(Icons.Filled.Person, null, tint = colors.onWallpaperMuted) }, label = "Cerca anche nei contatti…", muted = true, onClick = onRequestContacts)
                    }
                }
                item { SectionLabel("Web") }
                item {
                    SimpleRow(icon = { Icon(Icons.Filled.Search, null, tint = colors.accent) }, label = "Cerca «${results.query}» sul web") {
                        onWebSearch(results.query)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = LocalOndaColors.current.onWallpaperMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SimpleRow(icon: @Composable () -> Unit, label: String, muted: Boolean = false, onClick: () -> Unit) {
    val colors = LocalOndaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (muted) colors.onWallpaperMuted else colors.onWallpaper, fontSize = 17.sp)
    }
}
