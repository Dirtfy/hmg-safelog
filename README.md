# 세이프로그 (SafeLog) — HMG 차량용 앱 개발 공모전 (Pleos Connect)

브레이크·기어·차속 신호로 주차장·후진 같은 저속 순간의 페달 실수를 음성으로 먼저 알리고, 그 순간을 **운전자 본인의 기록**으로 남기는 Pleos 앱.
타깃은 Pleos 차량을 운전하는 만 65세 이상 운전자입니다. 차량 제어는 하지 않으며, 공유는 운전자가 직접 누를 때만(기본 꺼짐) 합니다. 확장 경로(고령 직업운전자·보험 연계·다른 운전자군·차종·해외·SDK 신호)는 기획서 6번 항목에 있습니다.

## 구조
| 경로 | 내용 |
|---|---|
| `docs/plan/기획서_세이프로그.docx` | 제출용 기획서 — 공모전 공식 접수양식(form.docx) 그대로, 02 항목 1~7 기입 + 별지(참고한 자료). 개인정보·서명 칸은 비워 둠 |
| `docs/plan/기획서_세이프로그.pdf` | 같은 내용의 PDF |
| `docs/demo/세이프로그_시연영상.mp4` | 시연 영상(107초, 한국어 자막, 무음). 장면 B(마트 주차장 후진)는 **실제 앱 화면**(데모 모드를 Android 에뮬레이터에서 실행해 캡처, 신호는 시뮬레이션), 장면 A·C는 화면 시안(목업) |
| `docs/research/` | 근거 자료(조사 노트 md), Pleos SDK 공개 API 시그니처 추출본 |
| `docs/poc/poc_verdict.md` | PoC 구현 가능성 판정표 (구현됨 / 대체 구현 / 불가) |
| `poc/core` | 순수 Kotlin 로직: `PedalGuard`, `ParkingStartCheck`, `TripFeedback`, `AlertPolicy`(공유·동의 알림) + JUnit5 테스트 |
| `poc/app` | Android 어댑터: Pleos Vehicle SDK(Brake·Gear·Safety), TextToSpeech, AAOS `CarPropertyManager`(PERF_VEHICLE_SPEED) |
| `poc/sim/scenarios` | 가짜 신호 시나리오 CSV (오조작·정상 주차·고속 가속·브레이크 밟은 가속·에어백·경고등 3일·시연용 마트 후진 등 10개) |
| `poc/app/.../DemoActivity.kt`, `DemoView.kt` | 데모 모드: 시나리오 CSV를 실제 core 엔진에 흘려 보내고 화면(주행·기록 보기·돌아보기)을 그림. Pleos SDK를 쓰지 않아 일반 에뮬레이터에서 실행됨 |
| `poc/demo/record.sh` | 에뮬레이터에서 데모 모드 실행 → 화면 녹화 + 100ms 단위 프레임 캡처 |
| `.github/workflows/ci.yml` | `:core:test` + `:app:assembleDebug` (APK는 Actions 아티팩트 `app-debug-apk`) |
| `.github/workflows/demo-record.yml` | APK 빌드 → Android 에뮬레이터(API 34)에서 데모 모드 실행·녹화 (아티팩트 `safelog-demo-recording`) |

## 심사 환경 · 실행 방법
- 개발 대상 환경 (Pleos Playground 개발 문서 기준): Pleos Connect Emulator **2.1.0**, 화면 **2,560 x 1,440**, Android API 28 이상, Gradle 8 이상, Kotlin.
- 사용 SDK: Vehicle 2.3.2(Brake·Gear·Safety), TextToSpeech 3.3.1, AAOS CarPropertyManager(PERF_VEHICLE_SPEED — Pleos 차량 앱 FAQ 안내 방법). 모두 공개 개발 문서 범위의 SDK이며 차량 제어 권한은 쓰지 않습니다.
- **현재 상태:** core 단위 테스트(21건), APK 빌드, 그리고 데모 모드를 일반 Android 에뮬레이터(API 34, GitHub Actions)에서 실행하는 것까지 확인했습니다. 데모 모드는 시뮬레이션 CSV를 실제 판단 로직(PedalGuard 등)에 넣어 경고 시점을 앱이 정합니다. Pleos Connect Emulator 2.1.0에서 Pleos SDK 신호(브레이크·기어·차속)로 실행하는 것은 아직 하지 않았고, 2차 개발 기간에 진행합니다.
- 실행 방법: `./gradlew :core:test` (로직 테스트), `./gradlew :app:assembleDebug` (APK 빌드). Pleos SDK는 공개 Maven 저장소(nexus-playground.pleos.ai)에서 받습니다. 비밀값(API 키)은 저장소에 없습니다.
- 데모 실행: `adb shell am start -n com.hmg.safelog.poc/com.hmg.safelog.app.DemoActivity --es scenario demo_mart_reverse` (프레임 캡처는 `--ez frames true`).
- APK: PoC 디버그 APK는 GitHub Actions 아티팩트(`app-debug-apk`)로 받을 수 있습니다. 정식 APK는 2차 개발 기간에 `apk/` 폴더에 `팀명_앱이름_release.apk`로 올립니다.

## PoC 결과 요약
- CI: https://github.com/Dirtfy/hmg-safelog/actions — core 단위 테스트 21건 모두 통과, 실제 Pleos SDK(Vehicle 2.3.2, TextToSpeech 3.3.1)를 붙여 APK 빌드 성공, 에뮬레이터 데모 녹화 성공.
- 페달 경고 문구: "다른 페달, 브레이크를 밟으세요" — 놀란 순간에도 발을 옮기라는 뜻이 먼저 전해지도록 '다른 페달'을 앞에 둡니다.
- **대체 구현:** Vehicle SDK에는 가속 페달 API가 없습니다. 그래서 "R/D단 + 15km/h 이하에서 1초 안에 +8km/h + 브레이크 미작동" 조건으로 오조작을 추정합니다. 차속은 AAOS 경로로 받습니다.
- **미검증:** 실차·Pleos 에뮬레이터 실행, 권한 승인, 콜백 주기, Pleos TTS 음성 출력. 연락처 전송 채널은 인터페이스만 만들었습니다.
- 빌드: 컴파일·테스트·에뮬레이터 실행은 모두 GitHub Actions에서 합니다.
