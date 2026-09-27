package com.simplecityapps.shuttle.ui.screens.settings.about

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.platform.AppVersion
import com.simplecityapps.shuttle.platform.BundledText
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelsTest {
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val appVersion = AppVersion { "2026.09.27" }
    private val files = mutableMapOf<String, String>()
    private val bundledText = BundledText { files[it] }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `What's new lists the bundled changelog and marks this version seen`() {
        files["changelog.json"] = CHANGELOG
        preferences.showChangelogOnLaunch = true

        val viewModel = WhatsNewViewModel(GetChangelog(ChangelogRepository(bundledText)), MarkChangelogViewed(preferences, appVersion))

        val changeset = viewModel.uiState.value.changesets.single()
        viewModel.uiState.value.loading shouldBe false
        changeset.versionName shouldBe "1.0.10"
        changeset.date shouldBe LocalDate(2024, 3, 9)
        changeset.features shouldBe listOf("A feature")
        IsWhatsNewPending(preferences, appVersion)() shouldBe false
    }

    @Test
    fun `What's new is pending until this version's notes are seen`() {
        preferences.showChangelogOnLaunch = true
        preferences.lastViewedChangelogVersion = "2020.01.01"

        IsWhatsNewPending(preferences, appVersion)() shouldBe true
        MarkChangelogViewed(preferences, appVersion)()
        preferences.lastViewedChangelogVersion shouldBe "2026.09.27"
    }

    @Test
    fun `an invalid or missing changelog reads as empty`() {
        val viewModel = { WhatsNewViewModel(GetChangelog(ChangelogRepository(bundledText)), MarkChangelogViewed(preferences, appVersion)) }

        viewModel().uiState.value.changesets.shouldBeEmpty()
        files["changelog.json"] = "[{\"versionName\": 1}]"
        viewModel().uiState.value.changesets.shouldBeEmpty()
    }

    @Test
    fun `licences come from the aboutlibraries metadata sorted by name`() {
        files["aboutlibraries.json"] = LICENCES

        val viewModel = LicencesViewModel(GetLicences(LicencesRepository(bundledText)))

        viewModel.uiState.value.loading shouldBe false
        viewModel.uiState.value.licences shouldBe listOf(
            Licence(name = "Activity", version = "1.11.0", licence = "Apache License 2.0", website = "https://developer.android.com"),
            Licence(name = "Zed", version = null, licence = null, website = null),
        )
    }

    @Test
    fun `no licences metadata lists no licences`() {
        LicencesViewModel(GetLicences(LicencesRepository(bundledText))).uiState.value.licences.shouldBeEmpty()
    }

    private companion object {
        val CHANGELOG = """
            [{"versionName": "1.0.10", "releaseDate": "09/03/2024", "features": ["A feature"], "fixes": [], "improvements": [], "notes": []}]
        """.trimIndent()

        val LICENCES = """
            {
              "libraries": [
                {"uniqueId": "z:zed", "name": "Zed", "artifactVersion": "", "licenses": []},
                {"uniqueId": "androidx.activity:activity", "name": "Activity", "artifactVersion": "1.11.0", "website": "https://developer.android.com", "licenses": ["Apache-2.0"]}
              ],
              "licenses": {
                "Apache-2.0": {"hash": "Apache-2.0", "internalHash": "Apache-2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0", "spdxId": "Apache-2.0", "name": "Apache License 2.0"}
              }
            }
        """.trimIndent()
    }
}
