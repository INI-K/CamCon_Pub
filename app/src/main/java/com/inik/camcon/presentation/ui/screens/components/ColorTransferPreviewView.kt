package com.inik.camcon.presentation.ui.screens.components

import android.graphics.ColorSpace
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.io.File

/**
 * 색감 전송용 미리보기 뷰 - 위아래로 2분할하여 색감 참조 이미지와 적용 대상 이미지를 표시
 */
@Composable
fun ColorTransferPreviewView(
    referenceImagePath: String? = null,
    targetImagePath: String? = null,
    onReferenceImageClick: () -> Unit = {},
    onTargetImageClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 상단: 색감 참조 이미지
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        if (enabled) MaterialTheme.colors.surface
                        else MaterialTheme.colors.surface.copy(alpha = 0.6f)
                    )
            ) {
                val referenceClick = if (enabled) onReferenceImageClick else ({})
                ColorTransferImageSection(
                    imagePath = referenceImagePath,
                    title = "색감 참조 이미지",
                    subtitle = "이 이미지의 색감이 적용됩니다",
                    placeholder = "색감을 가져올\n참조 이미지를 선택하세요",
                    onClick = referenceClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 색감 전송 표시 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "색감 전송",
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 구분선
            Divider(
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            // 하단: 적용 대상 이미지 
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        if (enabled) MaterialTheme.colors.surface
                        else MaterialTheme.colors.surface.copy(alpha = 0.6f)
                    )
            ) {
                val targetClick = if (enabled) onTargetImageClick else ({})
                ColorTransferImageSection(
                    imagePath = targetImagePath,
                    title = "적용 대상 이미지",
                    subtitle = "색감이 적용될 이미지입니다",
                    placeholder = "색감이 적용될\n대상 이미지를 선택하세요",
                    onClick = targetClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 색감 전송 이미지 섹션 컴포넌트
 */
@Composable
private fun ColorTransferImageSection(
    imagePath: String?,
    title: String,
    subtitle: String,
    placeholder: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (imagePath != null && File(imagePath).exists()) {
            // 이미지가 있을 때
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 이미지 미리보기
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (enabled) MaterialTheme.colors.onSurface.copy(alpha = 0.2f)
                            else MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imagePath)
                                .crossfade(true)
                                .memoryCacheKey(imagePath)
                                .apply {
                                    // sRGB 색공간 설정
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                                    }
                                }
                                .build()
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = if (enabled) 1f else 0.6f
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 이미지 정보
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colors.onSurface 
                                else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.body2,
                        color = if (enabled) MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                else MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val file = File(imagePath)
                    Text(
                        text = "파일명: ${file.name}",
                        style = MaterialTheme.typography.caption,
                        color = if (enabled) MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
                    
                    Text(
                        text = "크기: ${file.length() / 1024} KB",
                        style = MaterialTheme.typography.caption,
                        color = if (enabled) MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
                    
                    if (enabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "탭하여 다른 이미지 선택",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else {
            // 이미지가 없을 때 플레이스홀더
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Default.Add else Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (enabled) MaterialTheme.colors.primary.copy(alpha = 0.7f)
                           else MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colors.onSurface 
                            else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.body2,
                    color = if (enabled) MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            else MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
                
                if (enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = onClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("선택하기")
                    }
                }
            }
        }
    }
}