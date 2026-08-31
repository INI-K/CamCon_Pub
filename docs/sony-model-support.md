# 소니 기종별 원격 제어 지원 현황

소니 공식 문서에서 기계적으로 추출한 표다. 홈페이지 게시 등 정리 작업의 원자료로 쓴다.

## 출처

- `Camera Control PTP 3 Reference` (2026-04-17판) — Compatibility 장(85쪽~)
- 같은 배포본의 `README.pdf` — 기종별 USB/IP 연결 표

## 추출 방법과 신뢰도

PDF 표의 머리글이 세로로 회전돼 있어 일반 텍스트 추출로는 열이 어긋난다. 실제로 처음
시도한 레이아웃 추출은 `0x923x` 행의 열을 통째로 밀어 잘못된 결과를 냈다.

그래서 `pdftotext -bbox` 로 단어별 좌표를 뽑아, 체크 표시의 x 좌표를 회전된 머리글의
x 좌표에 맞추는 방식으로 다시 읽었다. 검증은 두 가지로 했다.

1. 전 기종이 지원해야 하는 행(`0x1001`·`0x1002`·`0x1003`)이 35/35 로 나오는지
2. a7m5 실기 DeviceInfo 덤프가 광고하는 오퍼레이션과 표가 일치하는지

**단, 표와 실기가 어긋나는 사례가 하나 있다.** a7m5 는 `0x100A GetThumb` 를 지원한다고
광고하지만 공식 표는 미지원으로 적고 있고, 실제 동작은 표 쪽이 맞았다(디코딩 불가능한
117KB 반환, 2026-08-30 실측). **기종 판정은 카메라 광고가 아니라 이 표를 근거로 한다.**

## 열의 의미

| 열 | 오퍼레이션 | 뜻 |
|---|---|---|
| 저장소 | `0x1004` GetStorageIDs | 메모리 카드를 저장소로 셀 수 있는가 |
| 카드조회 | `0x****` SDIO_SetContentsTransferMode | 콘텐츠 전송 모드로 카드를 볼 수 있는가 |
| 동시조회 | `0x****` SDIO_GetContentInfoList | 촬영·라이브뷰를 유지한 채 카드를 볼 수 있는가 |
| 썸네일 | `0x100A` GetThumb | 표준 방식으로 썸네일을 받을 수 있는가 |
| 설명파일 | `0x****` SDIO_GetDeviceDescriptionFile | 능력 XML 을 PTP 로 받을 수 있는가 |
| 전송상태 | `0x****` Content Transfer Enable Status | 전송 모드 상태를 속성으로 읽을 수 있는가 |

연결 열은 README 기준이다. `무선만` 인 7종은 캠코더·팬틸트 기종이며, README 각주 *2 가
"콘텐츠 전송 모드를 쓸 수 없다"고 명시한다. 이 기종들은 PTP 가 아니라 HTTP 로
`MEDIAPRO.XML` 을 받아 가는 별도 경로를 쓴다.

## 매트릭스

| 기종 | 연결 | 저장소 | 카드조회 | 동시조회 | 썸네일 | 설명파일 | 전송상태 |
|---|---|---|---|---|---|---|---|
| ILCE-1M2 | USB·무선 | O | O | O | O | O | O |
| ILCE-1 | USB·무선 | O | O | O | O | O | O |
| ILCE-9M3 | USB·무선 | O | O | O | O | O | O |
| ILCE-9M2 | USB·무선 | — | — | — | — | — | — |
| ILCE-7RM6 | USB·무선 | O | O | O | — | O | — |
| ILCE-7RM5 | USB·무선 | O | O | O | O | O | O |
| ILCE-7RM4A | USB·무선 | O | O | — | O | — | O |
| ILCE-7RM4 | USB·무선 | — | — | — | — | — | — |
| ILCE-7M5 | USB·무선 | O | O | O | — | O | — |
| ILCE-7M4 | USB·무선 | O | O | O | O | O | O |
| ILCE-7SM3 | USB·무선 | O | O | O | O | O | O |
| ILCE-7CM2 | USB·무선 | O | O | O | O | O | O |
| ILCE-7CR | USB·무선 | O | O | O | O | O | O |
| ILCE-7C | USB·무선 | O | O | — | O | — | O |
| ILCE-6700 | USB·무선 | O | O | O | O | O | O |
| ILX-LR1 | USB·무선 | O | O | O | O | O | O |
| ILME-FX3(A) | USB·무선 | O | O | O | O | O | O |
| ILME-FX30 | USB·무선 | O | O | O | O | O | O |
| ILME-FX2 | USB·무선 | O | O | O | O | O | O |
| ZV-E1 | USB·무선 | O | O | O | O | O | O |
| ZV-E10 | USB·무선 | — | — | — | — | — | — |
| ZV-E10M2 | USB·무선 | O | O | — | O | — | O |
| ZV-1M2 | USB만 | O | O | — | O | — | O |
| ZV-1(A) | USB만 | — | — | — | — | — | — |
| ZV-1F | USB만 | O | O | — | O | — | O |
| DSC-RX1RM3 | USB·무선 | O | O | O | — | O | — |
| DSC-RX100M7(A) | USB만 | — | — | — | — | — | — |
| DSC-RX0M2 | USB만 | O | O | — | O | — | O |
| ILME-FX6 | 무선만 | — | — | — | — | — | — |
| MPC-2610 | 무선만 | — | — | — | — | — | — |
| ILME-FR7 | 무선만 | — | — | — | — | — | — |
| BRC-AM7 | 무선만 | — | — | — | — | — | — |
| PXW-Z300 | 무선만 | — | — | — | — | — | — |
| PXW-Z200 | 무선만 | — | — | — | — | — | — |
| HXR-NX800 | 무선만 | — | — | — | — | — | — |
## 읽어 둘 점

**2025년 신형 3종(ILCE-7RM6 · ILCE-7M5 · DSC-RX1RM3)은 구형 방식을 버렸다.** 썸네일
(`0x100A`)과 전송 상태(`0x****`)를 지원하지 않는 대신 신형 콘텐츠 API(`0x****`~`0x****`)를
받았다. 이 기종에서 썸네일을 보려면 신형 API 를 써야 한다.

**SSH 는 기종 표에 없다.** 명세가 "카메라 호환성은 DigitalImagingDesc.xml 을 보라"고만
적는다. 즉 고정된 기종 목록이 아니라 연결할 때 XML 로 판정할 값이다. 자세한 것은
아래 절에 적었다.

## SSH 와 능력 XML

XML 의 판정 태그는 넷이다.

| 태그 | 뜻 |
|---|---|
| `X_SSH_Support` | `Enable` 이면 **SSH 연결이 필수**다. 이때 15740 직접 접속은 막힌다 |
| `X_PTP_PairingNecessity` | `Necessary` 면 카메라에서 페어링 승인이 필요하다 |
| `X_PTP_MediaServerSupport` | `Enable` 이면 콘텐츠 전송 모드(모드 1)를 지원한다 |
| `X_PTP_ContentsTransferSupport` | `Enable` 이면 촬영과 전송을 함께 하는 모드(모드 2)를 지원한다 |

마지막 태그는 명세의 태그 설명 목록에는 없고 Tips 장에만 나온다. **신형에서 추가된
태그**로 보인다. 실제로 명세가 예시로 든 ILCE-1 의 XML 에는 없고, a7m5 실측 XML 에는 있다.

    ILCE-1 (명세 예시)   X_ServerVersion 3.01 · MediaServer Enable · SSH Disable
    ILCE-7M5 (실측)      X_ServerVersion 4.00 · MediaServer Enable · ContentsTransfer Enable · SSH Enable

XML 을 얻는 길은 두 가지다.

1. **UPnP 발견** — 카메라의 `dd.xml` 에서 SCPDURL 을 얻어 `DigitalImagingDesc.xml` 을 받는다.
   HTTP 포트는 **64321** 이다(80 이 아니다). 카메라의 원격 촬영 설정이 켜져 있어야 응답한다.
2. **PTP 명령** — `SDIO_GetDeviceDescriptionFile`(`0x****`). USB·무선 모두에서 쓸 수 있고
   `SDIO_OpenSession` 보다 먼저 실행할 수 있다. 다만 **위 표의 `설명파일` 열이 O 인 17종만**
   가능하다.

즉 구형 기종에서는 1번 길밖에 없다.

## SSH 연결 방법 (명세 규정)

    ssh -c aes128-ctr -N -L 15740:localhost:15740 <사용자>@<카메라IP>

- 암호는 `aes128-ctr` 만 지원한다
- 원격 명령을 실행해서는 안 된다
- 사용자·비밀번호는 카메라의 네트워크 설정(액세스 인증)에서 정한다
- 접속 시 지문을 카메라 화면의 값과 대조해야 한다
- 팬틸트 기종은 `네트워크 > SSH > SSH 설정` 을 직접 켜야 한다

**로컬 포트는 반드시 15740 을 쓴다.** libgphoto2 는 이벤트 채널 포트를 파싱 전에 15740 으로
정해 두고 명령 채널 포트만 갱신하므로, 다른 번호를 쓰면 이벤트 채널이 엉뚱한 곳으로 간다
(2026-08-29 실측으로 확인).

## SSH 필수 기종 (CrSDK 공식 표)

출처: CrSDK v2.02.00 `html/other/compatibility.html` — "Supporting physical layer" 표.
각주 원문: "*1: Must be used SSH authentication."

**SSH 인증 필수 (*1 표기, 9종):** ILCE-7RM6 · ILCE-7M5 · MPC-2610 · ILME-FX6 ·
ILME-FR7 · BRC-AM7 · PXW-Z300 · PXW-Z200 · HXR-NX800

나머지 기종은 SSH 불필요(15740 직결 또는 페어링). 주의할 반례 둘:

- **DSC-RX1RM3** — GetThumb 를 버린 신형 3종에 들지만 SSH 는 요구하지 않는다.
  "신세대 = SSH" 일반화는 성립하지 않는다.
- **ILCE-7M4** — 신형 콘텐츠 API 를 지원하지만 SSH 불필요.

따라서 앱의 판정은 이 목록이 아니라 `X_SSH_Support`(XML) 동적 판정을 유지한다.
이 목록은 구매·검증 계획과 홈페이지 안내용 참고 자료다.

## 연결 방식별 모드 지원 (CrSDK "Supporting physical layer")

같은 표가 R(원격제어)·C(카드조회=모드1)·T(촬영하며 조회=모드2)를 USB/유선/무선별로
구분한다. CamCon 에 중요한 행만 발췌:

| 기종 | USB | 유선 LAN | 무선 |
|---|---|---|---|
| ILCE-7M5 | R·C·T | — | R·C·T |
| ILCE-7M4 | R·C·T | — | R·C·T |
| ILCE-7RM6 | R·C·T | — | R·C·T |
| DSC-RX1RM3 | R·C·T | — | R·C·T |
| ILCE-7C | R·C | — | 불가 |
| ILCE-7RM4A | R·C | — | 불가 |
| ILCE-9M2 | R | R | 불가 |
| ZV-E10M2 | R·C | — | R·C |
| ZV-E1 | R·C·T | — | R·C·T |

**a7M5·a7M4 모두 무선에서 모드 2(T)까지 공식 지원** — `0x****` 썸네일 경로가 Wi-Fi 에서
막히지 않는다는 공식 근거다. CrSDK 연결 문서(op_connect_a_camera)도 PTP 명세와 동일하게
"모드 전환은 연결 중 불가, 끊고 재연결" 을 규정하며, SSH 는 `GetFingerprint()` 로 지문을
받아 사용자 대조를 요구한다(CamCon 의 지문 대조 필수 정책과 일치).
