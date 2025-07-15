#!/bin/bash
set -e

# Android 빌드 환경 설정
export ANDROID_NDK_ROOT=/opt/android-ndk
export TOOLCHAIN=$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64
export AR=$TOOLCHAIN/bin/llvm-ar
export CC=$TOOLCHAIN/bin/aarch64-linux-android29-clang
export CXX=$TOOLCHAIN/bin/aarch64-linux-android29-clang++
export STRIP=$TOOLCHAIN/bin/llvm-strip
export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
export NM=$TOOLCHAIN/bin/llvm-nm

# 빌드 디렉토리 설정
BUILD_DIR=/src/build
OUTPUT_DIR=/src/output
mkdir -p $BUILD_DIR $OUTPUT_DIR

# INI-K의 libgphoto2 포크 클론
cd /src
if [ ! -d "libgphoto2" ]; then
    git clone https://github.com/INI-K/libgphoto2.git
fi

cd libgphoto2

# 의존성 라이브러리 다운로드 및 빌드
echo "=== 의존성 라이브러리 빌드 ==="

# libusb (Android용)
if [ ! -f "$OUTPUT_DIR/lib/libusb-1.0.a" ]; then
    echo "Building libusb..."
    cd /src
    git clone https://github.com/libusb/libusb.git
    cd libusb
    ./autogen.sh
    ./configure --host=aarch64-linux-android \
                --prefix=$OUTPUT_DIR \
                --disable-shared \
                --enable-static \
                --disable-udev
    make -j4
    make install
fi

# libjpeg-turbo (Android용)
if [ ! -f "$OUTPUT_DIR/lib/libjpeg.a" ]; then
    echo "Building libjpeg-turbo..."
    cd /src
    git clone https://github.com/libjpeg-turbo/libjpeg-turbo.git
    cd libjpeg-turbo
    mkdir -p build && cd build
    cmake .. \
        -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-29 \
        -DCMAKE_INSTALL_PREFIX=$OUTPUT_DIR \
        -DENABLE_SHARED=OFF \
        -DENABLE_STATIC=ON
    make -j4
    make install
fi

# libexif (Android용)
if [ ! -f "$OUTPUT_DIR/lib/libexif.a" ]; then
    echo "Building libexif..."
    cd /src
    git clone https://github.com/libexif/libexif.git
    cd libexif
    autoreconf -fiv
    ./configure --host=aarch64-linux-android \
                --prefix=$OUTPUT_DIR \
                --disable-shared \
                --enable-static
    make -j4
    make install
fi

# libgphoto2 빌드
echo "=== libgphoto2 빌드 ==="
cd /src/libgphoto2

# 자동 생성 도구 실행
autoreconf -fiv

# Android용 크로스 컴파일 설정
./configure \
    --host=aarch64-linux-android \
    --prefix=$OUTPUT_DIR \
    --with-libusb=yes \
    --with-libusb-prefix=$OUTPUT_DIR \
    --with-jpeg-prefix=$OUTPUT_DIR \
    --with-libexif=yes \
    --with-libexif-prefix=$OUTPUT_DIR \
    --disable-shared \
    --enable-static \
    --disable-nls \
    --disable-rpath \
    --without-bonjour \
    --without-hal \
    --without-hotplug \
    --without-libxml2 \
    --without-libcurl \
    --without-gdlib \
    --without-aa \
    --without-jpeg \
    --without-libtool-lock \
    CFLAGS="-I$OUTPUT_DIR/include -fPIC" \
    LDFLAGS="-L$OUTPUT_DIR/lib" \
    PKG_CONFIG_PATH="$OUTPUT_DIR/lib/pkgconfig"

# 빌드 실행
make -j4

# 설치
make install

# 결과 확인
echo "=== 빌드 완료 ==="
echo "Output directory: $OUTPUT_DIR"
ls -la $OUTPUT_DIR/lib/libgphoto2*
ls -la $OUTPUT_DIR/lib/libgphoto2_port*

# Android 프로젝트에 복사할 파일들을 패키징
echo "=== Android 프로젝트용 파일 패키징 ==="
cd $OUTPUT_DIR
tar -czf libgphoto2-android-arm64.tar.gz \
    lib/libgphoto2.so \
    lib/libgphoto2_port.so \
    lib/libgphoto2/ \
    lib/libgphoto2_port/ \
    include/

echo "패키징 완료: libgphoto2-android-arm64.tar.gz"