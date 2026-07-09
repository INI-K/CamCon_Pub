package com.inik.camcon.presentation.util

import android.content.ContentUris
import android.provider.MediaStore

/**
 * MediaStore content URI 매핑 순수 함수(단위테스트 대상).
 *
 * `CapturedPhoto.id` 는 두 종류다:
 * - MediaStore 쿼리 결과 row → `_ID`(Long 문자열)
 * - 파일시스템 폴백 row → `UUID.randomUUID()`(Long 아님)
 *
 * 스코프드 스토리지(API29+)에서 raw File 경로 접근이 막히므로, `_ID` 인 경우에만 이미지
 * content URI 로 관통(로드/EXIF/공유)한다. UUID 폴백이면 null 을 돌려 호출부가 기존 filePath
 * 로 폴백하게 한다.
 *
 * @param mediaId `CapturedPhoto.id`
 * @return content URI 문자열, 혹은 id 가 Long 이 아니면 null
 */
fun imageContentUriOrNull(mediaId: String): String? =
    mediaId.toLongOrNull()?.let { id ->
        ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
        ).toString()
    }
