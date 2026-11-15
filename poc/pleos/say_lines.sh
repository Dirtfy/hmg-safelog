#!/usr/bin/env bash
# 시연 영상 소리용: 앱이 주행 중 말한 문장을 앱의 실제 TTS 경로(Pleos TextToSpeech)로 한 줄씩 다시 말하게 하고,
# 에뮬레이터 소리 출력(pulseaudio null sink, 워크플로 audio=true)을 녹음한다. 문장마다 시각을 MARK로 남긴다.
set -u
O="$OUT"; mkdir -p "$O"
PKG=com.hmg.safelog.poc
a() { timeout 60 adb shell "$@" 2>&1; }
adb forward tcp:8088 tcp:8088
python3 poc/pleos/vc.py heartbeat &
HB=$!
python3 poc/pleos/vc.py wait_ready > "$O/vc_ready.txt" 2>&1
timeout 120 adb install -r -t -g "$(ls poc/app/build/outputs/apk/debug/*.apk | head -1)" > "$O/install.txt" 2>&1
a settings put secure immersive_mode_confirmations confirmed
python3 poc/pleos/vc.py gear Park; python3 poc/pleos/vc.py controls 0 0 0
a media volume --show --stream 3 --set 15 > "$O/volume.txt" 2>&1
a cmd media_session volume --stream 3 --set 15 >> "$O/volume.txt" 2>&1
adb logcat -c
timeout 900 adb logcat -v epoch > "$O/logcat.txt" 2>&1 &
LC=$!
a am start -W -n $PKG/com.hmg.safelog.app.LiveActivity > "$O/am_start.txt"
sleep 20
pactl list short sink-inputs > "$O/sink_inputs0.txt" 2>&1; pactl list short clients >> "$O/sink_inputs0.txt" 2>&1
i=0
mapfile -t LINES < poc/pleos/say_lines.txt   # adb shell이 표준입력을 먹으므로 while-read를 쓰지 않는다
for line in "${LINES[@]}"; do
  [ -z "$line" ] && continue
  i=$((i+1))
  for rep in 1 2; do
    echo "$(date +%s.%N) SAY $i.$rep $line" >> "$O/timeline.log"
    a am broadcast -a com.hmg.safelog.poc.SAY --es text "\"$line\"" -p $PKG > /dev/null
    n=${#line}; sleep $(( 6 + n / 5 ))
  done
  pactl list short sink-inputs >> "$O/sink_inputs.txt" 2>&1
done
echo "$(date +%s.%N) END" >> "$O/timeline.log"
sleep 3
kill $LC $HB 2>/dev/null
grep -E " SafeLog|TextToSpeech|TtsService|AudioTrack|OnDevice" "$O/logcat.txt" > "$O/logcat_app.txt"
