#!/opt/homebrew/bin/bash
# ptp2 camlib만 incremental 재빌드 → jniLibs 반영.
# build_libgphoto2_android.sh와 달리 git reset / libgphoto2_src 덮어쓰기를 하지 않아
# libgphoto2_src 안의 0x9010 + 드레인 패치를 보존한다.
set -euo pipefail

BASE=/Users/ini-k/CamCon
ABI=arm64-v8a
API=29
TARGET=aarch64-linux-android
TH=${TARGET}${API}

NDKROOT="$HOME/Library/Android/sdk/ndk"
NDK="$NDKROOT/$(ls -1 "$NDKROOT" | sort -t. -k1,1rn -k2,2rn | head -1)"
HOST=darwin-arm64
[ -x "$NDK/toolchains/llvm/prebuilt/$HOST/bin/${TH}-clang" ] || HOST=darwin-x86_64
TC="$NDK/toolchains/llvm/prebuilt/$HOST"
SYSROOT="$TC/sysroot"
export PATH="$TC/bin:$PATH"

ABI_DIR="$BASE/gphoto-build_16k/$ABI"
DEP="$ABI_DIR/deps"
SRC="$ABI_DIR/libgphoto2_src"
PRFX="$ABI_DIR/output"

export CC="$TC/bin/${TH}-clang"   CXX="$TC/bin/${TH}-clang++"
export AR="$TC/bin/llvm-ar"       NM="$TC/bin/llvm-nm"
export RANLIB="$TC/bin/llvm-ranlib" STRIP="$TC/bin/llvm-strip"
export AS="$TC/bin/${TH}-clang"   LD="$TC/bin/${TH}-clang"
export CFLAGS="-fPIE -fPIC -O2 --sysroot=$SYSROOT -I$DEP/include -I$DEP/include/libusb-1.0 -D_LIBCPP_HAS_NO_OFF_T_FUNCTIONS -w"
export CXXFLAGS="$CFLAGS"
ALIGN="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
export LDFLAGS="-L$SYSROOT/usr/lib/${TARGET}/$API -L$DEP/lib $ALIGN"
export PKG_CONFIG_LIBDIR="$DEP/lib/pkgconfig"
export PKG_CONFIG_PATH="$DEP/lib/pkgconfig"
export lt_cv_deplibs_check_method=pass_all
export lt_cv_prog_gnu_ld=yes

echo ">> NDK: $NDK ($HOST)"
echo ">> SRC: $SRC"
cd "$SRC"

echo ">> make (incremental)"
make -j"$(sysctl -n hw.logicalcpu)"

echo ">> make install"
LDFLAGS="$LDFLAGS -L$PRFX/lib" make install

# camlib .so 를 prefix 네이밍으로 정리 (build script와 동일 규약)
for f in "$PRFX"/lib/libgphoto2/*/*.so; do
    [ -e "$f" ] && mv -f "$f" "$PRFX/lib/libgphoto2_camlib_$(basename "$f")"
done

SO="$PRFX/lib/libgphoto2_camlib_ptp2.so"
[ -f "$SO" ] || { echo "ERROR: $SO 없음 (빌드 실패)"; exit 1; }

echo ">> 0x9010 패치 문자열 확인:"
strings "$SO" | grep -E "CamCon AdvancedTransfer" || echo "  (경고: CamCon 문자열 없음)"

DST="$BASE/app/src/main/jniLibs/$ABI/libgphoto2_camlib_ptp2.so"
cp -f "$SO" "$DST"
"$STRIP" --strip-unneeded "$DST"

echo ">> DONE → $DST"
ls -l "$DST"
