#!/usr/bin/env bash
# Pleos Connect Emulator에서 세이프로그 LiveActivity를 실행하고, Virtual Console REST로 신호를 넣어 세 장면을 녹화한다.
# 녹화: 에뮬레이터 자체 녹화(adb emu screenrecord, 호스트 쪽 → 게스트 부하 없음). 결과: $OUT/scene_{A,B,C}.webm, logcat, vc_calls.log
set -u
O="$OUT"; mkdir -p "$O"
PKG=com.hmg.safelog.poc
VC="python3 poc/pleos/vc.py"
a() { timeout 60 adb shell "$@" 2>&1; }
ts() { echo "$(date +%s.%N) $*" | tee -a "$O/timeline.log"; }
rec_start() { ts "REC_START $1"; timeout 20 adb emu screenrecord start --time-limit 170 "$O/scene_$1.webm" >> "$O/rec.log" 2>&1; }
rec_stop() { timeout 20 adb emu screenrecord stop >> "$O/rec.log" 2>&1; ts "REC_STOP $1"; sleep 3; }
voice() { ts "VOICE $1"; a am broadcast -a $PKG.VOICE --es text "\"$1\"" -p $PKG > /dev/null; }
tap() { # 1920x1080 layout unit -> screen px using VIEW_BOUNDS
  read X Y W H < "$O/view_bounds.txt"
  python3 - "$1" "$2" $X $Y $W $H > /tmp/tap.txt <<'PY'
import sys
ux,uy,X,Y,W,H=map(float,sys.argv[1:])
u=min(W/1920,H/1080); ox=X+(W-1920*u)/2; oy=Y+(H-1080*u)/2
print(int(ox+ux*u), int(oy+uy*u))
PY
  ts "TAP $1 $2 -> $(cat /tmp/tap.txt)"; a input tap $(cat /tmp/tap.txt)
}

adb forward tcp:8088 tcp:8088
# Virtual Console은 브라우저 세션(1초 heartbeat)이 있어야 차량 쪽(DDS) 브리지를 켠다
python3 poc/pleos/vc.py heartbeat &
HB=$!
$VC wait_ready | tee "$O/vc_ready.txt"
$VC catalog > "$O/catalog.json"
$VC state > "$O/state0.json"
timeout 120 adb install -r -t -g "$(ls poc/app/build/outputs/apk/debug/*.apk | head -1)" > "$O/install.txt" 2>&1
a dumpsys package $PKG | grep -E "permission|granted" > "$O/app_perms.txt"
a settings put secure immersive_mode_confirmations confirmed
# 시작 상태: P, 브레이크 밟음
$VC gear Park; $VC controls 0 0 100
adb logcat -c
timeout 600 adb logcat -v epoch > "$O/logcat.txt" 2>&1 &
a am start -W -n $PKG/com.hmg.safelog.app.LiveActivity --ez route true > "$O/am_start.txt"
sleep 12
grep -m1 "VIEW_BOUNDS" "$O/logcat.txt" | sed 's/.*VIEW_BOUNDS //' > "$O/view_bounds.txt"; cat "$O/view_bounds.txt"
timeout 30 adb exec-out screencap -p > "$O/s00_start.png"

# ---------------- 장면 A: 아침, 병원 가는 길 · 주행 중 경고등(TPMS) → 쉬운 말 해설 → "응" → 정비소 경유지
rec_start A
sleep 2
$VC gear Drive; sleep 1.5
$VC controls 0 0 0; sleep 0.5
$VC ramp 0 40 5 30 0
sleep 3
ts "TELLTALE TPMS On"; $VC telltale TPMS On
sleep 10
voice "응, 안내해 줘"
sleep 8
timeout 30 adb exec-out screencap -p > "$O/sA_end.png"
rec_stop A
# 녹화 밖: 병원까지 계속 주행(거리·시간이 돌아보기 요약에 들어감) 후 주차
$VC ramp 40 50 3 30 0
sleep 120
$VC ramp 50 0 6 0 60
$VC controls 0 0 100; sleep 1; $VC gear Park; sleep 4

# ---------------- 장면 B: 낮, 마트 주차장 후진 · 페달 오조작 의심
rec_start B
sleep 3
ts "GEAR R"; $VC gear Reverse
sleep 5
ts "BRAKE OFF"; $VC controls 0 0 0
sleep 2
ts "ACCEL (wrong pedal)"; $VC ramp 0 13 1.0 45 0
sleep 1.5
ts "BRAKE ON"; $VC controls 13 0 100; $VC ramp 13 0 2.4 0 100
sleep 2
ts "GEAR P"; $VC gear Park
sleep 4
timeout 30 adb exec-out screencap -p > "$O/sB_parked.png"
tap 480 690   # 기록 보기
sleep 11
timeout 30 adb exec-out screencap -p > "$O/sB_record.png"
tap 1800 80   # 닫기
sleep 2
rec_stop B

# ---------------- 장면 C: 저녁, 집 앞 · 오늘 운전 돌아보기 → "보내 줘"
rec_start C
sleep 3
tap 1410 690  # 오늘 운전 돌아보기
sleep 10
voice "보내 줘"
sleep 8
timeout 30 adb exec-out screencap -p > "$O/sC_sent.png"
rec_stop C

$VC state > "$O/state_end.json"
sleep 2
kill %1 2>/dev/null; kill $HB 2>/dev/null
grep -E " SafeLog|NaviHelper|Navi|TextToSpeech|pleos" "$O/logcat.txt" | grep -viE "dexopt" > "$O/logcat_app.txt"
ls -la "$O"
