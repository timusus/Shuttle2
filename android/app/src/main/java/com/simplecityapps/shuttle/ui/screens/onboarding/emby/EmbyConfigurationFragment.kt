package com.simplecityapps.shuttle.ui.screens.onboarding.emby

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.emby.http.LoginCredentials
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class EmbyConfigurationFragment : DialogFragment() {
    @Inject
    lateinit var embyAuthenticationManager: EmbyAuthenticationManager

    var addressInputLayout: TextInputLayout by autoCleared()
    var loginInputLayout: TextInputLayout by autoCleared()
    var passwordInputLayout: TextInputLayout by autoCleared()
    var rememberPasswordSwitch: SwitchCompat by autoCleared()
    var loadingView: CircularLoadingView by autoCleared()
    var inputGroup: Group by autoCleared()

    // Lifecycle

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.fragment_emby_configuration, null)

        addressInputLayout = view.findViewById(R.id.addressInputLayout)
        loginInputLayout = view.findViewById(R.id.loginInputLayout)
        passwordInputLayout = view.findViewById(R.id.passwordInputLayout)
        rememberPasswordSwitch = view.findViewById(R.id.rememberPasswordSwitch)
        loadingView = view.findViewById(R.id.loadingView)
        inputGroup = view.findViewById(R.id.inputGroup)

        embyAuthenticationManager.getAddress()?.let { address ->
            addressInputLayout.editText?.setText(address)
        }
        embyAuthenticationManager.getLoginCredentials()?.username?.let { username ->
            loginInputLayout.editText?.setText(username)
        }
        embyAuthenticationManager.getLoginCredentials()?.password?.let { password ->
            passwordInputLayout.editText?.setText(password)
            passwordInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
        }

        addressInputLayout.editText!!.doOnTextChanged { _, _, _, _ ->
            addressInputLayout.error = null
        }
        loginInputLayout.editText!!.doOnTextChanged { _, _, _, _ ->
            loginInputLayout.error = null
        }
        passwordInputLayout.editText!!.doOnTextChanged { text, _, _, _ ->
            passwordInputLayout.error = null
            if (text?.isEmpty() == true) {
                passwordInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
        }

        loadingView.listener =
            object : CircularLoadingView.Listener {
                override fun onRetryClicked() {
                    loadingView.isVisible = false
                    inputGroup.isVisible = true
                }
            }

        rememberPasswordSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!isChecked) {
                embyAuthenticationManager.setLoginCredentials(null)
            }
        }

        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(requireContext().getString(com.simplecityapps.mediaprovider.R.string.media_provider_title_long_emby))
                .setView(view)
                .setPositiveButton(requireContext().getString(R.string.media_provider_button_authenticate), null)
                .setNegativeButton(requireContext().getString(R.string.dialog_button_close), null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!validate()) {
                    return@setOnClickListener
                }

                inputGroup.isVisible = false
                loadingView.setState(CircularLoadingView.State.Loading(requireContext().getString(R.string.media_provider_authenticating)))
                loadingView.isVisible = true

                embyAuthenticationManager.setAddress(addressInputLayout.editText!!.text.toString())

                val loginCredentials = LoginCredentials(loginInputLayout.editText!!.text.toString(), passwordInputLayout.editText!!.text.toString())

                lifecycleScope.launch {
                    val result =
                        embyAuthenticationManager.authenticate(
                            address = embyAuthenticationManager.getAddress()!!,
                            loginCredentials = loginCredentials
                        )
                    result.onSuccess {
                        if (rememberPasswordSwitch.isChecked) {
                            embyAuthenticationManager.setLoginCredentials(loginCredentials)
                        }
                        loadingView.setState(CircularLoadingView.State.Empty(requireContext().getString(R.string.media_provider_authentication_success)))
                        delay(1000)
                        dialog.dismiss()
                    }
                    result.onFailure { error ->
                        Timber.e("Emby authentication failed. Error ${error.localizedMessage}")
                        loadingView.setState(CircularLoadingView.State.Retry(error.userDescription()))
                    }
                }
            }
        }

        return dialog
    }

    // Public

    fun show(manager: FragmentManager) {
        super.show(manager, "EmbyConfigurationFragment")
    }

    // Private

    private fun validate(): Boolean {
        var hasError = false

        // Host
        if (addressInputLayout.editText!!.text.isEmpty()) {
            addressInputLayout.error = requireContext().getString(R.string.validation_field_required)
            hasError = true
        }

        // Username
        if (loginInputLayout.editText!!.text.isEmpty()) {
            loginInputLayout.error = requireContext().getString(R.string.validation_field_required)
            hasError = true
        }

        return !hasError
    }

    // Static

    companion object {
        fun newInstance() = EmbyConfigurationFragment()
    }
}
