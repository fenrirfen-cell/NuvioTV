@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.R

@Composable
internal fun ProfileSettingsContent(
    onManageProfiles: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.profile_title),
            subtitle = stringResource(R.string.profile_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            SettingsActionRow(
                title = stringResource(R.string.profile_manage_button),
                subtitle = null,
                onClick = onManageProfiles,
                modifier = if (initialFocusRequester != null) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                }
            )
        }
        SpanishOnlyProfilesCard()
    }
}

/** One "Solo español" switch per profile. Only the main profile can open this page. */
@Composable
private fun SpanishOnlyProfilesCard(
    viewModel: SpanishOnlySettingsViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val spanishOnlyIds by viewModel.spanishOnlyIds.collectAsState()

    SettingsDetailHeader(
        title = stringResource(R.string.profile_spanish_only_title),
        subtitle = stringResource(R.string.profile_spanish_only_subtitle)
    )
    SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
        profiles.forEach { profile ->
            SettingsToggleRow(
                title = profile.name,
                subtitle = null,
                checked = profile.id in spanishOnlyIds,
                onToggle = { viewModel.toggle(profile.id) }
            )
        }
    }
}
