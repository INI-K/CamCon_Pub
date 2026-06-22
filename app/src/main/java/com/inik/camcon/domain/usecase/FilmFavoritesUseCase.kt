package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 필름 LUT 즐겨찾기 조회/토글을 [AppSettingsRepository] 에 위임하는 얇은 UseCase.
 * 도메인 레이어는 Android 의존성을 갖지 않는다([FilmLutUseCase] 와 동일 패턴).
 */
@Singleton
class FilmFavoritesUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) {

    /** 즐겨찾기한 필름 LUT id 집합 Flow. */
    fun favorites(): Flow<Set<String>> = appSettingsRepository.favoriteFilmLutIds

    /** 주어진 LUT id 의 즐겨찾기 상태를 토글한다. */
    suspend fun toggle(id: String) = appSettingsRepository.toggleFavoriteFilmLut(id)
}
