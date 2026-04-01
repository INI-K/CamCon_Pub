#!/bin/bash
# libgphoto2 Android 크로스 컴파일 빌드 스크립트
#
# 사용법:
#   chmod +x build_libgphoto2_android.sh
#   ANDROID_NDK_HOME=/path/to/ndk ./build_libgphoto2_android.sh
#
# 또는 NDK 경로를 환경변수로 지정:
#   export ANDROID_NDK_HOME=/path/to/ndk
#   ./build_libgphoto2_android.sh

##############################################
# 자동 부트스트랩 (직접 실행 가능하게)
##############################################

# bash 3.x (macOS 기본)이면 homebrew bash로 자동 재실행
if (( BASH_VERSINFO[0] < 4 )); then
    for _bash in /opt/homebrew/bin/bash /usr/local/bin/bash; do
        if [ -x "$_bash" ] && "$_bash" -c '(( BASH_VERSINFO[0] >= 4 ))' 2>/dev/null; then
            exec "$_bash" "$0" "$@"
        fi
    done
    echo ""
    echo "ERROR: bash 4.0+ 가 필요합니다 (현재: $BASH_VERSION)"
    echo "  설치: brew install bash"
    echo "  이후 다시 실행하면 자동으로 적용됩니다."
    exit 1
fi

set -euo pipefail

##############################################
# 1. 플랫폼 감지 및 환경 설정
##############################################
OS_TYPE="$(uname -s)"
ARCH_TYPE="$(uname -m)"

if [[ "$OS_TYPE" == "Darwin" ]]; then
    [[ "$ARCH_TYPE" == "arm64" ]] && PREBUILT_HOST="darwin-arm64" || PREBUILT_HOST="darwin-x86_64"
    NPROC=$(sysctl -n hw.logicalcpu)
    # arm64은 aarch64로 정규화 (autoconf config.sub 호환성)
    [[ "$ARCH_TYPE" == "arm64" ]] && BUILD_TRIPLE="aarch64-apple-darwin" || BUILD_TRIPLE="${ARCH_TYPE}-apple-darwin"
else
    PREBUILT_HOST="linux-x86_64"
    NPROC=$(nproc)
    BUILD_TRIPLE="x86_64-linux-gnu"
fi

##############################################
# 2. 필요 도구 자동 설치
##############################################
check_and_install_tools() {
    echo ">> 필요 도구 확인 중..."

    if [[ "$OS_TYPE" == "Darwin" ]]; then
        if ! command -v brew &>/dev/null; then
            echo ""
            echo "ERROR: Homebrew가 필요합니다."
            echo "  설치: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/homebrew/install/HEAD/install.sh)\""
            exit 1
        fi

        local missing=()
        for tool in cmake autoconf automake pkg-config git gettext; do
            command -v "$tool" &>/dev/null || missing+=("$tool")
        done
        # macOS libtool은 brew 패키지명이 다름 (glibtool)
        command -v glibtool &>/dev/null || command -v libtool &>/dev/null || missing+=("libtool")

        if (( ${#missing[@]} > 0 )); then
            echo ">> 누락된 도구 자동 설치: ${missing[*]}"
            brew install "${missing[@]}"
        fi

    else
        # Linux: 없는 도구 목록만 출력
        local missing=()
        for tool in cmake autoconf automake libtool pkg-config git wget; do
            command -v "$tool" &>/dev/null || missing+=("$tool")
        done
        if (( ${#missing[@]} > 0 )); then
            echo ""
            echo "ERROR: 다음 도구를 설치해주세요:"
            echo "  apt: sudo apt-get install -y ${missing[*]}"
            echo "  yum: sudo yum install -y ${missing[*]}"
            exit 1
        fi
    fi

    echo ">> 도구 확인 완료."
}

check_and_install_tools

##############################################
# 3. NDK 자동 탐색 및 경로 확인
##############################################
export API="${API:-29}"

find_ndk() {
    local sdk_roots=()
    if [[ "$OS_TYPE" == "Darwin" ]]; then
        sdk_roots=(
            "$HOME/Library/Android/sdk"
            "$HOME/Library/Android/Sdk"
            "${ANDROID_SDK_ROOT:-}"
            "${ANDROID_HOME:-}"
        )
    else
        sdk_roots=(
            "$HOME/Android/Sdk"
            "$HOME/Android/sdk"
            "${ANDROID_SDK_ROOT:-}"
            "${ANDROID_HOME:-}"
        )
    fi

    for sdk in "${sdk_roots[@]}"; do
        [ -z "$sdk" ] || [ ! -d "$sdk" ] && continue
        # sdk/ndk/<version> 형태 (Android Studio 최신)
        if [ -d "$sdk/ndk" ]; then
            # 디렉토리명이 버전 번호(예: 28.0.12916984), 내림차순 정렬 후 최신 선택
            local ndk_dir
            ndk_dir=$(ls -1 "$sdk/ndk" 2>/dev/null | sort -t. -k1,1rn -k2,2rn | head -1)
            if [ -n "$ndk_dir" ] && [ -d "$sdk/ndk/$ndk_dir" ]; then
                echo "$sdk/ndk/$ndk_dir"
                return 0
            fi
        fi
        # sdk/ndk-bundle 형태 (구버전 Android Studio)
        if [ -d "$sdk/ndk-bundle" ] && [ -f "$sdk/ndk-bundle/source.properties" ]; then
            echo "$sdk/ndk-bundle"
            return 0
        fi
    done

    # 수동 설치 경로
    for path in /opt/android-ndk /usr/local/android-ndk; do
        if [ -d "$path" ] && [ -f "$path/source.properties" ]; then
            echo "$path"
            return 0
        fi
    done

    return 1
}

# ANDROID_NDK_HOME 결정 (환경변수 > 자동 탐색)
if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "${ANDROID_NDK_HOME:-}" ]; then
    echo ">> ANDROID_NDK_HOME 자동 탐색 중..."
    if found_ndk=$(find_ndk); then
        export ANDROID_NDK_HOME="$found_ndk"
        echo ">> NDK 자동 발견: $ANDROID_NDK_HOME"
    else
        echo ""
        echo "ERROR: Android NDK를 찾을 수 없습니다."
        echo "  다음 중 하나로 해결하세요:"
        echo "  1) 환경변수 지정: ANDROID_NDK_HOME=/path/to/ndk ./build_libgphoto2_android.sh"
        echo "  2) Android Studio -> SDK Manager -> SDK Tools -> NDK 설치"
        echo "  3) 직접 다운로드 후 경로 지정: https://developer.android.com/ndk/downloads"
        exit 1
    fi
else
    echo ">> NDK: $ANDROID_NDK_HOME"
fi

PREBUILT_DIR="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"

# 선호 host가 없으면 실제 존재하는 prebuilt 디렉토리로 자동 폴백
if [ ! -d "$PREBUILT_DIR/$PREBUILT_HOST" ]; then
    found_host=$(ls -1 "$PREBUILT_DIR" 2>/dev/null | head -1)
    if [ -n "$found_host" ] && [ -d "$PREBUILT_DIR/$found_host" ]; then
        echo ">> WARNING: $PREBUILT_HOST 없음, 대신 사용: $found_host"
        PREBUILT_HOST="$found_host"
    else
        echo ""
        echo "ERROR: NDK toolchain 경로가 없습니다: $PREBUILT_DIR"
        echo "  ANDROID_NDK_HOME 경로 또는 NDK 설치를 확인하세요."
        exit 1
    fi
fi

export TOOLCHAIN="$PREBUILT_DIR/$PREBUILT_HOST"
export SYSROOT="$TOOLCHAIN/sysroot"
export OBJCOPY_BIN="$TOOLCHAIN/bin/llvm-objcopy"
export PATH="$TOOLCHAIN/bin:$PATH"

# NDK 버전 출력
ndk_ver=$(grep "Pkg.Revision" "$ANDROID_NDK_HOME/source.properties" 2>/dev/null | cut -d= -f2 | tr -d ' ' || echo "unknown")
echo ">> NDK 버전: $ndk_ver | Toolchain: $PREBUILT_HOST | API: $API"

# 16KB 페이지 크기 지원 (Android 15+ Google Play 요구사항)
# PT_LOAD 세그먼트 정렬을 링크 타임에 16KB로 설정
ALIGN_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

# 빌드 대상 ABI (x86 32비트는 Google Play 신규 앱 불필요)
declare -A ABI_TARGETS=(
    ["arm64-v8a"]="aarch64-linux-android"
)

##############################################
# 4. 버전 정의
##############################################
# libgphoto2: git master HEAD 사용 (PR #1187 ptp_list_folder 루트 캐싱 수정 포함)
# 2.5.33 릴리즈에는 이 수정이 누락되어 있어 PTP/IP 파일 전송이 매우 느림
LIBGPHOTO2_REPO="https://github.com/gphoto/libgphoto2.git"
LIBGPHOTO2_BRANCH="master"
LIBTOOL_VER="2.4.7"
LIBUSB_VER="1.0.27"
LIBXML2_VER="2.13.6"
LIBJPEG_TURBO_VER="3.1.0"   # v3.0+은 CMake만 지원
LIBEXIF_VER="0.6.25"

##############################################
# 5. 공통 유틸리티
##############################################
BASE_DIR="$(pwd)"
BUILD_DIR="$BASE_DIR/gphoto-build_16k"
SOURCE_DIR="$BUILD_DIR/sources"
mkdir -p "$BUILD_DIR" "$SOURCE_DIR"

# wget 없는 환경(macOS 기본)에서 curl 폴백
fetch() {
    local url="$1"
    local name
    name="$(basename "$url")"
    if [ ! -f "$SOURCE_DIR/$name" ]; then
        echo ">> 다운로드: $name"
        if command -v wget &>/dev/null; then
            wget -O "$SOURCE_DIR/$name" "$url"
        else
            curl -fSL -o "$SOURCE_DIR/$name" "$url"
        fi
    fi
}

extract_if_needed() {
    local archive="$1"
    local target_dir="$2"
    if [ ! -d "$target_dir" ]; then
        mkdir -p "$target_dir"
        tar xf "$SOURCE_DIR/$archive" -C "$target_dir" --strip-components=1
    fi
}

# macOS BSD sed / GNU sed 호환 래퍼
sed_inplace() {
    if [[ "$OS_TYPE" == "Darwin" ]]; then
        sed -i '' "$@"
    else
        sed -i "$@"
    fi
}

# 링크 타임 정렬(-Wl,-z,max-page-size)이 핵심이며,
# 이 함수는 섹션 헤더 sh_addralign을 추가로 맞춰주는 보완 처리
align_shared_objects() {
    local root_dir="$1"
    if [ ! -x "$OBJCOPY_BIN" ]; then
        echo "WARNING: llvm-objcopy 없음, 섹션 정렬 후처리 건너뜀"
        return
    fi
    [ -d "$root_dir" ] || return
    echo ">> 16KB 섹션 정렬 후처리: $root_dir"
    find "$root_dir" -type f -name "*.so" -print0 | while IFS= read -r -d '' so; do
        "$OBJCOPY_BIN" \
            --set-section-alignment .text=16384 \
            --set-section-alignment .rodata=16384 \
            --set-section-alignment .data.rel.ro=16384 \
            --set-section-alignment .data=16384 \
            --set-section-alignment .bss=16384 \
            --set-section-alignment .tbss=16384 \
            "$so" 2>/dev/null || true
    done
}

##############################################
# 6. 의존성 빌드
##############################################
build_dependencies() {
    local TARGET="$1"
    local ABI="$2"
    local TARGET_HOST="${TARGET}${API}"
    local ABI_BUILD_DIR="$BUILD_DIR/$ABI"

    echo ">> 의존성 빌드: $ABI ($TARGET_HOST)"
    mkdir -p "$ABI_BUILD_DIR"

    export CC="$TOOLCHAIN/bin/${TARGET_HOST}-clang"
    export CXX="$TOOLCHAIN/bin/${TARGET_HOST}-clang++"
    export AR="$TOOLCHAIN/bin/llvm-ar"
    export NM="$TOOLCHAIN/bin/llvm-nm"
    export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$TOOLCHAIN/bin/llvm-strip"

    # 공유 라이브러리 빌드에도 16KB 정렬 플래그 적용
    export CFLAGS="--sysroot=$SYSROOT -fPIC -O2 -Wno-error -Wno-incompatible-pointer-types -Wno-unused-but-set-variable -Wno-sign-compare"
    export CXXFLAGS="$CFLAGS"
    export LDFLAGS="--sysroot=$SYSROOT $ALIGN_LDFLAGS"

    # configure 크로스 컴파일 캐시 변수
    export LTDL_INSTALL_SLIBS=yes
    export ac_cv_func_dlopen_works=yes
    export ac_cv_func_dlsym_works=yes
    export ac_cv_func_dlclose_works=yes
    export ac_cv_header_dlfcn_h=yes
    export lt_cv_deplibs_check_method=pass_all
    # NDK clang은 GNU ld 호환 인터페이스를 사용 - subdir configure에도 전파됨
    export lt_cv_prog_gnu_ld=yes

    # --- libtool (libltdl) ---
    echo ">> Building libtool-${LIBTOOL_VER}..."
    cd "$ABI_BUILD_DIR"
    extract_if_needed "libtool-${LIBTOOL_VER}.tar.gz" "$ABI_BUILD_DIR/libtool-${LIBTOOL_VER}"
    cd "libtool-${LIBTOOL_VER}"
    rm -rf install && mkdir install
    ./configure \
        --host="$TARGET_HOST" --build="$BUILD_TRIPLE" \
        --prefix="$PWD/install" \
        CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
        --enable-shared --disable-static --enable-ltdl-install \
        --disable-binaries
    make -j"$NPROC" && make install

    # --- libusb ---
    echo ">> Building libusb-${LIBUSB_VER}..."
    cd "$ABI_BUILD_DIR"
    extract_if_needed "libusb-${LIBUSB_VER}.tar.bz2" "$ABI_BUILD_DIR/libusb-${LIBUSB_VER}"
    cd "libusb-${LIBUSB_VER}"
    rm -rf install && mkdir install
    ./configure \
        --host="$TARGET_HOST" --build="$BUILD_TRIPLE" \
        --prefix="$PWD/install" \
        CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
        --disable-shared --enable-static --disable-udev --disable-ltdl
    make -j"$NPROC" && make install

    # --- libxml2 ---
    echo ">> Building libxml2-${LIBXML2_VER}..."
    cd "$ABI_BUILD_DIR"
    extract_if_needed "libxml2-${LIBXML2_VER}.tar.xz" "$ABI_BUILD_DIR/libxml2-${LIBXML2_VER}"
    cd "libxml2-${LIBXML2_VER}"
    rm -rf install && mkdir install
    ./configure \
        --host="$TARGET_HOST" --build="$BUILD_TRIPLE" \
        --prefix="$PWD/install" \
        CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
        --enable-shared --disable-static --without-python --without-zlib --without-lzma
    make -j"$NPROC" && make install

    # --- libjpeg-turbo (CMake, v3.0+에서 autoconf 제거됨) ---
    echo ">> Building libjpeg-turbo-${LIBJPEG_TURBO_VER}..."
    cd "$ABI_BUILD_DIR"
    extract_if_needed "libjpeg-turbo-${LIBJPEG_TURBO_VER}.tar.gz" \
        "$ABI_BUILD_DIR/libjpeg-turbo-${LIBJPEG_TURBO_VER}"
    cd "libjpeg-turbo-${LIBJPEG_TURBO_VER}"
    rm -rf _build && mkdir _build && cd _build
    cmake .. \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$API" \
        -DCMAKE_INSTALL_PREFIX="$ABI_BUILD_DIR/libjpeg-turbo-${LIBJPEG_TURBO_VER}/install" \
        -DCMAKE_BUILD_TYPE=Release \
        -DENABLE_SHARED=OFF \
        -DENABLE_STATIC=ON \
        -DWITH_JPEG8=ON
    cmake --build . -j"$NPROC" && cmake --install .
    cd "$ABI_BUILD_DIR"

    # --- libexif ---
    echo ">> Building libexif-${LIBEXIF_VER}..."
    extract_if_needed "libexif-${LIBEXIF_VER}.tar.gz" "$ABI_BUILD_DIR/libexif-${LIBEXIF_VER}"
    cd "libexif-${LIBEXIF_VER}"
    rm -rf install && mkdir install
    ./configure \
        --host="$TARGET_HOST" --build="$BUILD_TRIPLE" \
        --prefix="$PWD/install" \
        CFLAGS="$CFLAGS" \
        --disable-shared --enable-static
    make -j"$NPROC" && make install

    # --- 통합 deps 디렉토리 ---
    cd "$ABI_BUILD_DIR"
    local DEP_ROOT="$ABI_BUILD_DIR/deps"
    rm -rf "$DEP_ROOT"
    mkdir -p "$DEP_ROOT/include" "$DEP_ROOT/lib"

    for lib in \
        "libtool-${LIBTOOL_VER}" \
        "libusb-${LIBUSB_VER}" \
        "libxml2-${LIBXML2_VER}" \
        "libjpeg-turbo-${LIBJPEG_TURBO_VER}" \
        "libexif-${LIBEXIF_VER}"
    do
        [ -d "$ABI_BUILD_DIR/$lib/install/include" ] && \
            cp -rf "$ABI_BUILD_DIR/$lib/install/include/." "$DEP_ROOT/include/"
        [ -d "$ABI_BUILD_DIR/$lib/install/lib" ] && \
            cp -rf "$ABI_BUILD_DIR/$lib/install/lib/." "$DEP_ROOT/lib/"
    done

    echo "OK: $ABI 의존성 빌드 완료"
    cd "$BUILD_DIR"
}

##############################################
# 7. libgphoto2 빌드
##############################################
build_libgphoto2() {
    local TARGET="$1"
    local ABI="$2"
    local TARGET_HOST="${TARGET}${API}"
    local ABI_BUILD_DIR="$BUILD_DIR/$ABI"
    local DEP_ROOT="$ABI_BUILD_DIR/deps"

    echo ">> libgphoto2 빌드: $ABI ($TARGET_HOST)"

    export CC="$TOOLCHAIN/bin/${TARGET_HOST}-clang"
    export CXX="$TOOLCHAIN/bin/${TARGET_HOST}-clang++"
    export AR="$TOOLCHAIN/bin/llvm-ar"
    export NM="$TOOLCHAIN/bin/llvm-nm"
    export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$TOOLCHAIN/bin/llvm-strip"
    export AS="$TOOLCHAIN/bin/${TARGET_HOST}-clang"
    export LD="$TOOLCHAIN/bin/${TARGET_HOST}-clang"

    export CFLAGS="-fPIE -fPIC -O2 --sysroot=$SYSROOT -I$DEP_ROOT/include -I$DEP_ROOT/include/libusb-1.0 -D_LIBCPP_HAS_NO_OFF_T_FUNCTIONS -w"
    export CXXFLAGS="$CFLAGS"
    # 16KB 정렬: 링크 타임에 PT_LOAD 세그먼트 정렬 설정 (핵심)
    # -L$SYSROOT/usr/lib/${TARGET}/${API}: sysroot 시스템 라이브러리 경로 명시 (공식 스크립트 방식)
    export LDFLAGS="-L$SYSROOT/usr/lib/${TARGET}/$API -L$DEP_ROOT/lib $ALIGN_LDFLAGS"

    export PKG_CONFIG_LIBDIR="$DEP_ROOT/lib/pkgconfig"
    export PKG_CONFIG_PATH="$DEP_ROOT/lib/pkgconfig"
    export lt_cv_deplibs_check_method=pass_all
    # NDK clang은 GNU ld 호환 인터페이스를 사용 - libgphoto2_port subdir configure에도 전파됨
    export lt_cv_prog_gnu_ld=yes

    local SRC_DIR="$ABI_BUILD_DIR/libgphoto2_src"
    rm -rf "$SRC_DIR"
    cp -r "$BUILD_DIR/libgphoto2" "$SRC_DIR"
    cd "$SRC_DIR"

    # 릴리즈 tarball은 configure 포함, git clone 시에만 autoreconf 필요
    [ ! -f "./configure" ] && autoreconf -fi

    local PRFX="$ABI_BUILD_DIR/output"
    rm -rf "$PRFX" && mkdir -p "$PRFX"

    ./configure \
        --host="$TARGET_HOST" \
        --build="$BUILD_TRIPLE" \
        --prefix="$PRFX" \
        --with-sysroot="$SYSROOT" \
        --with-gnu-ld=yes \
        --with-libxml-2.0=no \
        --with-libcurl=no \
        --with-gdlib=no \
        --with-libexif=no \
        --enable-static=no \
        --enable-shared=yes \
        --disable-serial \
        LTDLINCL="-I$DEP_ROOT/include" \
        LIBLTDL="-L$DEP_ROOT/lib -lltdl" \
        || { echo "ERROR: configure 실패 ($ABI)"; exit 1; }

    # libtool 크로스 컴파일 패치
    # 1) archive_cmds가 비어있으면 직접 지정 (with_gnu_ld 오탐 시 안전망)
    # 2) lt_sysroot 클리어: --with-sysroot가 설정되면 libtool이 relink 시
    #    빌드 디렉토리 -L 경로에 sysroot를 프리픽스로 붙여 경로를 오염시킴
    # 3) build_libtool_libs=no → yes: configure가 archive_cmds=''로 오탐하면
    #    shared lib 빌드 자체를 비활성화함; .lo/.o도 pic_object=none으로 컴파일됨
    for lt in libtool libgphoto2_port/libtool; do
        if [ -f "$lt" ]; then
            sed_inplace \
                's/archive_cmds=""/archive_cmds="\\\$CC -shared \\\$pic_flag \\\$libobjs \\\$deplibs \\\$compiler_flags \\\$wl-soname \\\$wl\\\$soname -o \\\$lib"/' \
                "$lt"
            sed_inplace "s|lt_sysroot=$SYSROOT|lt_sysroot=|" "$lt"
            sed_inplace 's/^build_libtool_libs=no/build_libtool_libs=yes/' "$lt"
        fi
    done

    make -j"$NPROC" || { echo "ERROR: make 실패 ($ABI)"; exit 1; }
    # make install 시 libtool이 iolib 리링크 단계에서 libgphoto2_port를 찾을 수 있도록
    # 출력 lib 경로를 LDFLAGS에 추가
    LDFLAGS="$LDFLAGS -L$PRFX/lib" make install || { echo "ERROR: make install 실패 ($ABI)"; exit 1; }

    # 카메라/포트 드라이버 .so를 lib 최상위로 이동 (원본 네이밍 유지)
    for f in "$PRFX"/lib/libgphoto2/*/*.so; do
        [ -e "$f" ] && mv -v "$f" "$PRFX/lib/libgphoto2_camlib_$(basename "$f")"
    done
    for f in "$PRFX"/lib/libgphoto2_port/*/*.so; do
        [ -e "$f" ] && mv -v "$f" "$PRFX/lib/libgphoto2_port_iolib_$(basename "$f")"
    done

    align_shared_objects "$PRFX/lib"

    echo "OK: $ABI 빌드 완료 -> $PRFX"
    cd "$BUILD_DIR"
}

##############################################
# 8. 소스 다운로드 (한 번만)
##############################################
cd "$BUILD_DIR"

# libgphoto2: git clone (기존 디렉토리가 있으면 pull로 최신화)
if [ -d "$BUILD_DIR/libgphoto2/.git" ]; then
    echo ">> libgphoto2 기존 소스 최신화 (git pull)..."
    cd "$BUILD_DIR/libgphoto2"
    git fetch origin
    git checkout "$LIBGPHOTO2_BRANCH"
    git reset --hard "origin/$LIBGPHOTO2_BRANCH"
    cd "$BUILD_DIR"
else
    echo ">> libgphoto2 소스 클론 (branch: $LIBGPHOTO2_BRANCH)..."
    rm -rf "$BUILD_DIR/libgphoto2"
    git clone --depth 1 --branch "$LIBGPHOTO2_BRANCH" "$LIBGPHOTO2_REPO" "$BUILD_DIR/libgphoto2"
fi
echo ">> libgphoto2 HEAD: $(cd "$BUILD_DIR/libgphoto2" && git log --oneline -1)"

# CamCon 커스텀 패치 적용 (로컬 수정 사항 반영)
# 패치 목록:
#   - library.c:  find_child PTP 캐시 우선 탐색 + Nikon GetPartialObjectEx 확대
#   - ptpip.c:    TCP 소켓 최적화
#   - gphoto2-filesys.c: lookup_folder lazy creation + lookup_folder_file 캐시 우선 검색
#                         + append_to_folder 재귀 생성 + gp_filesystem_append dirty 열거 스킵
PATCH_SRC="/Users/ini-k/libgphoto2"
if [ -d "$PATCH_SRC/camlibs/ptp2" ]; then
    echo ">> CamCon 패치 적용 중..."
    cp -v "$PATCH_SRC/camlibs/ptp2/library.c" "$BUILD_DIR/libgphoto2/camlibs/ptp2/library.c"
    cp -v "$PATCH_SRC/camlibs/ptp2/ptpip.c"   "$BUILD_DIR/libgphoto2/camlibs/ptp2/ptpip.c"
    [ -f "$PATCH_SRC/libgphoto2/gphoto2-filesys.c" ] && \
        cp -v "$PATCH_SRC/libgphoto2/gphoto2-filesys.c" "$BUILD_DIR/libgphoto2/libgphoto2/gphoto2-filesys.c"

    # 패치 검증 — 핵심 패치가 누락되면 빌드 중단
    echo ">> 패치 검증 중..."
    patch_ok=true
    grep -q 'cache hit' "$BUILD_DIR/libgphoto2/camlibs/ptp2/library.c" \
        && echo "  ✓ find_child 캐시 우선 탐색" \
        || { echo "  ✗ find_child 패치 누락!"; patch_ok=false; }
    grep -q 'returning NULL for lazy creation' "$BUILD_DIR/libgphoto2/libgphoto2/gphoto2-filesys.c" \
        && echo "  ✓ lookup_folder lazy creation" \
        || { echo "  ✗ lookup_folder 패치 누락!"; patch_ok=false; }
    grep -q 'First check if the file' "$BUILD_DIR/libgphoto2/libgphoto2/gphoto2-filesys.c" \
        && echo "  ✓ lookup_folder_file 캐시 우선 검색" \
        || { echo "  ✗ lookup_folder_file 패치 누락!"; patch_ok=false; }
    grep -q 'Skip full enumeration' "$BUILD_DIR/libgphoto2/libgphoto2/gphoto2-filesys.c" \
        && echo "  ✓ gp_filesystem_append dirty 열거 스킵" \
        || { echo "  ✗ gp_filesystem_append 패치 누락!"; patch_ok=false; }
    grep -q 'skipping ChangeCameraMode' "$BUILD_DIR/libgphoto2/camlibs/ptp2/library.c" \
        && echo "  ✓ PTP/IP capture_preview ChangeCameraMode 건너뜀 (물리 셔터 활성화)" \
        || { echo "  ✗ capture_preview ChangeCameraMode 패치 누락!"; patch_ok=false; }

    if [ "$patch_ok" = false ]; then
        echo ""
        echo "ERROR: 패치 검증 실패. $PATCH_SRC 디렉토리의 패치 파일을 확인하세요."
        exit 1
    fi
    echo ">> 패치 적용 및 검증 완료"
fi

fetch "https://ftp.gnu.org/gnu/libtool/libtool-${LIBTOOL_VER}.tar.gz"
fetch "https://github.com/libusb/libusb/releases/download/v${LIBUSB_VER}/libusb-${LIBUSB_VER}.tar.bz2"
fetch "https://download.gnome.org/sources/libxml2/2.13/libxml2-${LIBXML2_VER}.tar.xz"
fetch "https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/${LIBJPEG_TURBO_VER}/libjpeg-turbo-${LIBJPEG_TURBO_VER}.tar.gz"
fetch "https://github.com/libexif/libexif/releases/download/v${LIBEXIF_VER}/libexif-${LIBEXIF_VER}.tar.gz"

##############################################
# 9. ABI별 빌드 실행
##############################################
for ABI in "${!ABI_TARGETS[@]}"; do
    TARGET="${ABI_TARGETS[$ABI]}"
    echo ""
    echo "=========================================="
    echo "  ABI: $ABI | Target: $TARGET"
    echo "=========================================="
    build_dependencies "$TARGET" "$ABI"
    build_libgphoto2 "$TARGET" "$ABI"
done

##############################################
# 10. 결과 요약
##############################################
echo ""
echo "=========================================="
echo "전체 빌드 완료!"
echo "=========================================="
echo "출력 경로:"
for ABI in "${!ABI_TARGETS[@]}"; do
    echo "  $ABI -> $BUILD_DIR/$ABI/output/lib"
done
echo ""
echo "16KB 페이지 정렬 검증 (Align 값이 0x4000 이상이어야 함):"
for ABI in "${!ABI_TARGETS[@]}"; do
    so=$(find "$BUILD_DIR/$ABI/output/lib" -name "libgphoto2.so*" -not -name "*.la" 2>/dev/null | head -1)
    if [ -n "$so" ]; then
        echo "  [$ABI] readelf -lW $so | grep LOAD"
    fi
done
echo ""
echo "[ Android 앱에서 사용 시 필수 설정 ]"
echo "  1) AndroidManifest.xml:"
echo "       android:extractNativeLibs=\"true\""
echo ""
echo "  2) JNI 초기화 코드 (카메라 open 전에 반드시 실행):"
echo "       // Java에서 USB fd 획득"
echo "       val fd = usbManager.openDevice(device).fileDescriptor"
echo "       // JNI를 통해 전달"
echo "       gp_port_usb_set_sys_device(fd)"
echo ""
echo "  3) 환경변수 설정 (nativeLibraryDir는 getApplicationInfo().nativeLibraryDir):"
echo "       setenv(\"CAMLIBS\",      nativeLibraryDir, 1)"
echo "       setenv(\"IOLIBS\",       nativeLibraryDir, 1)"
echo "       setenv(\"CAMLIBS_PREFIX\", \"libgphoto2_camlib_\", 1)"
echo "       setenv(\"IOLIBS_PREFIX\", \"libgphoto2_port_iolib_\", 1)"
echo "=========================================="
