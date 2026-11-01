# research6 공통 — MAU 퍼널 비율(설치율·활성률·유료전환) 근거 벤치마크

조사일: 2026-10-31. RULES.md 형식 준수. 열어서 확인한 결과(WebSearch/WebFetch)만 인용. 1차 원문 페이지를 직접 열지 못한 것은 "2차 인용"으로 표기. 기존 퍼널: Pleos 보급 대수(research5/common.md 2-3: 2027말 약 29만대, 2028말 약 59만대, 신뢰도 낮음, E) × 타깃 점유율 → 설치율 → 활성률 → (유료전환).

## 오너 질문별 답 (funnel ratio answers)

**Q1. 설치율(Pleos 탑재차 오너 중 특정 앱을 설치하는 비율) — 권장 Low 3% / Mid 8% / High 15%** (등급 E, 확신도 하)
- 직접 벤치마크는 없음. Pleos 앱마켓 다운로드·이용자 수는 미발견(앱 18종 확대 보도에 수치 없음). 인카 앱스토어 설치율 자체를 다룬 공개 통계도 미발견.
- 간접 앵커: (a) 국내 신차 구매자(2년 내)의 97%가 커넥티드카 시스템을 "이용"(컨슈머인사이트 제25차, 오토헤럴드 2026-02-03). 단 대부분 무료 기간 내 기본 기능 이용. (b) 미국 ICE 오너 약 80%가 차량 앱을 이용하나 "자주 쓰는" 비율은 27%(J.D. Power 2025 ICE 앱 리포트, 2차 인용). (c) J.D. Power TXI 2025: 차량 내 쇼핑·결제 같은 앱형 서비스는 "낮은 채택"으로 평가.
- 산식(E): Mid 8% = 앱스토어를 능동적으로 쓰는 오너 약 27%(J.D. Power "frequent" 비율을 대리지표로 사용) × 그중 특정 카테고리 앱을 실제 설치하는 비율 약 30%(팀 가정) ≈ 8%. Low 3%는 "frequent" 비율의 약 1/9(일반 사용자는 기본 탑재 앱 위주 사용 가정), High 15%는 제조사 푸시·기본 노출·앱 필요성이 큰 경우(렌터카·가족 등 명확한 pain point). 30% 가정은 근거 없는 팀 가정이므로 민감도 분석에 포함 권장.

**Q2. 활성률(설치 대비 월간 활성) — 권장 Low 10% / Mid 35% / High 55%** (등급 B/E, 확신도 중-하)
- 모바일 오픈마켓 하한: AppsFlyer(2026 Q3 툴, Spreadcast 2차 인용) Lifestyle D30 리텐션 2.5%, Travel 2.4%; Braze 인용 AppsFlyer 기준 Utilities&Productivity D30 2.7%/2.7%(iOS/Android). 단, 이는 광고 설치 유입 앱의 "설치 후 30일째 접속" 지표로, MAU/설치보다 훨씬 보수적이고 차량 내 설치(의도적 설치)와 성격이 다름.
- 중간 앵커: 와이즈앱·리테일 — OTT 앱 1인당 평균 2개 설치, 실제 사용 0.7개 (≈35%)(뉴스1, 제목·첫 문장만 확인).
- 상단 앵커: J.D. Power 2026 EV 앱 리포트(autoconnectedcar 2차 인용) — 이탈(앱 사용 중단) 4.5%(2023년 22%), 비테슬라 일간 사용 55%. 2025 리포트: 비테슬라 32%/테슬라 79%가 매 주행마다 사용.
- 산식: Low 10% ≈ √(2.5% × 35%) = 9.4%(하한과 중간의 기하평균, E). Mid = 35%(OTT 사용/설치). High 55%(EV 앱 일간 사용률).

**Q3. 유료전환 — 권장(설치 대비) Low 1% / Mid 2% / High 5.6%; (활성 대비) Low 3% / Mid 6% / High 10%** (등급 A/B/E, 확신도 중)
- RevenueCat State of Subscription Apps 2026: 전체 D35 다운로드→유료 중앙값 2.0%, 상위 10% 9.1%; freemium 2.1%, 하드 페이월 10.7%; 북미 2.6%(상위 25% 5.6%); iOS 2.9%·Android 0.9%(2차 요약: Arpu Brothers; RevenueCat 원문은 카테고리 수치 일부만 확인 — Health&Fitness 2.9%, Business 2.6%, Shopping 1.3%, Gaming 1.0%; Travel 트라이얼→유료 43.5%, 다운로드→트라이얼 Utilities/Education 6.5%, Travel 4.1%).
- 한국 커넥티드카: 무료 종료 후 유료 전환 6%(국산 3%, 수입 20%)(컨슈머인사이트 제25차, 오토헤럴드 2026-02-03). 블루링크 무료 5년 후 월 5,500원(1년 약정)/11,000원(무약정)(현대차 공식 안내 요약, 2차).
- 산식: Mid 2.0%(설치 대비) = RevenueCat 중앙값. 활성 대비로 환산하면 2.0% ÷ 35%(Q2 Mid) ≈ 5.7% ≈ 6% — 컨슈머인사이트 6%와 교차 일치(우연일 수 있음). Low 1% ≈ Android 0.9%. High 5.6% = 북미 상위 25%. 활성 대비 High 10% ≈ 하드 페이월 중앙값 10.7%(설치 기준)를 활성 기준으로 차용한 보수적 상한(E). 주의: 자동차 도메인은 무료 기능 번들·소유자 반발(소비자주권시민회의 2026-04 공정위 조사 촉구 보도)로 유료 저항 큼 → 개인 대상 유료는 Mid 이하 권장.

**Q4. 지불의사 서베이(방향성)** (등급 B, 확신도 중)
- S&P Global Mobility 2025: 커넥티드 서비스 지불의사 86%(2024)→68%(2025)(CBT News 2025-07-07, CarBuzz 인용). "인포테인먼트 지불 최하위"(2024 조사 요약). 2025 초 서베이: 76%가 브랜드 커넥티드 서비스 비구독, 65%가 고가를 이유로 꼽음(Dealership Guy). J.D. Power 2026 EV 앱: 64% 구독료 지불 안 함(2차). McKinsey 2014→2015: 구독 지불의사 21%→32%(구자료, 참고).
- 시사: 설문 지불의사(68%)는 실제 전환(6%)의 10배 이상 → 설문치를 전환율로 쓰지 말 것.

**Q5. 렌터카·플릿 B2B 참고 단가** (등급 B, 확신도 중-하)
- 국내 차량관제(GPS) 대당 월: LG U+ 커넥트 16,500원(VAT 포함, 단말 0원); IoTIC 12,000~15,000원(VAT 별도, 36개월 약정); 제이비즈 13,200원(VAT 포함, 약 3년 전 자료); urigage 13,200원(36개월). → 권장 기준 Low 12,000 / Mid 16,500 / High 약 30,000원/대·월(High는 AI카메라 포함 안전관제용, 해외 환산, E).
- 해외: Geotab 기본 $10~18(3rd-party 추정), 정부 프로그램 $19.75(HW·설치 포함); Samsara $27~33(GPS), Sourcewell 공시 리스트가 $39/대·월, 하드웨어 별도 $99~148(airpinpoint/btracking 등 2차). 환율 1$=1,400원 가정(E) 시 $39≈5.5만원.
- 렌터카 전용 관제 대당 단가는 미발견(롯데렌터카 비즈카 커넥트프로 요금 비공개).

## 수치 근거표

| 수치 | 값 | 출처(기관·연도) | URL | 산출(산식·가정) | 등급 | 확신도 |
|---|---|---|---|---|---|---|
| 신차 구매자(2년 내) 커넥티드카 시스템 이용 | 97% | 컨슈머인사이트 제25차 연례 자동차 기획조사, 보도 2026-02-03 | http://www.autoherald.co.kr/news/articleView.html?idxno=59404 | 직접 확인. 표본 수 기사에 미기재 | A(1차 인용 보도) | 중 |
| 무료 기간 중 비율 / 유료 전환 | 91% / 6% (국산 3%, 수입 20%) | 동일 | 동일 | 직접 확인 | A | 중 |
| 국산차 주 이용 기능 | 원격제어 39%, 실시간 내비 31%, AI 2% | 동일 | 동일 | 직접 확인 | A | 중 |
| 커넥티드카 이용경험자 만족(미디어·스트리밍 68%, 스마트폰 연계 67%, 실시간 내비 65%) | 만족률 | 컨슈머인사이트 2025 (이용경험자 1,489명) | https://www.consumerinsight.co.kr/autoinsight/abview?no=3859&id=pr4_list | 검색 요약만 확인(페이지 직접 열람 실패) | B | 중 |
| 미국 ICE 오너 차량 앱 이용 / 자주 이용 | ~80% / 27% | J.D. Power 2025 ICE 앱 리포트(2025-12) | https://www.businesswire.com/news/home/20251210626487/en (검색 결과 링크, 본문 미열람) | 검색 요약 인용 | B | 중 |
| EV 앱: 비테슬라 매 주행 사용 32%, 테슬라 79% | 32%/79% | J.D. Power 2025 EV 앱 리포트 | https://www.jdpower.com/business/press-releases/2025-us-oem-ev-app-report/ | 검색 요약 인용 | B | 중 |
| EV 앱 이탈 4.5%(2023 22%), 비테슬라 일간 55%, 구독 미지불 64% | 4.5%/55%/64% | J.D. Power 2026 EV 앱(autoconnectedcar 인용) | https://www.autoconnectedcar.com/2026/05/ev-app-usage-is-up-but-more-useful-features-are-wanted/ | 2차 요약 | B | 하-중 |
| 차량 내 쇼핑·결제 앱 낮은 채택 | 정성 | J.D. Power TXI 2025 (76,230명) | https://www.jdpower.com/business/press-releases/2025-us-tech-experience-index-txi-study | 검색 요약 | B | 중 |
| 커넥티드 서비스 지불의사 | 86%(2024)→68%(2025) | S&P Global Mobility 2025 (9,000+명), CBT News | https://www.cbtnews.com/survey-shows-decline-in-consumer-interest-for-connected-car-services-amid-privacy-and-pricing-concerns/ | 검색 요약 | B | 중 |
| 브랜드 커넥티드 서비스 비구독 76%, 고가 사유 65% | 76%/65% | Dealership Guy 2025-02 | https://news.dealershipguy.com/p/why-drivers-are-skipping-connected-car-subscriptions-2025-02-10 | 검색 요약 | B | 하-중 |
| McKinsey 구독 지불의사 | 21%(2014)→32%(2015) | McKinsey | https://www.mckinsey.com/industries/automotive-and-assembly/our-insights/how-carmakers-can-compete-for-the-connected-consumer | 구자료 | B | 중(구식) |
| 현대차·기아 글로벌 커넥티드 가입 1,000만 | 1,000만(2023-06) | 현대차그룹 | https://www.hyundaimotorgroup.com/ko/news/CONT0000000000098115 | 검색 결과 제목만 확인(가입률 아님) | A | 중 |
| 국내 그룹 커넥티드 누적 | 200만(2020 말) | 현대차그룹 보도(이코노믹리뷰 등) | https://www.econovill.com/news/articleView.html?idxno=501065 | 가입률 산출 불가(분모 미확인) | B | 중 |
| 블루링크 무료 5년 후 요금 | 월 5,500(1년 약정)/11,000(무약정) | 현대차 안내(검색 요약) | https://www.hyundai.com/kr/ko/digital-customer-support/app/myhyundai/bluelink-registration | 검색 요약 | A/B | 중 |
| Pleos 앱마켓 앱 수 | 11→18종 | 포티투닷/현대차그룹 2026-09-07 | https://www.hyundaimotorgroup.com/ko/news/42dot-pleos-connect-app-market-new-apps | 다운로드·이용자 수 없음 | A | 상 |
| 전체 D35 다운로드→유료 중앙값 | 2.0% (상위10% 9.1%) | RevenueCat SOSA 2026 (Arpu Brothers 2차) | https://www.revenuecat.com/state-of-subscription-apps / https://arpubrothers.com/blog/revenuecat-subscription-app-report-2026/ | 2.0%는 RevenueCat 본문에서도 확인 | A/B | 중 |
| Freemium 2.1% / 하드 페이월 10.7% / 북미 2.6%(상위25% 5.6%) / iOS 2.9%, Android 0.9% | | 위와 동일(검색 요약) | 동일 | 2차 요약, 일부 출처 간 상이(북미 2.6 vs 2.8) | B | 중-하 |
| RevenueCat 카테고리: Travel 트라이얼→유료 43.5%; 다운로드→트라이얼 Utilities/Education 6.5%, Travel 4.1% | | RevenueCat SOSA 2026 | https://www.revenuecat.com/state-of-subscription-apps | 직접 열람(카테고리 D35·월 이탈률은 본문 미노출) | A | 중 |
| 월간 플랜 1년 유지율 | 14~26% | RevenueCat 2026 (Arpu Brothers 2차) | https://arpubrothers.com/blog/revenuecat-subscription-app-report-2026/ | 2차 | B | 하-중 |
| Lifestyle D1/D7/D30 | 17.0/6.5/2.5% | AppsFlyer 벤치마크(2026 Q3, Spreadcast 인용) | https://spreadcast.app/resources/app-retention-benchmarks | 2차 | B | 중 |
| Travel D1/D7/D30 | 19.5/7.3/2.4% | 동일 | 동일 | 2차 | B | 중 |
| D30 iOS/Android: Travel 3.1/2.8, Utilities&Productivity 2.7/2.7, Lifestyle 2.7/2.5 | | AppsFlyer via Business of Apps, Braze 인용 | https://www.braze.com/resources/articles/mobile-app-retention-10-tip | 2차 | B | 중 |
| 고(高) 대안: Lifestyle 25/13.1/5.9%, Utilities 28.2/16.8/9.2% | | OneSignal via GetStream | https://getstream.io/blog/app-retention-guide/ | 측정 방식 상이(상한 참고) | B | 하 |
| OTT 앱 1인당 설치 2개·사용 0.7개 | ≈35% | 와이즈앱·리테일, 뉴스1 | https://www.news1.kr/it-science/general-it/5914840 | 제목·요약만 확인. 0.7÷2=35% | B | 하-중 |
| 국내 차량관제 GPS 대당 월 요금 | 12,000~16,500원 | LG U+ 커넥트, IoTIC, 제이비즈, urigage | https://www.lguplus.com/biz/all/telecom/wireless/uplus-vehicle/B000000046 / https://iotic.co.kr/%EC%B0%A8%EB%9F%89%EA%B4%80%EC%A0%9C-detail/ / https://www.biziot.kr/product4/ | 검색 요약(페이지 미열람) | A/B | 중 |
| Geotab/Samsara 대당 월 | Geotab $10~18(정부 $19.75), Samsara $27~33(리스트 $39) | airpinpoint, btracking 등 | https://airpinpoint.com/compare/samsara-vs-geotab / https://btracking.com/blog/what-fleet-gps-actually-costs | 벤더 비공개, 3자 추정 | B | 중-하 |
| Pleos 보급 추정 | 2027말 ≈29만대, 2028말 ≈59만대 | 본 팀(research5/common.md 2-3) | research5/common.md | 그랜저 월 8,356대 등 가정 | E | 하 |
| 권장 설치율 Low/Mid/High | 3/8/15% | 본 팀 | - | Q1 산식 | E | 하 |
| 권장 활성률 | 10/35/55% | 본 팀 | - | Q2 산식 | E | 하-중 |
| 권장 유료전환(설치 기준) | 1/2/5.6% | 본 팀 | - | Q3 산식 | E | 중 |

## 설계 변경 제안
1. **퍼널 표기 통일**: "Pleos 대수 × 타깃 점유 × 설치율 × 활성률 × (유료)" 에서 유료전환 정의(설치 대비 vs 활성 대비)를 명시. 설치 대비 2% ≈ 활성 대비 6%(교차 일치)이므로 이중 적용 금지.
2. **MAU 보수 시나리오 기본값**: 설치율 8% × 활성률 35% ≈ 2.8% (Mid) / 3%×10% = 0.3% (Low) / 15%×55% ≈ 8% (High). 예: 2028말 59만대 × 타깃 점유 100% 가정 시 Mid 약 1.6만 MAU(타깃이 렌터카 등 일부 세그먼트면 해당 비율만큼 축소). 주의: 59만대 자체가 E-저신뢰.
3. **B2C 유료 의존 축소**: 자동차 영역은 무료 번들·유료 반발이 커서(전환 6%, 국산 3%) 개인 구독 대신 B2B(렌터카·플릿) 과금 비중 확대. B2B 참고가: 월 12,000~16,500원/대(국내 관제), 해외 $20~39/대.
4. **리텐션 목표 제시 시 모바일 벤치마크(D30 2.5~3%) 대비 인카 우위 논거**를 쓰되, 인카 직접 근거는 J.D. Power EV 앱 이탈 4.5%·일간 55%로 한정(앱스토어 앱 직접 수치 아님을 명시).
5. 심사 "예상 MAU·Retention" 항목에 Low/Mid/High 3개 시나리오와 각 산식을 부록 표로 제시.

## 미발견·한계
- Pleos App Market 다운로드·이용자 수: 미발견. 인카 앱스토어(AAOS/Google built-in, Polestar/Volvo 등) 차량당 앱 다운로드·설치율: 미발견(Polestar 폰 앱 100K+ 다운로드만 확인, 무관).
- 블루링크/기아 커넥트 "가입률"(판매차 대비) 및 유료 유지율: 미발견(그룹 누적 가입자만 있고 분모 없음).
- 한국 인카 앱 이용 서베이(컨슈머인사이트) 중 앱 설치·이용률: 미발견(기능별 만족률·주 이용 기능만).
- RevenueCat 카테고리별(Utilities/Lifestyle/Travel/Education) D35 전환·월 이탈률 정확값: 본문 미노출, 2차 요약만. Adjust 2025 해당 카테고리 수치: 미발견.
- AppsFlyer·J.D. Power·S&P·RevenueCat 다수 수치는 검색 요약·2차 인용이며 원문 PDF 미열람.
- 렌터카 전용 관제 단가: 미발견. 1$=1,400원 환율은 팀 가정.
- 설치율 Q1의 "30%" 및 활성률 Low는 팀 가정/산식 기반 추정(근거 약함).
