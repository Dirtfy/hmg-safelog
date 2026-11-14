# 세이프로그 (SafeLog) — HMG 차량용 앱 개발 공모전 (Pleos Connect)

브레이크·기어·차속 신호로 주차장·후진 같은 저속 순간의 페달 실수를 음성으로 먼저 알리고, 그 순간을 **운전자 본인의 기록**으로 남기는 Pleos 앱.
타깃은 Pleos 차량을 운전하는 만 65세 이상 운전자입니다. 차량 제어는 하지 않으며, 공유는 운전자가 직접 누를 때만(기본 꺼짐) 합니다. 확장 경로(고령 직업운전자·보험 연계·다른 운전자군·차종·해외·SDK 신호)는 기획서 6번 항목에 있습니다.

## 구조
| 경로 | 내용 |
|---|---|
| `docs/plan/기획서_세이프로그.docx` | 제출용 기획서 — 공모전 공식 접수양식(form.docx) 그대로, 02 항목 1~7 기입 + 별지(참고한 자료). 개인정보·서명 칸은 비워 둠 |
| `docs/plan/기획서_세이프로그.pdf` | 같은 내용의 PDF |
| `docs/demo/세이프로그_시연영상.mp4` | 시연 영상(145초, 한국어 자막, 무음). 세 장면 모두 **Pleos Connect Emulator 2.1.0에서 실제로 실행한 화면**(신호는 Virtual Console). 왼쪽 Pleos 화면 녹화, 오른쪽 같은 순간 앱이 그린 화면, 아래 앱 로그 |
| `docs/research/` | 근거 자료(조사 노트 md), Pleos SDK 공개 API 시그니처 추출본 |
| `docs/poc/poc_verdict.md` | PoC 구현 가능성 판정표 (구현됨 / 대체 구현 / 불가) |
| `poc/core` | 순수 Kotlin 로직: `PedalGuard`, `ParkingStartCheck`, `TripFeedback`, `AlertPolicy`(공유·동의 알림) + JUnit5 테스트 |
| `poc/app` | Android 어댑터: Pleos Vehicle SDK(Brake·Gear·Safety), TextToSpeech, AAOS `CarPropertyManager`(PERF_VEHICLE_SPEED) |
| `poc/sim/scenarios` | 가짜 신호 시나리오 CSV (오조작·정상 주차·고속 가속·브레이크 밟은 가속·에어백·경고등 3일·시연용 마트 후진 등 10개) |
| `poc/app/.../LiveActivity.kt`, `LiveSession.kt` | 라이브 모드: 실제 Pleos SDK 신호(Vehicle·NaviHelper·TextToSpeech)로 동작. 경고등 쉬운 말 해설 + 정비소 경유지, 오늘 돌아보기 + 운전자 '보내기' |
| `poc/pleos/` | Pleos Connect Emulator 실행 스크립트: `setup_avd.sh`(AVD), `vc.py`(Virtual Console REST), `record_live.sh`(세 장면 녹화) |
| `.github/workflows/pleos-probe.yml` | 공식 Pleos 에뮬레이터 이미지를 받아 KVM 러너에서 실행 → 스크립트 실행 → 결과 아티팩트(`pleos-out`) |
| `poc/app/.../DemoActivity.kt`, `DemoView.kt` | 데모 모드: 시나리오 CSV를 실제 core 엔진에 흘려 보내고 화면(주행·기록 보기·돌아보기)을 그림. Pleos SDK를 쓰지 않아 일반 에뮬레이터에서 실행됨 |
| `poc/demo/record.sh` | 에뮬레이터에서 데모 모드 실행 → 화면 녹화 + 100ms 단위 프레임 캡처 |
| `.github/workflows/ci.yml` | `:core:test` + `:app:assembleDebug` (APK는 Actions 아티팩트 `app-debug-apk`) |
| `.github/workflows/demo-record.yml` | (이전 방식) APK 빌드 → 일반 Android 에뮬레이터(API 34)에서 데모 모드 실행·녹화 — 수동 실행만 |

## 심사 환경 · 실행 방법
- 개발 대상 환경 (Pleos Playground 개발 문서 기준): Pleos Connect Emulator **2.1.0**, 화면 **2,560 x 1,440**, Android API 28 이상, Gradle 8 이상, Kotlin.
- 사용 SDK: Vehicle 2.3.2(Brake·Gear·Safety), TextToSpeech 3.3.1, NaviHelper 3.0.5, AAOS CarPropertyManager(PERF_VEHICLE_SPEED — Pleos 차량 앱 FAQ 안내 방법). 모두 공개 개발 문서 범위의 SDK이며 차량 제어 권한은 쓰지 않습니다.
- **현재 상태:** core 단위 테스트 28건 통과, APK 빌드, 그리고 **공식 Pleos Connect Emulator 2.1.0에서 실제 실행**(GitHub Actions KVM 러너)까지 확인했습니다. Virtual Console로 기어·브레이크·속도를 넣으면 앱이 Pleos Vehicle SDK·차량 속성으로 받아 R단 출발 안내, 페달 오조작 경고, 기록 저장, 주행 요약까지 동작했고, 음성은 Pleos TTS로 나갔습니다.
- **Pleos 에뮬레이터에서 찾은 것:** (1) `pleos.car.permission.TTS_SERVICE` 미선언 시 시작하자마자 종료 → 추가. (2) `registerTelltales`·`getTelltalesInfo`는 `PRIVATE_CAR_SAFETY` 필요(서드파티 불가) → 앱이 죽지 않게 격리, 시연은 값을 직접 넣음(`com.hmg.safelog.poc.TELLTALE` 브로드캐스트). (3) DO 미인정 앱은 주행·R단에서 화면 제한 + 액티비티 재생성 → 상태를 `LiveSession`으로. (4) 차량 콜백이 메인 스레드에서 수십 초 지연 → 별도 스레드. (5) 신호 몰림으로 급제동 과다 집계 → 1초 구간 가속도. (6) 임시 페달 기준이 빠른 정상 출발(5초에 40km/h)도 경고 → 실차 자료로 재조정 예정.
- 실행 방법: `./gradlew :core:test` (로직 테스트), `./gradlew :app:assembleDebug` (APK 빌드). Pleos SDK는 공개 Maven 저장소(nexus-playground.pleos.ai)에서 받습니다. 비밀값(API 키)은 저장소에 없습니다.
- Pleos 에뮬레이터 실행: Actions에서 `pleos-probe` 워크플로를 `script=poc/pleos/record_live.sh`로 실행(이미지는 실행마다 공식 주소에서 받고 재배포하지 않음). 로컬에서는 `adb shell am start -n com.hmg.safelog.poc/com.hmg.safelog.app.LiveActivity --ez route true` 후 Virtual Console(`adb forward tcp:8088 tcp:8088`)로 신호를 넣습니다.
- 데모 모드(일반 에뮬레이터용, 이전 방식): `adb shell am start -n com.hmg.safelog.poc/com.hmg.safelog.app.DemoActivity --es scenario demo_mart_reverse`.
- APK: PoC 디버그 APK는 GitHub Actions 아티팩트(`app-debug-apk`)로 받을 수 있습니다. 정식 APK는 2차 개발 기간에 `apk/` 폴더에 `팀명_앱이름_release.apk`로 올립니다.

## PoC 결과 요약
- CI: https://github.com/Dirtfy/hmg-safelog/actions — core 단위 테스트 28건 모두 통과, 실제 Pleos SDK(Vehicle 2.3.2, TextToSpeech 3.3.1, NaviHelper 3.0.5)를 붙여 APK 빌드 성공, Pleos Connect Emulator 2.1.0 실행·녹화 성공.
- 페달 경고 문구: "다른 페달, 브레이크를 밟으세요" — 놀란 순간에도 발을 옮기라는 뜻이 먼저 전해지도록 '다른 페달'을 앞에 둡니다.
- **대체 구현:** Vehicle SDK에는 가속 페달 API가 없습니다. 그래서 "R/D단 + 15km/h 이하에서 1초 안에 +8km/h + 브레이크 미작동" 조건으로 오조작을 추정합니다. 차속은 AAOS 경로로 받습니다.
- **미검증:** 실차 실행과 실차 신호 주기, 경고등 권한 승인, DO 인정(앱 마켓 심사), 음성 인식(STT — 에뮬레이터에 마이크 없음), 내비 경유지의 실제 반영(에뮬레이터 지도 데이터 없음). 연락처 전송 채널은 인터페이스만 만들었습니다.
- 빌드: 컴파일·테스트·에뮬레이터 실행은 모두 GitHub Actions에서 합니다.
