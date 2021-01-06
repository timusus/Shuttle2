package com.simplecityapps.shuttle.ui.screens.onboarding.emby

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.emby.http.LoginCredentials
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class EmbyConfigurationFragment : DialogFragment(), Injectable {

    @Inject lateinit var embyAuthenticationManager: EmbyAuthenticationManager

    var hostInputLayout: TextInputLayout by autoCleared()
    var portInputLayout: TextInputLayout by autoCleared()
    var loginInputLayout: TextInputLayout by autoCleared()
    var passwordInputLayout: TextInputLayout by autoCleared()
    var rememberPasswordSwitch: SwitchCompat by autoCleared()
    var loadingView: CircularLoadingView by autoCleared()
    var inputGroup: Group by autoCleared()

    // Lifecycle

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_emby_configuration, null)

        hostInputLayout = view.findViewById(R.id.hostInputLayout)
        portInputLayout = view.findViewById(R.id.portInputLayout)
        loginInputLayout = view.findViewById(R.id.loginInputLayout)
        passwordInputLayout = view.findViewById(R.id.passwordInputLayout)
        rememberPasswordSwitch = view.findViewById(R.id.rememberPasswordSwitch)
        loadingView = view.findViewById(R.id.loadingView)
        inputGroup = view.findViewById(R.id.inputGroup)

        embyAuthenticationManager.getHost()?.let { host ->
            hostInputLayout.editText?.setText(host)
        }
        embyAuthenticationManager.getPort()?.let { port ->
            portInputLayout.editText?.setText(port.toString())
        }
        embyAuthenticationManager.getLoginCredentials()?.username?.let { username ->
            loginInputLayout.editText?.setText(username)
        }
        embyAuthenticationManager.getLoginCredentials()?.password?.let { password ->
            passwordInputLayout.editText?.setText(password)
            passwordInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
        }

        hostInputLayout.editText!!.doOnTextChanged { _, _, _, _ ->
            hostInputLayout.error = null
        }
        portInputLayout.editText!!.doOnTextChanged { _, _, _, _ ->
            portInputLayout.error = null
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

        loadingView.listener = object : CircularLoadingView.Listener {
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

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Emby Media Server")
            .setView(view)
            .setPositiveButton("Authenticate", null)
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

                if (!validate()) {
                    return@setOnClickListener
                }

                inputGroup.isVisible = false
                loadingView.setState(CircularLoadingView.State.Loading("Authenticating…"))
                loadingView.isVisible = true

                embyAuthenticationManager.setAddress(hostInputLayout.editText!!.text.toString(), portInputLayout.editText!!.text.toString().toInt())

                val loginCredentials = LoginCredentials(loginInputLayout.editText!!.text.toString(), passwordInputLayout.editText!!.text.toString())

                lifecycleScope.launch {
                    val result = embyAuthenticationManager.authenticate(
                        address = embyAuthenticationManager.getAddress()!!,
                        loginCredentials = loginCredentials
                    )
                    result.onSuccess {
                        if (rememberPasswordSwitch.isChecked) {
                            embyAuthenticationManager.setLoginCredentials(loginCredentials)
                        }
                        loadingView.setState(CircularLoadingView.State.Empty("Authentication Successful"))
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
        if (hostInputLayout.editText!!.text.isEmpty()) {
            hostInputLayout.error = "Required"
            hasError = true
        }

        // Port
        if (portInputLayout.editText!!.text.isEmpty()) {
            portInputLayout.error = "Required"
            hasError = true
        }
        if (portInputLayout.editText!!.text.toString().toIntOrNull() == null) {
            portInputLayout.error = "Invalid"
            hasError = true
        }

        // Username
        if (loginInputLayout.editText!!.text.isEmpty()) {
            loginInputLayout.error = "Required"
            hasError = true
        }

        // Password
        if (passwordInputLayout.editText!!.text.isEmpty()) {
            passwordInputLayout.error = "Required"
            hasError = true
        }

        return !hasError
    }


    // Static

    companion object {
        fun newInstance() = EmbyConfigurationFragment()
    }
}