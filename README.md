# 세이프로그 (SafeLog) — HMG 차량용 앱 개발 공모전 (Pleos Connect)

브레이크·기어·차속 신호로 주차장·후진 같은 저속 순간의 페달 실수를 음성으로 먼저 알리고, 그 순간을 **운전자 본인의 기록**으로 남기는 Pleos 앱.
타깃은 Pleos 차량을 운전하는 만 65세 이상 운전자입니다. 차량 제어는 하지 않으며, 공유는 운전자가 직접 누를 때만(기본 꺼짐) 합니다. 확장 경로(고령 직업운전자·보험 연계·다른 운전자군·차종·해외·SDK 신호)는 기획서 6번 항목에 있습니다.

## 구조
| 경로 | 내용 |
|---|---|
| `docs/plan/기획서_세이프로그.docx` | 제출용 기획서 — 공모전 공식 접수양식(form.docx) 그대로, 02 항목 1~7 기입 + 별지(참고한 자료). 개인정보·서명 칸은 비워 둠 |
| `docs/plan/기획서_세이프로그.pdf` | 같은 내용의 PDF |
| `docs/demo/세이프로그_시연영상.mp4` | 화면 흐름 시연 영상(목업, 88초, 한국어 자막) — 화면 시안으로 만든 것이며 실제 앱 녹화가 아님 |
| `docs/research/` | 근거 자료(조사 노트 md), Pleos SDK 공개 API 시그니처 추출본 |
| `docs/poc/poc_verdict.md` | PoC 구현 가능성 판정표 (구현됨 / 대체 구현 / 불가) |
| `poc/core` | 순수 Kotlin 로직: `PedalGuard`, `ParkingStartCheck`, `TripFeedback`, `AlertPolicy`(공유·동의 알림) + JUnit5 테스트 |
| `poc/app` | Android 어댑터: Pleos Vehicle SDK(Brake·Gear·Safety), TextToSpeech, AAOS `CarPropertyManager`(PERF_VEHICLE_SPEED) |
| `poc/sim/scenarios` | 가짜 신호 시나리오 CSV (오조작·정상 주차·고속 가속·브레이크 밟은 가속·에어백·경고등 3일 등) |
| `.github/workflows/ci.yml` | `:core:test` + `:app:assembleDebug` |

## 심사 환경 · 실행 방법
- 개발 대상 환경 (Pleos Playground 개발 문서 기준): Pleos Connect Emulator **2.1.0**, 화면 **2,560 x 1,440**, Android API 28 이상, Gradle 8 이상, Kotlin.
- 사용 SDK: Vehicle 2.3.2(Brake·Gear·Safety), TextToSpeech 3.3.1, AAOS CarPropertyManager(PERF_VEHICLE_SPEED — Pleos 차량 앱 FAQ 안내 방법). 모두 공개 개발 문서 범위의 SDK이며 차량 제어 권한은 쓰지 않습니다.
- **현재 상태:** 에뮬레이터에서는 아직 실행하지 않았습니다. 검증된 것은 core 단위 테스트와 APK 컴파일(GitHub Actions)까지입니다. 에뮬레이터 실행·화면 확인은 2차 개발 기간에 진행합니다.
- 실행 방법: `./gradlew :core:test` (로직 테스트), `./gradlew :app:assembleDebug` (APK 빌드). Pleos SDK는 공개 Maven 저장소(nexus-playground.pleos.ai)에서 받습니다. 비밀값(API 키)은 저장소에 없습니다.
- APK: 아직 없음. 2차 개발 기간에 `apk/` 폴더에 `팀명_앱이름_release.apk`로 올립니다.

## PoC 결과 요약
- CI: https://github.com/Dirtfy/hmg-safelog/actions/runs/41285531757. core 단위 테스트는 20건 모두 통과했고, 실제 Pleos SDK(Vehicle 2.3.2, TextToSpeech 3.3.1)로 APK 컴파일에 성공했습니다.
- **대체 구현:** Vehicle SDK에는 가속 페달 API가 없습니다. 그래서 "R/D단 + 15km/h 이하에서 1초 안에 +8km/h + 브레이크 미작동" 조건으로 오조작을 추정합니다. 차속은 AAOS 경로로 받습니다.
- **미검증:** 실차·에뮬레이터 실행, 권한 승인, 콜백 주기, 실제 음성 출력. 연락처 전송 채널은 인터페이스만 만들었습니다.
- 빌드 정책: 서버에서는 빌드하지 않고, 컴파일·테스트는 GitHub Actions에서만 실행합니다.
