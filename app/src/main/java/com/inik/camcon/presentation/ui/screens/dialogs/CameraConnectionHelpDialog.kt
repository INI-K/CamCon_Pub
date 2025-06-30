package com.inik.camcon.presentation.ui.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.CamConTheme

/**
 * 카메라 연결 문제 해결 도움말 다이얼로그
 */
@Composable
fun CameraConnectionHelpDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("카메라 연결 문제 해결") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "카메라를 찾을 수 없습니다. 다음을 확인해주세요:",
                    style = MaterialTheme.typography.body1
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF2A2A2A)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HelpRow("1. ", "카메라가 PC/원격 모드로 설정되어 있는지 확인하세요.")
                        HelpRow("2. ", "USB 케이블이 제대로 연결되어 있는지 확인하세요.")
                        HelpRow("3. ", "카메라 전원이 켜져 있는지 확인하세요.")
                        HelpRow("4. ", "다른 앱에서 카메라를 사용 중이지 않은지 확인하세요.")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "카메라별 설정 방법:",
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold
                )

                CameraBrandInstructions()
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("다시 시도")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun HelpRow(
    number: String,
    instruction: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(number, fontWeight = FontWeight.Bold)
        Text(instruction)
    }
}

@Composable
private fun CameraBrandInstructions() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "• 캐논: 메뉴 > 통신 설정 > USB 연결 > PC 원격 촬영",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        Text(
            "• 니콘: 메뉴 > USB > PTP/MTP 모드",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        Text(
            "• 소니: 메뉴 > USB 연결 > PC 원격",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Preview(name = "Camera Connection Help Dialog", showBackground = true)
@Composable
private fun CameraConnectionHelpDialogPreview() {
    CamConTheme {
        CameraConnectionHelpDialog(
            onDismiss = { },
            onRetry = { }
        )
    }
}