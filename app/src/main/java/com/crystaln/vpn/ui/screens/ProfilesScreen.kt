package com.crystaln.vpn.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileShareLink
import com.crystaln.vpn.data.TrafficStats
import com.crystaln.vpn.ui.LiquidBlobs
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.glass.CircleGlassButton
import com.crystaln.vpn.ui.glass.CountBadge
import com.crystaln.vpn.ui.glass.Glass
import com.crystaln.vpn.ui.glass.GlassTab
import com.crystaln.vpn.ui.glass.GlassTabBar
import com.crystaln.vpn.ui.glass.PillButton
import com.crystaln.vpn.ui.glass.liquidGlass
import com.crystaln.vpn.ui.ServerAlias
import com.crystaln.vpn.ui.rememberWallpaper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.QRCodeWriter
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Special filter values for the group chips row. */
private const val FILTER_ALL = "__all__"
private const val FILTER_FAVORITES = "__favorites__"
private const val FILTER_UNGROUPED = "__ungrouped__"

/** Aggregated result of a full "check every profile" run. */
data class CheckSummary(
    val reachable: Int,
    val total: Int,
    val bestMs: Long?,
    val bestName: String?,
)

/**
 * The "Profiles" tab: search, group filters, the full availability check
 * and the profile cards grouped by subscription or flat when filtered.
 */
@Composable
fun ProfilesScreen(
    isDark: Boolean,
    profiles: List<Profile>,
    selectedId: String?,
    /** Privacy mask: show "🇩🇪 #N" instead of the profile name. */
    anonymizeNames: Boolean = false,
    wallpaperPath: String? = null,
    onSelect: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    /** Toggle the "favorites" pin (pinned to the top of the list). */
    onToggleFavorite: (Profile) -> Unit = {},
    onDelete: (Profile) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    /** Measured TCP ping per profile id (ms), -1 = unreachable. */
    pingResults: Map<String, Long> = emptyMap(),
    onPing: (Profile) -> Unit = {},
    pingAllRunning: Boolean = false,
    /** Full-check progress: done, total (0 = idle). */
    pingAllProgress: Pair<Int, Int> = 0 to 0,
    /** Aggregated summary of the last full check. */
    pingAllSummary: CheckSummary? = null,
    onPingAll: () -> Unit = {},
    /** Accumulated per-profile usage, id → totals. */
    trafficStats: Map<String, TrafficStats> = emptyMap(),
    /** Profile search filter. */
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    /** Subscription id → display name, for grouping. */
    subscriptionTitles: Map<String, String> = emptyMap(),
    /** Canonical user-defined group names (GroupStore). */
    groups: List<String> = emptyList(),
    onCreateGroup: (String) -> Unit = {},
    onDeleteGroup: (String) -> Unit = {},
    /** Open the share dialog (link + QR) for a profile. */
    onShare: (Profile) -> Unit = {},
    shareProfile: Profile? = null,
    onDismissShare: () -> Unit = {},
) {
    val wallpaper = rememberWallpaper(wallpaperPath)
    val overWallpaper = wallpaper != null
    val glassDark = wallpaper?.isDark ?: isDark
    var filter by remember { mutableStateOf(FILTER_ALL) }
    var groupsDialog by remember { mutableStateOf(false) }

    // Favorites first; a stable sort keeps the original order within each
    // group (plain alphabetical/reverse not needed — pinned just float up).
    val ordered = remember(profiles) { profiles.sortedByDescending { it.favorite } }

    // Privacy mask labels: flag emoji + 1-based index per profile id.
    val aliasLabels = remember(profiles, anonymizeNames) {
        if (anonymizeNames) {
            ordered.mapIndexed { i, p -> p.id to ServerAlias.label(p, i) }.toMap()
        } else {
            emptyMap()
        }
    }

    fun matchesFilter(p: Profile): Boolean = when (filter) {
        FILTER_ALL -> true
        FILTER_FAVORITES -> p.favorite
        FILTER_UNGROUPED -> p.group.isNullOrEmpty()
        else -> p.group.equals(filter, ignoreCase = true)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val hazeState = rememberHazeState()

        Box(Modifier.matchParentSize().hazeSource(hazeState)) {
            if (wallpaper != null) {
                WallpaperLayer(wallpaper.bitmap, wallpaper.isDark, themeDark = isDark)
            } else {
                LiquidBlobs(isDark)
            }

            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 170.dp),
                ) {
                    // Header: glass back button + big title.
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircleGlassButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                isDark = glassDark,
                                overWallpaper = overWallpaper,
                                onClick = onBack,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.tab_profiles),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }

                    item {
                        ProfilesHeader(
                            count = profiles.size,
                            isDark = glassDark,
                            overWallpaper = overWallpaper,
                            onImport = onImport,
                            onAdd = onAdd,
                            onManageGroups = { groupsDialog = true },
                            pingAllRunning = pingAllRunning,
                            onPingAll = onPingAll,
                        )
                    }

                    // Full availability check: live progress or last summary.
                    if (pingAllRunning && pingAllProgress.second > 0) {
                        item {
                            Text(
                                stringResource(
                                    R.string.check_running,
                                    pingAllProgress.first,
                                    pingAllProgress.second,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else if (pingAllSummary != null) {
                        item {
                            CheckSummaryRow(summary = pingAllSummary)
                        }
                    }

                    if (profiles.isNotEmpty()) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchChange,
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // Group filter chips: All / Favorites / each group /
                        // ungrouped (only when groups exist).
                        if (groups.isNotEmpty() || ordered.any { !it.group.isNullOrEmpty() }) {
                            item { FilterChipsRow(groups, ordered, filter, onPick = { filter = it }) }
                        }
                    }

                    if (ordered.isEmpty()) {
                        item {
                            EmptyProfiles(
                                isDark = glassDark,
                                overWallpaper = overWallpaper,
                                onImport = onImport,
                                onAdd = onAdd,
                            )
                        }
                    } else {
                        val visible = ordered.filter { matchesFilter(it) }
                        val query = searchQuery.trim()
                        val matched = { p: Profile ->
                            query.isEmpty() ||
                                p.name.contains(query, ignoreCase = true) ||
                                p.address.contains(query, ignoreCase = true) ||
                                p.protocol.displayName.contains(query, ignoreCase = true)
                        }

                        if (visible.none(matched)) {
                            item {
                                Text(
                                    stringResource(R.string.search_no_results),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        } else if (filter == FILTER_ALL && query.isEmpty() && subscriptionTitles.isNotEmpty()) {
                            // Grouped view: subscription groups first, then manual profiles.
                            val subGroups = subscriptionTitles.entries
                                .map { entry -> entry.key to visible.filter { it.subscriptionId == entry.key } }
                                .filter { it.second.isNotEmpty() }
                            val manual = visible.filter { it.subscriptionId == null }
                            subGroups.forEach { (title, list) ->
                                item(key = "group-$title") { SectionHeader(title) }
                                items(list, key = { it.id }) { p ->
                                    ProfileCard(
                                        profile = p,
                                        selected = p.id == selectedId,
                                        isDark = glassDark,
                                        overWallpaper = overWallpaper,
                                        ping = pingResults[p.id],
                                        pingable = isPingable(p),
                                        onPing = { onPing(p) },
                                        onSelect = { onSelect(p) },
                                        onEdit = { onEdit(p) },
                                        onDelete = { onDelete(p) },
                                        onShare = { onShare(p) },
                                        favorite = p.favorite,
                                        onToggleFavorite = { onToggleFavorite(p) },
                                        totalTraffic = trafficStats[p.id],
                                        aliasLabel = aliasLabels[p.id],
                                    )
                                }
                            }
                            if (manual.isNotEmpty()) {
                                item(key = "group-manual") {
                                    SectionHeader(stringResource(R.string.group_manual))
                                }
                                items(manual, key = { it.id }) { p ->
                                    ProfileCard(
                                        profile = p,
                                        selected = p.id == selectedId,
                                        isDark = glassDark,
                                        overWallpaper = overWallpaper,
                                        ping = pingResults[p.id],
                                        pingable = isPingable(p),
                                        onPing = { onPing(p) },
                                        onSelect = { onSelect(p) },
                                        onEdit = { onEdit(p) },
                                        onDelete = { onDelete(p) },
                                        onShare = { onShare(p) },
                                        favorite = p.favorite,
                                        onToggleFavorite = { onToggleFavorite(p) },
                                        totalTraffic = trafficStats[p.id],
                                        aliasLabel = aliasLabels[p.id],
                                    )
                                }
                            }
                        } else {
                            items(visible.filter(matched), key = { it.id }) { p ->
                                ProfileCard(
                                    profile = p,
                                    selected = p.id == selectedId,
                                    isDark = glassDark,
                                    overWallpaper = overWallpaper,
                                    ping = pingResults[p.id],
                                    pingable = isPingable(p),
                                    onPing = { onPing(p) },
                                    onSelect = { onSelect(p) },
                                    onEdit = { onEdit(p) },
                                    onDelete = { onDelete(p) },
                                    onShare = { onShare(p) },
                                    favorite = p.favorite,
                                    onToggleFavorite = { onToggleFavorite(p) },
                                    totalTraffic = trafficStats[p.id],
                                    aliasLabel = aliasLabels[p.id],
                                )
                            }
                        }
                    }
                }
            }
        }

        GlassTabBar(
            hazeState = hazeState,
            isDark = glassDark,
            overWallpaper = overWallpaper,
            tabs = listOf(
                GlassTab(Icons.Default.PowerSettingsNew, stringResource(R.string.tab_connect)),
                GlassTab(Icons.Default.Lan, stringResource(R.string.tab_profiles)),
                GlassTab(Icons.Default.Settings, stringResource(R.string.tab_settings)),
            ),
            selected = 1,
            onSelect = { index -> when (index) {
                0 -> onBack()
                2 -> onSettings()
            } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )

        if (groupsDialog) {
            GroupsDialog(
                groups = groups,
                profiles = profiles,
                isDark = isDark,
                onCreate = onCreateGroup,
                onDelete = onDeleteGroup,
                onDismiss = { groupsDialog = false },
            )
        }

        shareProfile?.let { profile ->
            ShareProfileDialog(profile = profile, onDismiss = onDismissShare)
        }
    }
}

@Composable
private fun CheckSummaryRow(summary: CheckSummary) {
    Column(Modifier.padding(top = 2.dp)) {
        Text(
            if (summary.reachable == 0) {
                stringResource(R.string.check_all_none_reachable, summary.total)
            } else {
                stringResource(R.string.check_summary, summary.reachable, summary.total)
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (summary.reachable == 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        summary.bestName?.let { name ->
            Text(
                stringResource(R.string.check_summary_best, name, summary.bestMs ?: 0L),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Horizontally scrollable chip row: All / Favorites / every group /
 * Ungrouped. Shows a per-chip profile count.
 */
@Composable
private fun FilterChipsRow(
    groups: List<String>,
    profiles: List<Profile>,
    selected: String,
    onPick: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            label = stringResource(R.string.group_filter_all),
            count = profiles.size,
            tint = MaterialTheme.colorScheme.primary,
            sel = selected == FILTER_ALL,
            onClick = { onPick(FILTER_ALL) },
        )
        FilterChip(
            label = stringResource(R.string.group_filter_favorites),
            count = profiles.count { it.favorite },
            tint = Color(0xFFFFC94A),
            sel = selected == FILTER_FAVORITES,
            onClick = { onPick(FILTER_FAVORITES) },
        )
        groups.forEach { g ->
            FilterChip(
                label = g,
                count = profiles.count { it.group.equals(g, ignoreCase = true) },
                tint = MaterialTheme.colorScheme.tertiary,
                sel = selected.equals(g, ignoreCase = true),
                onClick = { onPick(g) },
            )
        }
        FilterChip(
            label = stringResource(R.string.group_filter_ungrouped),
            count = profiles.count { it.group.isNullOrEmpty() },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            sel = selected == FILTER_UNGROUPED,
            onClick = { onPick(FILTER_UNGROUPED) },
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int,
    tint: Color,
    sel: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (sel) tint.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
            color = if (sel) tint else MaterialTheme.colorScheme.onSurface,
        )
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (sel) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Manage groups: rename-free CRUD-lite — create a group, delete one
 * (profiles are unassigned, not removed). Shows member counts.
 */
@Composable
private fun GroupsDialog(
    groups: List<String>,
    profiles: List<Profile>,
    isDark: Boolean,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(
                                    R.string.group_member_count,
                                    profiles.count { it.group.equals(g, ignoreCase = true) },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDelete(g) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete group",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                if (groups.isEmpty()) {
                    Text(
                        stringResource(R.string.groups_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.groups_hint)) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreate(newName)
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.groups_create))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Small grey caption above a profile group. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/**
 * Share dialog: QR code (for scanning into another device) plus the raw
 * share text with copy / system-share actions. Profiles without shareable
 * content (e.g. a WG config that was never filled) say so instead.
 */
@Composable
private fun ShareProfileDialog(
    profile: Profile,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val text = remember(profile.id) { ProfileShareLink.shareText(profile) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column {
                if (text == null) {
                    Text(
                        stringResource(R.string.share_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (text.length <= QR_MAX_TEXT) {
                        val qr = remember(text) { qrBitmap(text) }
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "QR code",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (text != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, R.string.share_copied, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_copy))
                    }
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }) {
                        Text(stringResource(R.string.action_share))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Beyond this, QR density stops being scannable; fall back to text-only. */
private const val QR_MAX_TEXT = 2000

/** Black-on-white QR of [content], quiet zone 1 module. */
private fun qrBitmap(content: String): Bitmap {
    val size = 780
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { i ->
        if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** Section header with the count badge and the add / import / groups buttons. */
@Composable
private fun ProfilesHeader(
    count: Int,
    isDark: Boolean,
    overWallpaper: Boolean,
    onImport: () -> Unit,
    onAdd: () -> Unit,
    onManageGroups: () -> Unit,
    pingAllRunning: Boolean = false,
    onPingAll: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.profiles_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        CountBadge(count)
        Spacer(Modifier.weight(1f))
        CircleGlassButton(
            icon = Icons.Default.Group,
            contentDescription = "Manage groups",
            isDark = isDark,
            overWallpaper = overWallpaper,
            onClick = onManageGroups,
            size = 40.dp,
        )
        Spacer(Modifier.width(8.dp))
        CircleGlassButton(
            icon = Icons.Default.NetworkCheck,
            contentDescription = "Ping all servers",
            isDark = isDark,
            overWallpaper = overWallpaper,
            onClick = onPingAll,
            size = 40.dp,
            enabled = count > 0 && !pingAllRunning,
        )
        Spacer(Modifier.width(8.dp))
        CircleGlassButton(
            icon = Icons.Default.FileOpen,
            contentDescription = "Import from file",
            isDark = isDark,
            overWallpaper = overWallpaper,
            onClick = onImport,
            size = 40.dp,
        )
        Spacer(Modifier.width(8.dp))
        CircleGlassButton(
            icon = Icons.Default.Add,
            contentDescription = "Add profile",
            isDark = isDark,
            overWallpaper = overWallpaper,
            accent = true,
            onClick = onAdd,
            size = 40.dp,
        )
    }
}

/**
 * One profile row: the top line (protocol icon, name/address, ping chip,
 * selection check) and a bottom actions line (favorite, share, edit,
 * delete), so the text never gets squeezed by the icon buttons.
 */
@Composable
private fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    isDark: Boolean,
    overWallpaper: Boolean,
    ping: Long?,
    pingable: Boolean,
    onPing: () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {},
    /** "Favorites" pin state + toggle (star icon button). */
    favorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    totalTraffic: TrafficStats? = null,
    /** Privacy mask: "🇩🇪 #4" instead of the real name (null = off). */
    aliasLabel: String? = null,
) {
    val pc = protocolColor(profile.protocol)
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Glass.tint(isDark, overWallpaper),
                isDark,
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSelect),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, bottom = 6.dp, end = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(pc.copy(alpha = 0.30f), pc.copy(alpha = 0.12f)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Lan,
                        contentDescription = null,
                        tint = pc,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Full-width text column: the action icons live on their
                // own row below, so ping and server info are never starved.
                Column(Modifier.weight(1f)) {
                    Text(
                        aliasLabel ?: profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.protocol.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = pc,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            profile.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    totalTraffic?.takeIf { it.rx + it.tx > 0 }?.let { t ->
                        Text(
                            stringResource(
                                R.string.profile_traffic_total,
                                formatBytes(t.rx + t.tx),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (pingable) {
                    PingChip(ping = ping, tint = pc, onPing = onPing)
                    Spacer(Modifier.width(6.dp))
                }
                if (selected) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            // Actions row: star (favorite), share, edit, delete. Compact
            // 30dp buttons, right-aligned — never squeeze the text column.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        modifier = Modifier.size(15.dp),
                        tint = if (favorite) Color(0xFFFFC94A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Small chip showing the measured server ping for a profile: a "measure"
 * icon before the first attempt, "—" in error color when unreachable,
 * "N мс" otherwise. Tap (re)measures.
 */
@Composable
private fun PingChip(
    ping: Long?,
    tint: Color,
    onPing: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.16f))
            .clickable(onClick = onPing)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            ping == null -> Icon(
                Icons.Default.NetworkCheck,
                contentDescription = "Ping",
                tint = tint,
                modifier = Modifier.size(15.dp),
            )

            ping < 0 -> Text(
                "—",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )

            else -> Text(
                stringResource(R.string.ping_ms, ping),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

/** TCP-connect ping is only meaningful for Xray-family TCP transports. */
private fun isPingable(p: Profile): Boolean {
    if (p.isWireGuardFamily || p.isOpenVpn) return false
    if (p.address.isEmpty() || p.port <= 0) return false
    return (p.fields["network"] ?: "tcp") !in setOf("quic", "kcp", "udp")
}

@Composable
private fun EmptyProfiles(
    isDark: Boolean,
    overWallpaper: Boolean,
    onImport: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(22.dp), Glass.tint(isDark, overWallpaper), isDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Lan,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.empty_profiles),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.empty_profiles_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = stringResource(R.string.action_import),
                icon = Icons.Default.FileOpen,
                accent = false,
                isDark = isDark,
                overWallpaper = overWallpaper,
                onClick = onImport,
            )
            PillButton(
                text = stringResource(R.string.action_create),
                icon = Icons.Default.Add,
                accent = true,
                isDark = isDark,
                onClick = onAdd,
            )
        }
    }
}

/** Localized byte size for the per-profile totals row. */
@Composable
private fun formatBytes(v: Long): String = when {
    v >= 1L shl 40 -> stringResource(R.string.size_tb_fmt, v / 1099511627776.0)
    v >= 1L shl 30 -> stringResource(R.string.size_gb_fmt, v / 1073741824.0)
    v >= 1L shl 20 -> stringResource(R.string.size_mb_fmt, v / 1048576.0)
    else -> stringResource(R.string.size_kb_fmt, v / 1024.0)
}
