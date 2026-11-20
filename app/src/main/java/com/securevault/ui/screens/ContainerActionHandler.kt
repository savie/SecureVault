package com.securevault.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.securevault.data.local.ContainerItem
import com.securevault.ui.viewmodel.ContainerViewModel
import com.securevault.utils.KeystoreHelper

fun handleUnlockLock(
    item: ContainerItem,
    isMounted: Boolean,
    masterPassword: String,
    mountedPasswords: MutableMap<String, String>,
    viewModel: ContainerViewModel,
    context: Context,
    canAuthenticate: Boolean,
    biometricGloballyEnabled: Boolean,
    activity: FragmentActivity?,
    onNeedPassword: (PasswordAction) -> Unit
) {
    if (isMounted) {
        val cached = mountedPasswords[item.id]
        if (cached != null) {
            viewModel.unmountContainer(item, cached)
            mountedPasswords.remove(item.id)
        } else if (item.usesMasterPassword) {
            viewModel.unmountContainer(item, masterPassword)
        } else {
            onNeedPassword(PasswordAction.Unmount(item))
        }
    } else {
        if (item.usesMasterPassword) {
            viewModel.mountContainer(item, masterPassword) { }
            return
        }

        val useBiometric = biometricGloballyEnabled &&
            item.biometricEnabled &&
            canAuthenticate &&
            activity != null

        if (useBiometric) {
            try {
                val prompt = BiometricPrompt(
                    activity!!,
                    ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            super.onAuthenticationSucceeded(result)

                            val encrypted =
                                com.securevault.utils.VaultImporterHelper
                                    .getEncryptedPassword(context, item.id)

                            if (encrypted != null) {
                                val pwd = KeystoreHelper.decrypt(encrypted)
                                if (pwd != null) {
                                    viewModel.mountContainer(item, pwd) { }
                                    mountedPasswords[item.id] = pwd
                                } else {
                                    onNeedPassword(PasswordAction.Mount(item))
                                }
                            } else {
                                onNeedPassword(PasswordAction.Mount(item))
                            }
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {
                            super.onAuthenticationError(errorCode, errString)

                            when (errorCode) {
                                BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                    // User explicitly selected "Use Password".
                                    onNeedPassword(PasswordAction.Mount(item))
                                }

                                BiometricPrompt.ERROR_USER_CANCELED,
                                BiometricPrompt.ERROR_CANCELED -> {
                                    // Cancel or Back: keep the Container locked.
                                }

                                else -> {
                                    // Lockout or another authentication error:
                                    // allow the existing password fallback.
                                    onNeedPassword(PasswordAction.Mount(item))
                                }
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            // An unsuccessful biometric attempt never unlocks.
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock ${item.name}")
                    .setSubtitle("Authenticate to unlock this Container")
                    .setNegativeButtonText("Use Password")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                    )
                    .build()

                prompt.authenticate(promptInfo)
            } catch (_: Exception) {
                onNeedPassword(PasswordAction.Mount(item))
            }
        } else {
            // Global biometric OFF or per-Container biometric OFF.
            onNeedPassword(PasswordAction.Mount(item))
        }
    }
}
