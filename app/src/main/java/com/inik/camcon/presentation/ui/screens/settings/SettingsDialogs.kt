package com.inik.camcon.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton

/**
 * 네이티브 로그 다이얼로그 — 최근 3000자만 표시. [onCopy] 로 클립보드 복사.
 */
@Composable
internal fun NativeLogDialog(
    logContent: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_native_log_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = logContent.takeLast(3000),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.ok),
                onClick = onDismiss
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.settings_native_log_copy_button),
                onClick = onCopy
            )
        }
    )
}

/**
 * 로그아웃 확인 다이얼로그. [onConfirm] 이 실제 로그아웃을 트리거한다.
 */
@Composable
internal fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.account_logout_dialog_title),
                style = HeadingL,
                color = TextPrimaryV2
            )
        },
        text = {
            Text(
                stringResource(R.string.account_logout_dialog_message),
                style = BodySmall,
                color = TextSecondaryV2
            )
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.account_logout_confirm),
                onClick = onConfirm
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.account_logout_cancel),
                onClick = onDismiss
            )
        }
    )
}

/**
 * 계정 삭제 1단계 안내 다이얼로그 — [onContinue] 로 확인(DELETE 입력) 단계로 넘어간다.
 */
@Composable
internal fun DeleteAccountDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.account_delete_dialog_title),
                style = HeadingL,
                color = ErrorV2
            )
        },
        text = {
            Text(
                stringResource(R.string.account_delete_dialog_message),
                style = BodySmall,
                color = TextSecondaryV2
            )
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.account_delete_continue),
                onClick = onContinue
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.account_delete_cancel),
                onClick = onDismiss
            )
        }
    )
}

/**
 * 계정 삭제 2단계 확인 다이얼로그 — "DELETE" 정확 입력 시에만 [onConfirm] 활성.
 * 삭제 진행 중([isDeleting])에는 취소·입력·닫기를 모두 차단한다.
 */
@Composable
internal fun DeleteConfirmDialog(
    input: String,
    onInputChange: (String) -> Unit,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmEnabled = input.trim() == "DELETE" && !isDeleting
    AppDialog(
        onDismissRequest = {
            if (!isDeleting) {
                onDismiss()
            }
        },
        title = {
            Text(
                stringResource(R.string.account_delete_confirm_title),
                style = HeadingL,
                color = ErrorV2
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    stringResource(R.string.account_delete_confirm_message),
                    style = BodySmall,
                    color = TextSecondaryV2
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    singleLine = true,
                    enabled = !isDeleting,
                    placeholder = {
                        Text(
                            stringResource(R.string.account_delete_confirm_placeholder),
                            color = TextTertiary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryV2,
                        unfocusedTextColor = TextPrimaryV2,
                        focusedContainerColor = Surface3,
                        unfocusedContainerColor = Surface3,
                        disabledContainerColor = Surface3,
                        focusedIndicatorColor = ErrorV2,
                        unfocusedIndicatorColor = Surface3,
                        cursorColor = ErrorV2
                    )
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.account_delete_confirm_button),
                enabled = confirmEnabled,
                isLoading = isDeleting,
                onClick = onConfirm
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.account_delete_confirm_cancel),
                onClick = {
                    if (!isDeleting) {
                        onDismiss()
                    }
                }
            )
        }
    )
}

/**
 * 추천 코드 등록 다이얼로그 — 코드 입력 후 [onApply]. 등록 진행 중([isLoading])에는 닫기·취소 차단.
 */
@Composable
internal fun ReferralRedeemDialog(
    input: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val applyEnabled = input.trim().isNotEmpty() && !isLoading
    AppDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        title = {
            Text(
                stringResource(R.string.settings_referral_redeem_title),
                style = HeadingL,
                color = TextPrimaryV2
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    stringResource(R.string.settings_referral_redeem_subtitle),
                    style = BodySmall,
                    color = TextSecondaryV2
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    placeholder = {
                        Text(
                            stringResource(R.string.settings_referral_redeem_hint),
                            color = TextTertiary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryV2,
                        unfocusedTextColor = TextPrimaryV2,
                        focusedContainerColor = Surface3,
                        unfocusedContainerColor = Surface3,
                        disabledContainerColor = Surface3,
                        focusedIndicatorColor = Accent,
                        unfocusedIndicatorColor = Surface3,
                        cursorColor = Accent
                    )
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.settings_referral_redeem_apply),
                enabled = applyEnabled,
                isLoading = isLoading,
                onClick = onApply
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = {
                    if (!isLoading) {
                        onDismiss()
                    }
                }
            )
        }
    )
}
