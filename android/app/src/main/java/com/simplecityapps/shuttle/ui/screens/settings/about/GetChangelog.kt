package com.simplecityapps.shuttle.ui.screens.settings.about

import com.simplecityapps.shuttle.ui.screens.changelog.Changeset
import javax.inject.Inject

class GetChangelog @Inject constructor(
    private val changelogRepository: ChangelogRepository
) {
    suspend operator fun invoke(): List<Changeset> = changelogRepository.changelog()
}
