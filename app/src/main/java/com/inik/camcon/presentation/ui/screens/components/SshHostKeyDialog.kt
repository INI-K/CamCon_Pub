package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton

/**
 * 호스트키 지문 대조 다이얼로그(TOFU 최초 신뢰).
 *
 * ⚠️ 보안 계약 — 사용자가 카메라 본체 화면의 지문과 [fingerprint]를 눈으로 대조한 뒤에만
 * 신뢰가 저장된다. **대조를 건너뛰고 신뢰하는 버튼이나 경로를 추가하지 않는다.** 그런 경로를
 * 만들면 최초 연결 시점의 중간자를 막을 수 없어 TOFU 를 도입한 이유가 사라진다.
 * 선택지는 [onTrust]와 [onDismiss] 둘뿐이다.
 */
@Composable
fun SshHostKeyDialog(
    cameraName: String,
    fingerprint: String,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        // 지문 대조는 실수로 바깥을 눌러 넘어가면 안 되는 확인 절차다.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.ssh_hostkey_title)) },
        text = {
            Column {
                Text(text = cameraName, style = BodySmall, color = TextSecondaryV2)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.ssh_hostkey_guide),
                    style = Body,
                    color = TextSecondaryV2
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                FingerprintBlock(fingerprint)
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.ssh_hostkey_trust),
                onClick = onTrust
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

/**
 * 지문 불일치 경고 다이얼로그.
 *
 * 저장된 기준값과 카메라가 제시한 지문이 다른 상태이므로 **신뢰 버튼을 두지 않는다.**
 * 불일치를 신뢰로 덮으면 중간자 탐지가 무력화되어 검증을 생략한 것과 같아진다.
 * 사용자가 카메라를 초기화했거나 설정을 다시 한 경우에는 저장된 지문을 지운 뒤 다시 연결한다.
 */
@Composable
fun SshHostKeyMismatchDialog(
    cameraName: String,
    fingerprint: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(
                text = stringResource(R.string.ssh_hostkey_mismatch_title),
                color = ErrorV2
            )
        },
        text = {
            Column {
                Text(text = cameraName, style = BodySmall, color = TextSecondaryV2)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.ssh_hostkey_mismatch_body),
                    style = Body,
                    color = TextSecondaryV2
                )
                if (!fingerprint.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    FingerprintBlock(fingerprint)
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.ssh_hostkey_mismatch_hint),
                    style = BodySmall,
                    color = TextSecondaryV2
                )
            }
        },
        confirmButton = {
            SecondaryButton(
                text = stringResource(R.string.close),
                onClick = onDismiss
            )
        }
    )
}

/** 지문은 한 글자씩 대조하는 값이라 고정폭으로 표시한다. */
@Composable
private fun FingerprintBlock(fingerprint: String) {
    Text(
        text = fingerprint,
        style = MonoNumeric,
        color = TextPrimaryV2,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface3, RoundedCornerShape(Radius.md))
            .padding(Spacing.sm)
    )
}

@Preview(name = "SSH 지문 대조")
@Composable
private fun SshHostKeyDialogPreview() {
    CamConTheme {
        SshHostKeyDialog(
            cameraName = "ILCE-7M5 (192.168.49.10)",
            fingerprint = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU",
            onTrust = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "SSH 지문 불일치")
@Composable
private fun SshHostKeyMismatchDialogPreview() {
    CamConTheme {
        SshHostKeyMismatchDialog(
            cameraName = "ILCE-7M5 (192.168.49.10)",
            fingerprint = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU",
            onDismiss = {}
        )
    }
}
