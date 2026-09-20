// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun OpenSourceLicensesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.open_source_licenses),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = RecorderIcons.Back,
                            contentDescription = stringResource(R.string.navigate_back),
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 28.dp,
            ),
        ) {
            items(
                items = OPEN_SOURCE_LIBRARIES,
                key = { library -> library.nameRes },
            ) { library ->
                val version = library.versionRes?.let { stringResource(it) }
                val license = stringResource(library.licenseRes)
                val summary = if (version == null) {
                    license
                } else {
                    stringResource(R.string.open_source_license_summary, version, license)
                }
                val website = stringResource(library.websiteRes)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 12.dp),
                ) {
                    ArrowPreference(
                        title = stringResource(library.nameRes),
                        summary = summary,
                        onClick = { onOpenExternalUrl(website) },
                    )
                }
            }
        }
    }
}

private data class OpenSourceLibrary(
    @StringRes val nameRes: Int,
    @StringRes val versionRes: Int?,
    @StringRes val licenseRes: Int,
    @StringRes val websiteRes: Int,
)

private val OPEN_SOURCE_LIBRARIES = listOf(
    OpenSourceLibrary(
        nameRes = R.string.library_activity_compose,
        versionRes = R.string.version_activity_compose,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_activity_compose,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_core_ktx,
        versionRes = R.string.version_core_ktx,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_core_ktx,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_androidx_annotation,
        versionRes = null,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_androidx_annotation,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_androidx_runtime_components,
        versionRes = null,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_androidx,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_navigation_event_compose,
        versionRes = R.string.version_navigation_event,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_navigation_event,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_compose_foundation,
        versionRes = R.string.version_compose,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_compose_multiplatform,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_compose_material3_window_size_class,
        versionRes = R.string.version_compose_material3_window_size_class,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_compose_multiplatform,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_compose_runtime,
        versionRes = R.string.version_compose,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_compose_multiplatform,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_compose_ui,
        versionRes = R.string.version_compose,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_compose_multiplatform,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_jspecify,
        versionRes = R.string.version_jspecify,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_jspecify,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_kotlin_stdlib,
        versionRes = R.string.version_kotlin,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_kotlin,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_kotlinx_coroutines,
        versionRes = null,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_kotlinx_coroutines,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_material_kolor,
        versionRes = R.string.version_material_kolor,
        licenseRes = R.string.license_mit,
        websiteRes = R.string.url_material_kolor,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_miuix_core,
        versionRes = R.string.version_miuix,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_miuix,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_miuix_preference,
        versionRes = R.string.version_miuix,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_miuix,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_miuix_shader,
        versionRes = R.string.version_miuix,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_miuix,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_miuix_squircle,
        versionRes = R.string.version_miuix,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_miuix,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_miuix_ui,
        versionRes = R.string.version_miuix,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_miuix,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_poko_annotations,
        versionRes = R.string.version_poko,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_poko,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_aosp_systemui,
        versionRes = null,
        licenseRes = R.string.license_apache_2,
        websiteRes = R.string.url_aosp_license,
    ),
    OpenSourceLibrary(
        nameRes = R.string.library_asterisk_ng,
        versionRes = R.string.version_asterisk_ng,
        licenseRes = R.string.license_gpl_3,
        websiteRes = R.string.url_asterisk_ng,
    ),
)
