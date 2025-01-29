package com.simplecityapps.shuttle.ui.screens.onboarding.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParentFragment

class StoragePermissionFragment :
    Fragment(),
    OnboardingChild {
    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_onboarding_permissions, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val mediaPermissionButton: Button = view.findViewById(R.id.grantPermissionButton)
        mediaPermissionButton.setOnClickListener {
            requestStoragePermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == OnboardingParentFragment.REQUEST_CODE_READ_STORAGE && grantResults.all { result -> result == PackageManager.PERMISSION_GRANTED }) {
            getParent().goToNext()
        } else {
            if (shouldShowRequestPermissionRationale(getStoragePermission())) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.onboarding_permission_dialog_title))
                    .setMessage(getString(R.string.onboarding_permission_dialog_subtitle))
                    .setPositiveButton(getString(R.string.dialog_button_retry)) { _, _ -> requestStoragePermission() }
                    .setNegativeButton(getString(R.string.dialog_button_close), null)
                    .show()
            } else {
                getParent().goToNext()
            }
        }
    }

    // Private

    private fun requestStoragePermission() {
        requestPermissions(arrayOf(getStoragePermission()), OnboardingParentFragment.REQUEST_CODE_READ_STORAGE)
    }

    private fun getStoragePermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // OnboardingChild Implementation

    override val page = OnboardingPage.StoragePermission

    override fun getParent() = parentFragment as OnboardingParent

    override fun handleNextButtonClick() {
        getParent().goToNext()
    }
}
