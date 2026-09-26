package com.simplecityapps.shuttle.ui.screens.settings.about

import javax.inject.Inject

class GetLicences @Inject constructor(
    private val licencesRepository: LicencesRepository
) {
    suspend operator fun invoke(): List<Licence> = licencesRepository.licences()
}
