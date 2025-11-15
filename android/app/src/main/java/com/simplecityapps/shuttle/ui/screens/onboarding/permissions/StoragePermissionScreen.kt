package com.simplecityapps.shuttle.ui.screens.onboarding.permissions

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.simplecityapps.shuttle.ui.theme.ColorSchemePreviewParameterProvider

class StoragePermissionScreenFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val onboardingParent = parentFragment as OnboardingParent
        return ComposeView(requireContext()).apply {
            setContent {
                AppTheme {
                    StoragePermissionScreen(onPermissionGranted = onboardingParent::goToNext)
                }
            }
        }
    }
}

@Composable
fun StoragePermissionScreen(
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalActivity.current
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var showPermissionRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && activity?.shouldShowRequestPermissionRationale(storagePermission) == true) {
            showPermissionRationale = true
        } else {
            showPermissionRationale = false
            onPermissionGranted()
        }
    }
    StoragePermissionScreen(
        modifier = modifier,
        showPermissionRationale = showPermissionRationale,
        onDismissPermissionRationale = { showPermissionRationale = false },
        onGrantPermissionClick = { launcher.launch(storagePermission) }
    )
}

@Composable
private fun StoragePermissionScreen(
    showPermissionRationale: Boolean,
    onGrantPermissionClick: () -> Unit,
    onDismissPermissionRationale: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (showPermissionRationale) {
        PermissionRationaleDialog(
            onDismissRequest = onDismissPermissionRationale,
            onGrantPermissionClick = onGrantPermissionClick
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            contentDescription = null,
            modifier = Modifier
                .size(196.dp)
                .padding(top = 40.dp),
            tint = MaterialTheme.colorScheme.primary,
            painter = painterResource(R.drawable.ic_launcher_foreground)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            text = stringResource(R.string.onboarding_permission_help_text)
        )
        Button(onClick = onGrantPermissionClick) {
            Text(text = stringResource(R.string.onboarding_permission_button_grant))
        }
    }
}

@Composable
private fun PermissionRationaleDialog(
    onDismissRequest: () -> Unit,
    onGrantPermissionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.onboarding_permission_dialog_title))
        },
        text = {
            Text(text = stringResource(R.string.onboarding_permission_dialog_subtitle))
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.dialog_button_close))
            }
        },
        confirmButton = {
            TextButton(onClick = onGrantPermissionClick) {
                Text(text = stringResource(R.string.dialog_button_retry))
            }
        }
    )
}

@Preview
@Composable
private fun Preview(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        StoragePermissionScreen(
            onGrantPermissionClick = {},
            showPermissionRationale = false,
            onDismissPermissionRationale = {}
        )
    }
}

@Preview
@Composable
private fun Rationale(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        StoragePermissionScreen(
            onGrantPermissionClick = {},
            showPermissionRationale = true,
            onDismissPermissionRationale = {}
        )
    }
}
