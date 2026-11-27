package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.data.UserProfile
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.AvatarPaletteHex
import com.example.ui.theme.LocalWatchTogetherExtras
import com.example.ui.theme.parseHexColor

@Composable
fun SettingsScreen(
    profile: UserProfile,
    userEmail: String?,
    isAdmin: Boolean,
    onSaveProfile: (
        displayName: String,
        avatarColorHex: String,
        darkThemeEnabled: Boolean,
        autoSyncEnabled: Boolean,
        hapticsEnabled: Boolean,
        liveKitUrl: String
    ) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by rememberSaveable(profile.displayName) { mutableStateOf(profile.displayName) }
    var selectedColor by rememberSaveable(profile.avatarColorHex) { mutableStateOf(profile.avatarColorHex) }
    var darkThemeEnabled by rememberSaveable(profile.darkThemeEnabled) { mutableStateOf(profile.darkThemeEnabled) }
    var autoSyncEnabled by rememberSaveable(profile.autoSyncEnabled) { mutableStateOf(profile.autoSyncEnabled) }
    var hapticsEnabled by rememberSaveable(profile.hapticsEnabled) { mutableStateOf(profile.hapticsEnabled) }
    var liveKitUrl by rememberSaveable(profile.liveKitUrl) { mutableStateOf(profile.liveKitUrl) }

    val extras = LocalWatchTogetherExtras.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Profile Identity Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatarBadge(
                                displayName = displayName.ifBlank { "W" },
                                colorHex = selectedColor,
                                size = 56.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName.ifBlank { "Cinema Fan" },
                                    style = MaterialTheme.typography.titleLarge
                                )
                                if (!userEmail.isNullOrBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = extras.hostGold.copy(alpha = 0.18f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AdminPanelSettings,
                                                contentDescription = null,
                                                tint = extras.hostGold,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Live TV Catalog Admin",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = extras.hostGold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(40) },
                            label = { Text("Watch Party Display Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_display_name_input")
                        )

                        Text(
                            text = "Avatar Glow Color",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(AvatarPaletteHex) { hex ->
                                val isSelected = hex.equals(selectedColor, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(hex))
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected color",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Playback, Theme & Haptics Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsToggleRow(
                            icon = Icons.Filled.DarkMode,
                            title = "Cinematic Dark Theme",
                            subtitle = "Obsidian dark-first theme (off for light theme)",
                            checked = darkThemeEnabled,
                            onCheckedChange = { darkThemeEnabled = it },
                            tag = "settings_dark_theme_switch"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsToggleRow(
                            icon = Icons.Filled.Sync,
                            title = "Auto Drift Correction",
                            subtitle = "Smooth rate nudge (0.3s–2s) & seek (>2s) with 30s clock sync",
                            checked = autoSyncEnabled,
                            onCheckedChange = { autoSyncEnabled = it },
                            tag = "settings_autosync_switch"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SettingsToggleRow(
                            icon = Icons.Filled.Vibration,
                            title = "Subtle Haptic Feedback",
                            subtitle = "Tactile taps for reactions and party creation",
                            checked = hapticsEnabled,
                            onCheckedChange = { hapticsEnabled = it },
                            tag = "settings_haptics_switch"
                        )
                    }
                }
            }

            // Optional Self-Hosted LiveKit Server Config
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Voice Chat Server (Optional LiveKit)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Enter your self-hosted LiveKit WebRTC server URL if deploying full multi-user voice audio alongside room voice presence.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = liveKitUrl,
                            onValueChange = { liveKitUrl = it.take(300) },
                            label = { Text("wss://your-livekit-server.example.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // YouTube API Services, Terms of Service & Google Privacy Policy
            item {
                val context = LocalContext.current
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "YouTube API Services & Legal",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "WatchTogether uses YouTube API Services and the official YouTube embedded player. By using WatchTogether, you agree to be bound by the YouTube Terms of Service and acknowledge the Google Privacy Policy. WatchTogether never downloads, caches, or republishes YouTube videos, never blocks ads, and never charges for YouTube access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/t/terms"))
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("youtube_tos_link")
                            ) {
                                Text("YouTube ToS", maxLines = 1)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("google_privacy_link")
                            ) {
                                Text("Google Privacy", maxLines = 1)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Save & Sign Out Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            onSaveProfile(
                                displayName,
                                selectedColor,
                                darkThemeEnabled,
                                autoSyncEnabled,
                                hapticsEnabled,
                                liveKitUrl
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("settings_save_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Preferences")
                    }

                    FilledTonalButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
