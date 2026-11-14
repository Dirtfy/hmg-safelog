#!/usr/bin/env bash
# Pleos Connect Emulator에서 세이프로그 LiveActivity를 실행하고, 차량 신호는 Virtual Console 웹 화면을 실제로 눌러서 넣는다.
# 조작 화면(Chrome, Xvfb)과 Pleos 화면을 장면마다 함께 녹화한다. 장면 진행은 record_vc.py.
set -u
O="$OUT"; mkdir -p "$O"
PKG=com.hmg.safelog.poc
VC="python3 poc/pleos/vc.py"
a() { timeout 60 adb shell "$@" 2>&1; }

# 조작 화면 녹화 도구: Xvfb + ffmpeg(x11grab) + Playwright(설치된 Chrome 사용)
( sudo apt-get update -qq && sudo apt-get install -y -qq xvfb ffmpeg ) > "$O/apt.txt" 2>&1
pip install -q playwright > "$O/pip.txt" 2>&1
google-chrome --version > "$O/chrome.txt" 2>&1
Xvfb :99 -screen 0 1280x800x24 -nolisten tcp > "$O/xvfb.txt" 2>&1 &
XV=$!
export DISPLAY=:99

adb forward tcp:8088 tcp:8088
# 페이지가 뜨기 전까지 브리지를 켜 둘 세션 신호(페이지도 같은 신호를 1초마다 보냄)
python3 poc/pleos/vc.py heartbeat &
HB=$!
$VC wait_ready | tee "$O/vc_ready.txt"
$VC catalog > "$O/catalog.json"
$VC state > "$O/state0.json"
timeout 120 adb install -r -t -g "$(ls poc/app/build/outputs/apk/debug/*.apk | head -1)" > "$O/install.txt" 2>&1
a dumpsys package $PKG | grep -E "permission|granted|installer" > "$O/app_perms.txt"
a settings put secure immersive_mode_confirmations confirmed
# R단에서 Pleos 후방 카메라 앱(ai.umos.car.evs)이 뜨는데 에뮬레이터에 카메라가 없어 ANR → 시스템이 느려짐. 녹화 동안 끈다
a pm disable-user --user 10 ai.umos.car.evs > "$O/evs_disable.txt" 2>&1; a pm disable-user ai.umos.car.evs >> "$O/evs_disable.txt" 2>&1
$VC gear Park; $VC controls 0 0 0
adb logcat -c
timeout 1500 adb logcat -v epoch > "$O/logcat.txt" 2>&1 &
LC=$!
python3 poc/pleos/vc.py frame_server "$O/app_frames/files/frames" &
FS=$!
adb reverse tcp:9099 tcp:9099
a am start -W -n $PKG/com.hmg.safelog.app.LiveActivity --ez route true --ez frames true --es frame_url http://127.0.0.1:9099/ > "$O/am_start.txt"
sleep 15
timeout 30 adb exec-out screencap -p > "$O/s00_start.png"

timeout 1500 python3 poc/pleos/record_vc.py > "$O/record_vc.log" 2>&1 || echo "record_vc exit $?" >> "$O/record_vc.log"

$VC state > "$O/state_end.json"
sleep 2
kill $LC $HB $FS $XV 2>/dev/null
grep -E " SafeLog|NaviHelper|Navi|TextToSpeech|pleos" "$O/logcat.txt" | grep -viE "dexopt" > "$O/logcat_app.txt"
ls -la "$O"
