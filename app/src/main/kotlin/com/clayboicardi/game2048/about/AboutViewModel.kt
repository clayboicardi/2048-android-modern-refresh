package com.clayboicardi.game2048.about

import android.content.res.Resources
import androidx.annotation.RawRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clayboicardi.game2048.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * The About and Licenses screens' data (ARCHITECTURE.md §4.7; DESIGN_SYSTEM.md §6.11, §6.12; D-122,
 * D-124): `res/raw/legacy_history.json` and `res/raw/third_party_licenses.json` with the license
 * texts, read from [resources] on [ioDispatcher], never on the main thread or in composition
 * (AGENTS.md). Entry-scoped: `container.aboutViewModelFactory(LocalResources.current)` inside the
 * `AboutKey` and `LicensesKey` entries; [ioDispatcher] is the container's.
 *
 * Each file is read once, when its flow is first collected ([SharingStarted.Lazily]): About reads
 * only the history and Licenses only the licenses. Both are static, so a read never repeats while
 * the entry lives (configuration changes included).
 */
class AboutViewModel(
    private val resources: Resources,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    /** The credits and the legacy releases (D-124); `null` until read. */
    val history: StateFlow<LegacyHistory?> = flow {
        emit(parseLegacyHistory(readRaw(R.raw.legacy_history)))
    }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * The license groups in the order MIT, OFL-1.1, Apache-2.0, each with its license text (D-122);
     * `null` until read.
     */
    val licenses: StateFlow<ImmutableList<LicenseGroup>?> = flow {
        val groups = LicenseCatalog.group(LicenseCatalog.parse(readRaw(R.raw.third_party_licenses)))
        emit(
            groups
                .map { group ->
                    group.copy(text = LicenseCatalog.textOf(group.licenseId)?.let(::readRaw))
                }
                .toImmutableList(),
        )
    }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** The UTF-8 text of the raw resource [id]; blocking, so only on [ioDispatcher]. */
    private fun readRaw(@RawRes id: Int): String =
        resources.openRawResource(id).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
