package com.inik.camcon

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JNI 시그니처 패리티 정적 테스트.
 *
 * [CameraNative] 의 `external fun` 반환 타입과, `app/src/main/cpp` 의
 * `JNIEXPORT <ret> JNICALL Java_com_inik_camcon_CameraNative_<name>` 네이티브
 * 진입점 반환 타입이 일치하는지 소스 파싱으로 전수 검증한다.
 *
 * JNI 링킹은 심볼 "이름"만으로 이뤄지므로 반환 타입이 어긋나도 컴파일·링크가
 * 모두 통과한다. 예컨대 Kotlin이 `Int`(jint)를 기대하는데 네이티브가 `jstring`을
 * 돌려주면, 런타임에 로컬 레퍼런스 포인터가 non-zero jint로 잘려 항상 "실패가 아닌
 * 성공"처럼 보이는 등 은밀한 결함이 된다. 이 테스트가 그런 어긋남을 CI에서 잡는다.
 *
 * 판정 규칙: **양쪽에 모두 존재하는 함수(교집합)의 반환 타입 불일치만** 실패로 본다.
 * 한쪽에만 있는 심볼(프리빌트 .so 전용 심볼, 미배선 external 등)은 무시한다.
 * 인자 타입은 이번 범위에서 검증하지 않는다(반환 타입만).
 */
class JniSignatureParityTest {

    /** `external fun name(...)` 의 파싱 결과. */
    private data class KotlinExternal(val name: String, val returnType: String)

    @Test
    fun `Kotlin external 반환 타입과 네이티브 JNIEXPORT 반환 타입이 일치한다`() {
        val moduleRoot = resolveModuleRoot()
        val cppDir = File(moduleRoot, "src/main/cpp")
        val kotlinFile = File(moduleRoot, "src/main/java/com/inik/camcon/CameraNative.kt")

        assertTrue("cpp 디렉터리를 찾지 못함: ${cppDir.absolutePath}", cppDir.isDirectory)
        assertTrue("CameraNative.kt 를 찾지 못함: ${kotlinFile.absolutePath}", kotlinFile.isFile)

        val kotlinExternals = parseKotlinExternals(kotlinFile.readText())
        val nativeReturns = parseNativeReturns(cppDir)

        // 파서 회귀 방어: external 선언 수가 급감하면 파싱이 깨진 것.
        assertTrue(
            "external fun 파싱 개수가 비정상적으로 적음(${kotlinExternals.size}). 파서 확인 필요.",
            kotlinExternals.size >= 150
        )
        assertTrue(
            "네이티브 JNIEXPORT 파싱 개수가 비정상적으로 적음(${nativeReturns.size}). 파서 확인 필요.",
            nativeReturns.size >= 150
        )

        val mismatches = mutableListOf<String>()
        for (ext in kotlinExternals) {
            val nativeRet = nativeReturns[ext.name] ?: continue // 교집합만 검증
            val expectedJni = kotlinReturnToJni(ext.returnType)
            if (expectedJni != nativeRet) {
                mismatches += "  - ${ext.name}: Kotlin '${ext.returnType}'(→$expectedJni) vs 네이티브 '$nativeRet'"
            }
        }

        assertTrue(
            buildString {
                appendLine("JNI 반환 타입 불일치 ${mismatches.size}건 발견:")
                mismatches.forEach { appendLine(it) }
                appendLine(
                    "(파싱: Kotlin external ${kotlinExternals.size}개 / 네이티브 JNIEXPORT ${nativeReturns.size}개)"
                )
            },
            mismatches.isEmpty()
        )
    }

    /**
     * `external fun <name>(<params>)[: <ret>]` 를 전수 파싱한다.
     * 멀티라인 파라미터 목록, 반환 타입 생략(Unit), nullable(`?`), 제네릭 배열을 처리한다.
     */
    private fun parseKotlinExternals(source: String): List<KotlinExternal> {
        val result = mutableListOf<KotlinExternal>()
        val header = Regex("""external\s+fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        for (m in header.findAll(source)) {
            val name = m.groupValues[1]
            val openParen = m.range.last // '(' 위치
            val closeParen = matchingParen(source, openParen) ?: continue
            // 닫는 괄호 뒤의 반환 타입: 공백/개행을 건너뛰고 첫 비공백이 ':' 이면 반환 타입 존재.
            val rest = source.substring(closeParen + 1)
            val ret = Regex("""\A\s*:\s*(\S+)""").find(rest)?.groupValues?.get(1) ?: "Unit"
            result += KotlinExternal(name, ret)
        }
        return result
    }

    /** [openIndex] 의 '(' 에 대응하는 ')' 인덱스를 괄호 균형으로 찾는다. 실패 시 null. */
    private fun matchingParen(source: String, openIndex: Int): Int? {
        var depth = 0
        var i = openIndex
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    /**
     * cpp/c 소스에서 `JNIEXPORT <ret> JNICALL Java_com_inik_camcon_CameraNative_<name>(`
     * 패턴을 전수 파싱한다. JNIEXPORT/JNICALL/함수명이 개행으로 나뉘어도 동작하도록
     * 파일 전체를 한 문자열로 읽어 개행 포함 매칭한다.
     */
    private fun parseNativeReturns(cppDir: File): Map<String, String> {
        val pattern = Regex(
            """JNIEXPORT\s+(\w+)\s+JNICALL\s+Java_com_inik_camcon_CameraNative_(\w+)\s*\(""",
            RegexOption.DOT_MATCHES_ALL
        )
        val map = mutableMapOf<String, String>()
        cppDir.walkTopDown()
            .filter { it.isFile && (it.extension == "cpp" || it.extension == "c") }
            .forEach { file ->
                val text = file.readText()
                for (m in pattern.findAll(text)) {
                    val ret = m.groupValues[1]
                    val name = m.groupValues[2]
                    map[name] = ret
                }
            }
        return map
    }

    /** Kotlin 반환 타입을 대응하는 JNI 반환 타입 심볼로 정규화한다. */
    private fun kotlinReturnToJni(rawType: String): String {
        val t = rawType.trim().removeSuffix("?").trim()
        return when {
            t == "Int" -> "jint"
            t == "Boolean" -> "jboolean"
            t == "Long" -> "jlong"
            t == "Float" -> "jfloat"
            t == "Double" -> "jdouble"
            t == "Short" -> "jshort"
            t == "Byte" -> "jbyte"
            t == "Char" -> "jchar"
            t == "String" -> "jstring"
            t == "Unit" || t.isEmpty() -> "void"
            t == "ByteArray" -> "jbyteArray"
            t == "IntArray" -> "jintArray"
            t == "LongArray" -> "jlongArray"
            t == "FloatArray" -> "jfloatArray"
            t == "DoubleArray" -> "jdoubleArray"
            t == "ShortArray" -> "jshortArray"
            t == "BooleanArray" -> "jbooleanArray"
            t == "CharArray" -> "jcharArray"
            t.startsWith("Array<") -> "jobjectArray"
            else -> "jobject" // 그 외 객체 반환 타입은 보수적으로 jobject 로 취급
        }
    }

    /**
     * 테스트 실행 워킹 디렉터리(루트 또는 :app 모듈)에서 위로 걸어 올라가며
     * `src/main/cpp` 를 포함하는 모듈 루트를 찾는다.
     */
    private fun resolveModuleRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var dir: File? = start
        while (dir != null) {
            // 워킹 디렉터리가 :app 모듈인 경우
            if (File(dir, "src/main/cpp").isDirectory) return dir
            // 워킹 디렉터리가 리포 루트인 경우
            val appModule = File(dir, "app")
            if (File(appModule, "src/main/cpp").isDirectory) return appModule
            dir = dir.parentFile
        }
        throw IllegalStateException(
            "src/main/cpp 를 포함하는 모듈 루트를 찾지 못함 (from ${start.absolutePath})"
        )
    }
}
