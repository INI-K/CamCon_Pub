# 니콘 기종별 원격 제어 지원 현황

니콘 공식 MTP 명세에서 기계적으로 추출한 표다. 소니 쪽 정리본(`sony-model-support.md`)과
같은 용도로, 홈페이지 게시·기종 지원 판단의 원자료로 쓴다.

## 출처

- `S-SDKZ-200BF-ALLIN/Command/English/*.pdf` — Z 시리즈 15기종의 개별 USB MTP 명세
- `S-SDKD5-011BF-ALLIN/Command/English/D5UsbMtpE_02.pdf` — DSLR 세대 D5 1기종

D5 배포본에도 `Command/English/` 가 있어 대상에 포함했다. 다만 이 배포본에 들어 있는 기종은
D5 하나뿐이므로, 이 문서가 다루는 DSLR 세대는 D5 로 한정된다. **합계 16기종이다.**

## 추출 방법과 신뢰도

각 PDF 는 6.2절 첫머리에 "The OperationCodes supported by the camera are shown below."
라는 문장과 함께 지원 오퍼레이션 요약 표를 싣는다. 표의 한 행은
`오퍼레이션 코드 · 이름 · 참조 절 · 라이브뷰 중 실행 가부 3열` 로 구성된다. 소니 명세와
달리 머리글이 회전돼 있지 않아 `pdftotext -layout` 만으로 열이 어긋나지 않았고, 좌표 기반
추출(`-bbox`)로 전환할 필요가 없었다.

검증은 두 가지로 했고 둘 다 통과했다.

1. **필수 명령 검사** — 전 기종이 지원해야 하는 `0x1001`·`0x1002`·`0x1003` 이
   각각 **16/16** 으로 나왔다.
2. **독립 교차 검증** — 요약 표에서 뽑은 코드 집합과, 문서 뒷부분의 개별 상세 절이
   선언하는 `Operation Code 0xXXXX` 집합을 대조했다. 16기종 중 11기종이 완전히 일치했고,
   나머지 5기종에서 어긋난 코드는 `0x**** PowerZoomDrive`·`0x**** GetManualSettingLensData2`
   둘뿐이었다. 이 둘은 상세 절이 실제로 존재하는데 `Operation Code` 라벨과 코드값이 서로
   다른 줄로 나뉘어 정규식에 걸리지 않은 형식 문제이며, 데이터 불일치가 아니다.

CamCon 실기 검증이 끝난 Z 6·Z 8 에 대한 교차 확인도 통과했다. 두 기종 모두
`0x**** GetLiveViewImageEx`·`0x**** GetPartialObjectEx`·`0x**** GetPartialObjectHighSpeed`
를 지원한다고 표에 적혀 있고, 기존 실측 결론과도 어긋나지 않는다. 특히 다음 두 가지가
과거 실기 조사 결과와 정확히 맞아떨어져 추출의 신뢰도를 뒷받침한다.

- `0x**** GetObjectsMetaData` 가 Z 6 에는 없다 — "Z 6 는 0x**** 미지원" 이라는 기존 실측과 일치
- `0x**** ChangeApplicationMode` 가 Z 5·Z 6·Z 7·Z 50 에 없다 — "1세대는 앱 모드 미지원" 과 일치

### 이 명세로는 알 수 없는 것

**이 문서들은 전부 USB MTP 명세다.** 그래서 CamCon 이 무선 경로에서 쓰는 다음 네 개의
오퍼레이션은 16기종 어느 문서에도 나오지 않는다. **문서에 없다는 사실을 미지원 근거로
삼아서는 안 된다.**

| 코드 | 이름 | CamCon 용도 |
|---|---|---|
| `0x****` | [redacted] | 무선 전송큐 pull(패치 0001·0008) — Z 6 실기에서 동작 확인됨 |
| `0x****` | (미문서) CamconGetObjectSummary | 잠금 세션 실명·크기 조회(패치 0042) — Z 6 실기에서 30/30 성공 |
| `0x****` | StaModeInit | STA 접속 승인(패치 0006) |
| `0x****` | StaApprove | STA 접속 승인(패치 0006) |

`0x****` 은 애초에 니콘이 어느 명세에도 공개하지 않은 코드이고, 나머지 셋은 무선 전용
경로라 USB 명세의 사정권 밖이다.

## 열의 의미 (CamCon 기능 대응)

### 전 기종 공통이라 표에서 뺀 핵심 명령

아래 명령들은 16기종 전부가 지원하므로 매트릭스에 열로 넣지 않았다. CamCon 의 핵심 기능
대부분이 여기에 걸린다는 뜻이라, 오히려 좋은 소식이다.

| 코드 | 이름 | CamCon 기능 |
|---|---|---|
| `0x1004` | GetStorageIDs | 카드 존재 판정 — 앱 셔터의 카드 라우팅 게이트(패치 0031·0034) |
| `0x1007`·`0x1008`·`0x1009` | GetObjectHandles·GetObjectInfo·GetObject | 카드 브라우징과 다운로드의 표준 경로 |
| `0x100a` | GetThumb | 갤러리 썸네일 표준 경로 |
| `0x101b` | GetPartialObject | 부분 다운로드 |
| `0x****`·`0x****` | InitiateCaptureRecInSdram·AfAndCaptureRecInSdram | SDRAM 캡처 |
| `0x****` | GetLargeThumb | 대형 썸네일 — 실패 시 표준 폴백(패치 0022·0043) |
| `0x****`·`0x****` | GetEvent·GetEventEx | 이벤트 폴 — 촬영물 수신 감지 |
| `0x****` | DeviceReady | 명령 완료 대기 |
| `0x****`·`0x****`·`0x****` | StartLiveView·EndLiveView·GetLiveViewImage | 라이브뷰 기본 경로 |
| `0x****`·`0x****`·`0x****`·`0x****` | AfDrive·MfDrive·ChangeAfArea·AfDriveCancel | 초점 제어·터치 AF |
| `0x****` | InitiateCaptureRecInMedia | 카드 캡처 — Wi-Fi 앱 셔터의 기본 경로(패치 0029) |
| `0x****` | GetFhdPicture | FHD 미리보기 폴백 — 카드 객체가 0x200F 로 막힐 때(패치 0014) |
| `0x****` | GetPartialObjectHighSpeed | 갤러리 고속 다운로드(패치 0011) |
| `0x****` | GetObjectPropList | MTP 프로퍼티 실명 복원(패치 0037) |

`0x****` 가 전 기종에 있다는 점은 특히 눈여겨볼 만하다. 다만 Z 6 실기에서는 **광고는 되어도
무선 잠금 세션에서 실행이 0x200F 로 거부됐다**(패치 0042 머리말). 즉 이 표는 "카메라가
광고하는 능력"이지 "특정 세션에서 실제로 실행되는 능력"이 아니다.

### 매트릭스에 넣은 열

| 열 | 오퍼레이션 | 뜻 |
|---|---|---|
| 등재 | — | libgphoto2 2.5.34 기종 카탈로그(`library.c`)에 VID/PID 가 등록돼 있는가 |
| 연결경로 | `0x****` ConnectionPath | 명세가 정의하는 PTP 연결 경로 — USB·WT(무선 송신기 어댑터)·Wi-Fi(내장)·LAN(유선) |
| LV버전 | `0x****` 데이터셋 | 확장 라이브뷰 프레임 헤더의 Major version |
| LV확장 | `0x****` GetLiveViewImageEx | AF 영역 오버레이가 실린 확장 라이브뷰(패치 0013·0036) |
| 부분Ex | `0x****` GetPartialObjectEx | 64비트 부분 읽기 — EXIF 썸네일 추출의 전제(패치 0015·0017) |
| 크기 | `0x****` GetObjectSize | 객체 크기 조회 |
| 메타 | `0x****` GetObjectsMetaData | 객체 메타데이터 일괄 조회 |
| 앱모드 | `0x****` ChangeApplicationMode | 애플리케이션 모드 전환 — 미리보기 탭 진입 성능에 관여 |
| 벤더구/벤더신 | `0x****` GetVendorPropCodes / `0x****` GetVendorCodes | 벤더 확장 코드 목록 조회 — 세대별로 코드가 갈린다 |
| 속성쓰기 | `0x****` SetObjectPropValue | MTP 객체 프로퍼티 쓰기 |
| 업로드 | `0x100c` SendObjectInfo | 호스트→카메라 파일 전송 |
| 총 | — | 그 기종이 광고하는 오퍼레이션 총 개수 |

연결경로 열은 `ConnectionPath`(0x****) 프로퍼티가 **보고할 수 있다고 명세가 정의한 값의
집합**이다. 그 경로로 PTP/IP 테더링이 실제로 되는지까지 보장하지는 않으므로, 무선 지원의
직접 근거가 아니라 정황 근거로 읽어야 한다. D5 는 이 프로퍼티 자체가 없어 빈칸이다.

## 매트릭스

| 기종 | PID | 등재 | 연결경로 | LV버전 | LV확장 | 부분Ex | 크기 | 메타 | 앱모드 | 벤더구 | 벤더신 | 속성쓰기 | 업로드 | 총 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| D5 | 0x043a | O | — | — | — | — | — | — | — | O | — | — | O | 73 |
| Z 5 | 0x0448 | O | USB·WT·Wi-Fi | 0x01 | O | O | O | O | — | O | — | — | O | 77 |
| Z 6 | 0x0443 | O | USB·WT·Wi-Fi | 0x01 | O | O | O | — | — | O | — | — | O | 74 |
| Z 7 | 0x0442 | O | USB·WT·Wi-Fi | 0x01 | O | O | O | — | — | O | — | — | O | 74 |
| Z 50 | 0x0444 | O | USB·Wi-Fi | 0x01 | O | O | O | — | — | O | — | — | O | 66 |
| Z 30 | 0x0452 | O | USB·WT·Wi-Fi | 0x01 | O | O | O | O | O | O | — | — | O | 79 |
| Z fc | 0x044f | O | USB·WT·Wi-Fi | 0x01 | O | O | O | O | O | O | — | — | O | 79 |
| Z 5II | 0x0456 | **미등재** | USB·Wi-Fi·LAN | 0x02 | O | O | O | O | O | — | O | O | — | 101 |
| Z 6II | 0x044c | O | USB·WT·Wi-Fi | 0x01 | O | O | O | O | O | O | — | — | O | 79 |
| Z 7II | 0x044b | O | USB·WT·Wi-Fi | 0x01 | O | O | O | O | O | O | — | — | O | 79 |
| Z 9 | 0x0450 | O | USB·Wi-Fi·LAN | 0x02 | O | O | O | O | O | — | O | O | O | 116 |
| Z 8 | 0x0451 | O | USB·Wi-Fi·LAN | 0x02 | O | O | O | O | O | — | O | — | O | 109 |
| Z f | 0x0453 | O | USB·Wi-Fi·LAN | 0x02 | O | O | O | O | O | — | O | O | — | 100 |
| Z 50II | 0x0455 | O | USB·Wi-Fi·LAN | 0x02 | O | O | O | O | O | — | O | O | — | 101 |
| Z 6III | 0x0454 | O | USB·Wi-Fi·LAN | 0x02 | O | O | O | O | O | — | O | O | — | 110 |
| ZR | 0x0458 | **미등재** | USB·Wi-Fi·LAN | 0x02 | O | O | O | O | O | — | O | O | — | 102 |

VID 는 16기종 전부 `0x04b0` 으로 같다.

## 읽어 둘 점

**세대 경계가 아주 뚜렷하다.** `0x****`~`0x****` 대역의 확장 프로퍼티 API, `0x****/0x****`
HLG 화질 데이터, `0x****` ClearEvent, `0x****/0x****` 카메라 설정 데이터 등 신형 API 는
Z 9·Z 8·Z f·Z 5II·Z 50II·Z 6III·ZR **7종에만** 있다. 그래서 광고 명령 총수도 이 7종은
100~116개인 반면 1세대는 66~79개에 머무른다.

**벤더 확장 코드 조회는 세대별로 코드가 다르다.** 구세대 9종(D5 포함)은 `0x****`
GetVendorPropCodes 를, 신형 7종은 `0x****` GetVendorCodes 를 쓴다. 겹치는 기종은 없다.
libgphoto2 는 `library.c:638`·`library.c:655` 에서 각각 `ptp_operation_issupported` 로 가드한
뒤 순서대로 시도하므로 두 세대 모두 정상 처리된다. 즉 이 차이가 지금 문제를 일으키지는
않지만, 벤더 코드 조회 쪽을 손댈 때는 두 갈래를 모두 유지해야 한다.

**라이브뷰 확장 프레임의 헤더 버전이 8:7 로 갈린다.** 구세대 8종(Z 30·Z 50·Z 5·Z 6·Z 6II·
Z 7·Z 7II·Z fc)이 Major version `0x01`, 신형 7종이 `0x02` 다. 이는 기존 조사 결과와 정확히
일치하며, 이번에 문서로 재확인했다. D5 는 `0x****` 자체가 없어 해당하지 않는다.
모든 문서가 버전 있는 데이터셋과 없는 데이터셋을 함께 기술하므로, 파서는 두 형태를 모두
받아낼 수 있어야 한다.

**파일 업로드는 신형에서 빠졌다.** `0x100c` SendObjectInfo·`0x100d` SendObject 는 11종에만
있고 Z f·Z 5II·Z 50II·Z 6III·ZR 에는 없다. Z 9·Z 8 은 있다. CamCon 은 업로드 기능이 없어
당장 영향은 없다.

**Z 9 전용 명령이 따로 있다.** `0x****`~`0x****` 자동 촬영(AutoCapture) 계열과
`0x****`~`0x****`(촬영 타이밍 옵션·부분 객체 전송)은 Z 9 에서만 광고된다.

**D5 는 Z 계열과 상당히 다르다.** `0x****`·`0x****`·`0x****`·`0x****`·`0x****`~`0x****`
같은 Z 공통 명령이 전부 없다. 대신 `0x****`~`0x****`(무비 픽처 컨트롤 확장),
`0x****` MirrorUpCancel 처럼 D5 에만 있는 명령을 갖는다. 즉 Z 세대용으로 다듬은 CamCon 의
경로가 D5 에서 그대로 통할 것이라고 가정해서는 안 된다.

**표는 능력 광고이지 실행 보장이 아니다.** 니콘 Z 는 무선 잠금 세션에서 광고된 명령을
0x200F(AccessDenied)로 거부하는 사례가 반복 확인됐다. `0x1008` GetObjectInfo,
`0x100a` GetThumb, `0x****` GetLargeThumb, `0x****` GetObjectPropList 가 모두 그런 예다
(패치 0015·0042·0043). 기종 판정에는 이 표를 쓰되, 세션 판정은 실행 결과로만 해야 한다.

## libgphoto2 카탈로그 미등재 2기종

`library.c` 의 기종 표를 VID/PID 로 대조한 결과, **ZR 뿐 아니라 Z 5II 도 미등재**였다.

| 기종 | VID | PID | 상태 |
|---|---|---|---|
| Z 5II | 0x04b0 | 0x0456 | **미등재** |
| ZR | 0x04b0 | 0x0458 | **미등재** |

나머지 14기종은 모두 등록돼 있다(예: `library.c:1849` 의 `{"Nikon:Z6 III", 0x04b0, 0x0454, ...}`).
따라서 예정된 카탈로그 등재 패치는 ZR 한 기종이 아니라 두 기종을 함께 다루는 편이 낫다.
두 기종 모두 등재 시 다른 Z 기종과 같은 `PTP_CAP|PTP_CAP_PREVIEW` 플래그가 적절해 보인다.

### ZR 특이사항

ZR 은 명세 4.1.1절 Device Descriptor 에 VID `0x04B0`("NIKON")·PID `0x0458` 이 명시돼 있다.
같은 절이 USB 2.10 과 USB 3.20 두 가지 기술자를 함께 싣는데 **PID 는 양쪽 모두 0x0458 로
같다**. 즉 USB 속도에 따라 PID 가 갈리지는 않으므로 카탈로그 항목은 하나면 충분하다.

능력 면에서 ZR 은 신형 7종에 확실히 속한다(총 102개, 라이브뷰 데이터셋 `0x02`,
`0x****` 벤더 코드 계열). 다만 신형 중에서도 동영상 성향이 강해, `0x****` GetVideoCodec 은
ZR 과 Z 6III 단 두 기종만 갖는다. 반대로 `0x****` GetManualSettingLensData2 는 ZR 에 있으나
Z 6III 에는 없다.

## 재현 방법

```bash
# 1. 텍스트 추출
for f in "S-SDKZ-200BF-ALLIN/Command/English/"*.pdf "S-SDKD5-011BF-ALLIN/Command/English/"*.pdf; do
    pdftotext -layout "$f" "$(basename "$f" .pdf).txt"
done

# 2. 요약 표 파싱
#    "supported by the camera are shown below" 다음 줄부터
#    "6.2.1 Standard" 헤딩 전까지에서 다음 정규식에 걸리는 행을 모은다.
#      ^\s*(0x[0-9A-Fa-f]{4})\s+(\w+)\s+(\d+\.\d+(?:\.\d+)*)\s+(Yes|-|\S+)
#    Z 8 이후 문서는 Yes/- 대신 체크 표시를 쓰므로 마지막 그룹을 넓게 잡아야 한다.

# 3. 교차 검증
#    본문 전체에서 "Operation Code\s+(0x[0-9A-Fa-f]{4})" 를 모아 위 집합과 대조한다.
```

전체 매트릭스는 `nikon-model-support.json` 에 기종 16 × 오퍼레이션 127 로 들어 있다.
