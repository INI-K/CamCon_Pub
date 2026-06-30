package com.inik.camcon.data.processor

import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * MKL 색감 전송을 위한 커스텀 GPU 필터
 */
class GPUImageMKLColorTransferFilter(
    private val inputStats: Array<FloatArray>,
    private val referenceStats: Array<FloatArray>,
    private val intensity: Float = 0.3f
) : GPUImageFilter(NO_FILTER_VERTEX_SHADER, MKL_FRAGMENT_SHADER) {

    companion object {
        // MKL 색감 전송을 위한 커스텀 프래그먼트 셰이더
        const val MKL_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;

            // MKL 색감 전송 파라미터
            uniform vec3 inputMean;
            uniform vec3 inputStdDev;
            uniform vec3 referenceMean;
            uniform vec3 referenceStdDev;
            uniform float intensity;

            // RGB to Lab 색공간 변환
            vec3 rgbToLab(vec3 rgb) {
                // 감마 보정 제거
                vec3 rgbNorm = vec3(
                    rgb.r > 0.04045 ? pow((rgb.r + 0.055) / 1.055, 2.4) : rgb.r / 12.92,
                    rgb.g > 0.04045 ? pow((rgb.g + 0.055) / 1.055, 2.4) : rgb.g / 12.92,
                    rgb.b > 0.04045 ? pow((rgb.b + 0.055) / 1.055, 2.4) : rgb.b / 12.92
                );

                // RGB to XYZ 변환 (D65 illuminant)
                vec3 xyz = vec3(
                    rgbNorm.r * 0.4124564 + rgbNorm.g * 0.3575761 + rgbNorm.b * 0.1804375,
                    rgbNorm.r * 0.2126729 + rgbNorm.g * 0.7151522 + rgbNorm.b * 0.0721750,
                    rgbNorm.r * 0.0193339 + rgbNorm.g * 0.1191920 + rgbNorm.b * 0.9503041
                );

                // XYZ to Lab 변환
                vec3 xyzNorm = vec3(
                    xyz.x / 0.95047,
                    xyz.y / 1.00000,
                    xyz.z / 1.08883
                );

                vec3 f = vec3(
                    xyzNorm.x > 0.008856 ? pow(xyzNorm.x, 1.0/3.0) : (7.787 * xyzNorm.x + 16.0/116.0),
                    xyzNorm.y > 0.008856 ? pow(xyzNorm.y, 1.0/3.0) : (7.787 * xyzNorm.y + 16.0/116.0),
                    xyzNorm.z > 0.008856 ? pow(xyzNorm.z, 1.0/3.0) : (7.787 * xyzNorm.z + 16.0/116.0)
                );

                return vec3(
                    116.0 * f.y - 16.0,
                    500.0 * (f.x - f.y),
                    200.0 * (f.y - f.z)
                );
            }

            // Lab to RGB 색공간 변환
            vec3 labToRgb(vec3 lab) {
                // Lab to XYZ 변환
                float fy = (lab.x + 16.0) / 116.0;
                float fx = lab.y / 500.0 + fy;
                float fz = fy - lab.z / 200.0;

                vec3 xyzNorm = vec3(
                    pow(fx, 3.0) > 0.008856 ? pow(fx, 3.0) : (fx - 16.0/116.0) / 7.787,
                    pow(fy, 3.0) > 0.008856 ? pow(fy, 3.0) : (fy - 16.0/116.0) / 7.787,
                    pow(fz, 3.0) > 0.008856 ? pow(fz, 3.0) : (fz - 16.0/116.0) / 7.787
                );

                vec3 xyz = vec3(
                    xyzNorm.x * 0.95047,
                    xyzNorm.y * 1.00000,
                    xyzNorm.z * 1.08883
                );

                // XYZ to RGB 변환
                vec3 rgb = vec3(
                    xyz.x * 3.2404542 + xyz.y * -1.5371385 + xyz.z * -0.4985314,
                    xyz.x * -0.9692660 + xyz.y * 1.8760108 + xyz.z * 0.0415560,
                    xyz.x * 0.0556434 + xyz.y * -0.2040259 + xyz.z * 1.0572252
                );

                // 감마 보정 적용
                return vec3(
                    rgb.r > 0.0031308 ? 1.055 * pow(rgb.r, 1.0/2.4) - 0.055 : 12.92 * rgb.r,
                    rgb.g > 0.0031308 ? 1.055 * pow(rgb.g, 1.0/2.4) - 0.055 : 12.92 * rgb.g,
                    rgb.b > 0.0031308 ? 1.055 * pow(rgb.b, 1.0/2.4) - 0.055 : 12.92 * rgb.b
                );
            }

            void main() {
                vec4 color = texture2D(inputImageTexture, textureCoordinate);
                vec3 lab = rgbToLab(color.rgb);

                // MKL 색감 전송 공식 적용
                vec3 transferredLab = vec3(
                    inputStdDev.x > 0.001 ? (lab.x - inputMean.x) / inputStdDev.x * referenceStdDev.x + referenceMean.x : referenceMean.x,
                    inputStdDev.y > 0.001 ? (lab.y - inputMean.y) / inputStdDev.y * referenceStdDev.y + referenceMean.y : referenceMean.y,
                    inputStdDev.z > 0.001 ? (lab.z - inputMean.z) / inputStdDev.z * referenceStdDev.z + referenceMean.z : referenceMean.z
                );

                // 강도 조절 (선형 보간)
                vec3 finalLab = mix(lab, transferredLab, intensity);

                // RGB로 변환하여 출력
                vec3 finalRgb = labToRgb(finalLab);
                gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), color.a);
            }
        """
    }

    private var inputMeanLocation = 0
    private var inputStdDevLocation = 0
    private var referenceMeanLocation = 0
    private var referenceStdDevLocation = 0
    private var intensityLocation = 0

    override fun onInit() {
        super.onInit()
        inputMeanLocation = GLES20.glGetUniformLocation(program, "inputMean")
        inputStdDevLocation = GLES20.glGetUniformLocation(program, "inputStdDev")
        referenceMeanLocation = GLES20.glGetUniformLocation(program, "referenceMean")
        referenceStdDevLocation = GLES20.glGetUniformLocation(program, "referenceStdDev")
        intensityLocation = GLES20.glGetUniformLocation(program, "intensity")
    }

    override fun onInitialized() {
        super.onInitialized()
        updateUniforms()
    }

    private fun updateUniforms() {
        runOnDraw {
            // 입력 이미지 통계 전달
            GLES20.glUniform3f(
                inputMeanLocation,
                inputStats[0][0], inputStats[1][0], inputStats[2][0]
            )
            GLES20.glUniform3f(
                inputStdDevLocation,
                inputStats[0][1], inputStats[1][1], inputStats[2][1]
            )

            // 참조 이미지 통계 전달
            GLES20.glUniform3f(
                referenceMeanLocation,
                referenceStats[0][0], referenceStats[1][0], referenceStats[2][0]
            )
            GLES20.glUniform3f(
                referenceStdDevLocation,
                referenceStats[0][1], referenceStats[1][1], referenceStats[2][1]
            )

            // 강도 전달
            GLES20.glUniform1f(intensityLocation, intensity)
        }
    }
}
