package com.inik.camcon.data.processor

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * 필름 그레인(필름 입자) 커스텀 셰이더 필터.
 *
 * 픽셀 좌표를 시드로 하는 의사난수 노이즈를 휘도 가중치로 합성한다(섀도/하이라이트보다
 * 미드톤에서 더 도드라지게). [strength] 0 = 노이즈 없음, 1 = 최대.
 *
 * 셰이더 hash 노이즈는 well-known `fract(sin(dot)*c)` 기법(GPU 결정적, 텍스처 불필요).
 */
class FilmGrainFilter(strength: Float = 0f) : GPUImageFilter(NO_FILTER_VERTEX_SHADER, FRAGMENT_SHADER) {

    private var strengthLocation = 0
    private var strength: Float = strength.coerceIn(0f, 1f)

    override fun onInit() {
        super.onInit()
        strengthLocation = GLES20.glGetUniformLocation(program, "grainStrength")
    }

    override fun onInitialized() {
        super.onInitialized()
        setStrength(strength)
    }

    fun setStrength(value: Float) {
        strength = value.coerceIn(0f, 1f)
        setFloat(strengthLocation, strength)
    }

    companion object {
        // 강도 1.0 에서의 최대 노이즈 진폭(0..1 색 범위 대비). 과하지 않게 0.12 로 시작(설계 §12: 1차 단순 구현).
        private const val MAX_AMPLITUDE = 0.12f

        // 그레인 입자 밀도(이미지 가로/세로당 셀 수). 실제 픽셀 해상도 대신 고정값을 써서
        // 프리뷰(다운스케일)와 내보내기(원본)에서 입자 크기가 동일하게 유지된다(preview==export 정합).
        private const val GRAIN_CELLS = 1400f

        private val FRAGMENT_SHADER = """
            precision highp float;
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform highp float grainStrength;

            highp float hash(highp vec2 p) {
                return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
            }

            void main() {
                lowp vec4 color = texture2D(inputImageTexture, textureCoordinate);
                if (grainStrength <= 0.0) {
                    gl_FragColor = color;
                    return;
                }
                highp vec2 pixel = textureCoordinate * $GRAIN_CELLS;
                highp float n = hash(pixel) - 0.5;
                // 미드톤에서 더 강하게: 휘도 0/1 부근은 노이즈 약화
                highp float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                highp float midWeight = 1.0 - abs(luma - 0.5) * 2.0;
                highp float amp = grainStrength * $MAX_AMPLITUDE * (0.5 + 0.5 * midWeight);
                gl_FragColor = vec4(clamp(color.rgb + n * amp, 0.0, 1.0), color.a);
            }
        """.trimIndent()
    }
}
