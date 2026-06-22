package com.inik.camcon.data.processor

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * 색수차(Chromatic Aberration) 커스텀 셰이더 필터.
 *
 * 화면 중심에서 바깥으로 갈수록 R/B 채널을 반대 방향으로 미세 이동시켜 렌즈 색수차 룩을 흉내낸다.
 * [strength] 0 = 없음, 1 = 최대 오프셋. G 채널은 고정해 기준으로 삼는다.
 */
class FilmChromaticAberrationFilter(strength: Float = 0f) :
    GPUImageFilter(NO_FILTER_VERTEX_SHADER, FRAGMENT_SHADER) {

    private var strengthLocation = 0
    private var strength: Float = strength.coerceIn(0f, 1f)

    override fun onInit() {
        super.onInit()
        strengthLocation = GLES20.glGetUniformLocation(program, "caStrength")
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
        // 강도 1.0 에서의 최대 채널 오프셋(텍스처 좌표 단위, 즉 화면 비율). 0.006 ≈ 1280px 에서 약 7.7px.
        private const val MAX_OFFSET = 0.006f

        private val FRAGMENT_SHADER = """
            precision highp float;
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform highp float caStrength;

            void main() {
                if (caStrength <= 0.0) {
                    gl_FragColor = texture2D(inputImageTexture, textureCoordinate);
                    return;
                }
                highp vec2 center = vec2(0.5, 0.5);
                highp vec2 dir = textureCoordinate - center;
                highp float dist = length(dir);
                highp vec2 offset = dir * dist * caStrength * $MAX_OFFSET * 2.0;
                lowp float r = texture2D(inputImageTexture, textureCoordinate + offset).r;
                lowp float g = texture2D(inputImageTexture, textureCoordinate).g;
                lowp float b = texture2D(inputImageTexture, textureCoordinate - offset).b;
                lowp float a = texture2D(inputImageTexture, textureCoordinate).a;
                gl_FragColor = vec4(r, g, b, a);
            }
        """.trimIndent()
    }
}
