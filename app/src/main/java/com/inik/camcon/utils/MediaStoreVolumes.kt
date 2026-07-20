package com.inik.camcon.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * 사진 저장 대상 MediaStore 볼륨 선택 유틸.
 *
 * 폰에 제거식 SD카드가 마운트돼 있으면 사진을 SD카드에 우선 저장하고, 없거나(또는 탈거되어)
 * 마운트 목록에서 사라지면 자동으로 primary(내장 외부저장소)로 폴백한다.
 *
 * [pickPreferredVolume]은 android.* 의존이 없는 순수 함수(단위 테스트 대상)이고,
 * android 프레임워크(MediaStore)에 접근하는 것은 [preferredImagesUri]뿐이다.
 */
object MediaStoreVolumes {

    // MediaStore.VOLUME_EXTERNAL_PRIMARY 와 동일한 값. pickPreferredVolume 을 android.* 무의존
    // 순수 함수로 유지하기 위해 리터럴로 비교한다.
    private const val VOLUME_EXTERNAL_PRIMARY = "external_primary"

    /**
     * 제거식(비-primary) 볼륨이 있으면 그중 사전순 첫 번째(결정성)를, 없으면 primary 볼륨명을 반환한다.
     *
     * @param volumeNames [MediaStore.getExternalVolumeNames] 결과 — '현재 마운트된' 볼륨만 포함한다.
     */
    fun pickPreferredVolume(volumeNames: Collection<String>): String =
        volumeNames
            .filter { it != VOLUME_EXTERNAL_PRIMARY }
            .minOrNull()
            ?: VOLUME_EXTERNAL_PRIMARY

    /**
     * 선호 볼륨의 이미지 컬렉션 URI.
     *
     * [MediaStore.getExternalVolumeNames]는 현재 마운트된 볼륨만 반환하므로 SD카드 탈거 시
     * 자동으로 primary URI로 폴백된다.
     */
    fun preferredImagesUri(context: Context): Uri {
        val volume = pickPreferredVolume(MediaStore.getExternalVolumeNames(context))
        return MediaStore.Images.Media.getContentUri(volume)
    }
}
