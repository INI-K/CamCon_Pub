package com.inik.camcon.di

import com.inik.camcon.data.network.ptpip.ssh.JschSessionFactory
import com.inik.camcon.data.network.ptpip.ssh.SshSessionFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SSH 터널 관련 바인딩 모듈(설계 D1).
 *
 * `SshTunnelManager` 와 `LocalPortAllocator` 는 생성자 주입만으로 해결되므로 여기 등록하지 않는다.
 * 인터페이스인 [SshSessionFactory] 만 구현체 지정이 필요해서 이 모듈을 둔다.
 *
 * `RepositoryModule` 이 아니라 별도 파일인 이유는 그 파일이 도메인 인터페이스 바인딩 전용
 * 자리이고 [SshSessionFactory] 는 Data 레이어 내부 경계이기 때문이다. 형식은 `CacheModule` 의
 * 선례(`object` + `@Provides`)를 따른다.
 */
@Module
@InstallIn(SingletonComponent::class)
object SshModule {

    @Provides
    @Singleton
    fun provideSshSessionFactory(impl: JschSessionFactory): SshSessionFactory = impl
}
