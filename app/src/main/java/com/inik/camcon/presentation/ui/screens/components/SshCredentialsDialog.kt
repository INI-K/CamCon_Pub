package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TouchTarget
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.viewmodel.SshCredentialsPromptReason

/**
 * SSH 로그인 정보 입력 다이얼로그.
 *
 * 카메라가 SSH 터널을 요구하는데 저장된 자격증명이 없거나 거부당했을 때 띄운다.
 * 입력값은 [onSubmit]으로만 밖으로 나가며, 이 컴포저블은 저장 결과를 알지 못한다
 * (저장 실패는 [reason]이 [SshCredentialsPromptReason.STORE_FAILED]로 바뀌어 돌아온다).
 *
 * 비밀번호 원문은 화면 회전으로도 보존하지 않는다 — 저장 상태에 남기면 프로세스 종료 복원
 * 번들에 평문이 실린다.
 */
@Composable
fun SshCredentialsDialog(
    cameraName: String,
    reason: SshCredentialsPromptReason,
    onSubmit: (user: String, password: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AppDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ssh_credentials_title)) },
        text = {
            Column {
                Text(
                    text = cameraName,
                    style = BodySmall,
                    color = TextSecondaryV2
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.ssh_credentials_guide),
                    style = Body,
                    color = TextSecondaryV2
                )

                val notice = when (reason) {
                    SshCredentialsPromptReason.REQUIRED -> null
                    SshCredentialsPromptReason.AUTH_FAILED ->
                        stringResource(R.string.ssh_credentials_auth_failed)

                    SshCredentialsPromptReason.STORE_FAILED ->
                        stringResource(R.string.ssh_credentials_store_failed)
                }
                if (notice != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(text = notice, style = BodySmall, color = ErrorV2)
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = {
                        Text(stringResource(R.string.ssh_credentials_user_label), style = BodySmall)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(
                            stringResource(R.string.ssh_credentials_password_label),
                            style = BodySmall
                        )
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        val stateDesc = if (passwordVisible) {
                            stringResource(R.string.ptpip_password_shown)
                        } else {
                            stringResource(R.string.ptpip_password_hidden)
                        }
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier
                                .size(TouchTarget.min)
                                .semantics { stateDescription = stateDesc }
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Filled.Visibility
                                } else {
                                    Icons.Filled.VisibilityOff
                                },
                                contentDescription = if (passwordVisible) {
                                    stringResource(R.string.ptpip_hide_password)
                                } else {
                                    stringResource(R.string.ptpip_show_password)
                                },
                                modifier = Modifier.size(IconSize.md)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.ptpip_connect),
                enabled = user.isNotBlank() && password.isNotEmpty(),
                onClick = { onSubmit(user, password) }
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

@Preview(name = "SSH 자격증명 입력")
@Composable
private fun SshCredentialsDialogPreview() {
    CamConTheme {
        SshCredentialsDialog(
            cameraName = "ILCE-7M5 (192.168.49.10)",
            reason = SshCredentialsPromptReason.AUTH_FAILED,
            onSubmit = { _, _ -> },
            onDismiss = {}
        )
    }
}
