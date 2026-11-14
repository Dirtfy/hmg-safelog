#!/usr/bin/env python3
"""Pleos Connect Emulator 녹화 v2: 차량 신호를 Virtual Console 웹 화면에서 '눌러서' 넣고, 그 조작 화면도 녹화한다.

- Virtual Console(http://127.0.0.1:8088, adb forward)을 Xvfb 위의 실제 Chrome 창으로 띄우고(Playwright),
  가속/브레이크 페달 패드를 누르고 떼고, P/R/N/D 버튼과 API 브라우저(경고등 TPMS)를 클릭한다.
  페이지 자체 물리 계산이 속도를 만들고 페이지가 REST로 차량 쪽에 넘긴다(직접 REST 호출 없음).
- 화면에는 마우스 위치를 보여 주는 점(누르면 노란색)을 페이지에 그린다. 조작은 Playwright 입력 이벤트.
- 녹화: Pleos 화면 = adb emu screenrecord (scene_X.webm), 조작 화면 = ffmpeg x11grab (vc_X.mp4).
- 앱 쪽 입력(그대로): 경고등 값(PRIVATE_CAR_SAFETY 권한 없음)·운전자 음성(마이크 없음)·화면 버튼은 am broadcast.
결과: $OUT/scene_{A,B,C}.webm, vc_{A,B,C}.mp4, timeline.log, vc_speed.log, logcat.txt, app_frames/
"""
import os
import subprocess
import sys
import time

from playwright.sync_api import sync_playwright

O = os.environ["OUT"]
PKG = "com.hmg.safelog.poc"
DISPLAY = os.environ.get("DISPLAY", ":99")
VW, VH = 1280, 800
TL = open(os.path.join(O, "timeline.log"), "a")
SPD = open(os.path.join(O, "vc_speed.log"), "a")


def ts(msg):
    line = f"{time.time():.3f} {msg}"
    print(line, flush=True)
    TL.write(line + "\n"); TL.flush()


def sh(cmd, timeout=60):
    try:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def adb(cmd, timeout=60):
    return sh(f"adb shell {cmd}", timeout)


def voice(text):
    ts(f"VOICE {text}")
    adb(f"am broadcast -a {PKG}.VOICE --es text '\"{text}\"' -p {PKG}")


def button(b):
    ts(f"BUTTON {b}")
    adb(f"am broadcast -a {PKG}.UI --es button {b} -p {PKG}")


def front():
    adb(f"am start -n {PKG}/com.hmg.safelog.app.LiveActivity")


ffm = None


def rec_start(scene):
    global ffm
    ts(f"REC_START {scene}")
    ffm = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "x11grab", "-draw_mouse", "0", "-framerate", "10",
         "-video_size", f"{VW}x{VH}", "-i", DISPLAY + ".0", "-c:v", "libx264", "-preset", "ultrafast",
         "-pix_fmt", "yuv420p", os.path.join(O, f"vc_{scene}.mp4")],
        stdin=subprocess.PIPE)
    ts(f"VC_REC_START {scene}")
    sh(f"adb emu screenrecord start --time-limit 170 {O}/scene_{scene}.webm", 20)
    ts(f"EMU_REC_START {scene}")


def rec_stop(scene):
    sh("adb emu screenrecord stop", 20)
    ts(f"EMU_REC_STOP {scene}")
    ffm.communicate(b"q", timeout=30)
    ts(f"REC_STOP {scene}")
    time.sleep(2)


CURSOR_JS = """
(() => {
  const add = () => {
    if (document.getElementById('__cur')) return;
    const c = document.createElement('div');
    c.id = '__cur';
    c.style.cssText = 'position:fixed;z-index:2147483647;left:-50px;top:-50px;width:30px;height:30px;' +
      'margin:-15px 0 0 -15px;border-radius:50%;border:4px solid #fff;background:rgba(255,64,129,.6);' +
      'box-shadow:0 0 10px #000;pointer-events:none;transition:transform .08s,background .08s';
    document.body.appendChild(c);
    const mv = e => { c.style.left = e.clientX + 'px'; c.style.top = e.clientY + 'px'; };
    document.addEventListener('pointermove', mv, true);
    document.addEventListener('mousemove', mv, true);
    document.addEventListener('pointerdown', () => { c.style.transform = 'scale(1.7)'; c.style.background = 'rgba(255,214,0,.85)'; }, true);
    document.addEventListener('pointerup', () => { c.style.transform = ''; c.style.background = 'rgba(255,64,129,.6)'; }, true);
  };
  if (document.body) add(); else document.addEventListener('DOMContentLoaded', add);
  window.__pad = (id, down, show) => {
    const el = document.getElementById(id), r = el.getBoundingClientRect();
    const x = r.x + r.width / 2, y = r.y + r.height / 2, c = document.getElementById('__cur');
    // 페이지 핸들러가 setPointerCapture(pointerId)를 부르는데, 스크립트가 만든 포인터는 브라우저에 없어 예외가 나고
    // 페달이 눌리지 않았다. 캡처 실패만 삼키고 나머지 핸들러는 그대로 돈다.
    if (!el.__safeCapture) {
      el.__safeCapture = true;
      el.setPointerCapture = function (id) { try { HTMLElement.prototype.setPointerCapture.call(this, id); } catch (e) {} };
    }
    if (show && c) { c.style.left = x + 'px'; c.style.top = y + 'px'; }
    el.dispatchEvent(new PointerEvent(down ? 'pointerdown' : 'pointerup',
      {bubbles: true, clientX: x, clientY: y, pointerId: 7, pointerType: 'mouse', isPrimary: true}));
  };
  window.__drive = (target, rate, hold) => new Promise(done => {
    const v0 = vehicle.speed_kph, up = Math.abs(target - v0) / rate, sign = target >= v0 ? 1 : -1;
    const t0 = performance.now();
    let cur = null;
    const set = p => { if (p === cur) return; if (cur) __pad(cur, false, true); cur = p; if (p) __pad(p, true, true); };
    const iv = setInterval(() => {
      const t = (performance.now() - t0) / 1000, want = v0 + sign * Math.min(up, t) * rate, v = vehicle.speed_kph;
      set(v < want - 0.3 ? 'acceleratorPad' : v > want + 3 ? 'brakePad' : null);
      if (t > up + hold) { set(null); clearInterval(iv); done(vehicle.speed_kph); }
    }, 50);
  });
})();
"""


class Console:
    """Virtual Console 화면 조작. 속도는 페이지의 vehicle.speed_kph(페이지 물리 계산 값)."""

    def __init__(self, page):
        self.page = page
        self.m = page.mouse
        self.pressed = None
        self.x, self.y = VW / 2, VH / 2

    def speed(self):
        try:
            v = float(self.page.evaluate("vehicle.speed_kph"))
        except Exception:
            v = float(self.page.inner_text("#speedValue") or 0)
        SPD.write(f"{time.time():.3f} {v:.2f}\n")
        return v

    def center(self, sel):
        b = self.page.locator(sel).first.bounding_box()
        return b["x"] + b["width"] / 2, b["y"] + b["height"] / 2

    def move(self, sel, steps=14):
        x, y = self.center(sel)
        self.m.move(x, y, steps=steps)
        self.x, self.y = x, y

    def click(self, sel, label):
        ts(f"VC_CLICK {label}")
        self.move(sel)
        time.sleep(0.25)
        self.m.down(); time.sleep(0.15); self.m.up()

    def pad(self, pad_id, down, show=True):
        """페달 패드를 페이지 자신의 pointerdown/pointerup 핸들러로 누르거나 뗀다(마우스 클릭과 같은 경로)."""
        self.page.evaluate("([i, d, s]) => window.__pad(i, d, s)", [pad_id, down, show])

    def gear(self, g):
        """실차처럼 브레이크를 밟은 채 기어를 바꾼다(Virtual Console은 브레이크 없이 P에서 나가면 P로 되돌림)."""
        ts(f"VC_BRAKE_HOLD for gear {g}")
        self.pad("brakePad", True, False)
        time.sleep(1.2)
        self.click(f"#gearButtons button[title='{g}']", f"gear {g}")
        t0 = time.time()
        while time.time() - t0 < 15 and self.page.evaluate("currentGear") != g:
            time.sleep(0.3)
        # 앱(Pleos Vehicle SDK)이 기어를 받았는지 앱 로그로 확인하고, 6초 안에 안 오면 같은 버튼을 한 번 더 누른다
        # (R→P 변경이 SDK 콜백으로 오지 않은 적이 있음)
        want = {"Drive": "DRIVE", "Reverse": "REVERSE", "Park": "PARK", "Neutral": "NEUTRAL"}[g]
        for again in range(2):
            if self.app_saw_gear(want, t0, 6):
                break
            ts(f"VC_GEAR_NOT_RECEIVED {g} -> press again")
            self.click(f"#gearButtons button[title='{g}']", f"gear {g} again")
            t0 = time.time()
        time.sleep(0.8)
        self.pad("brakePad", False, False)
        time.sleep(0.6)
        st = self.page.evaluate("[currentGear, document.getElementById('gearStatus').textContent]")
        ts(f"VC_GEAR_NOW {st}")

    @staticmethod
    def app_saw_gear(want, since, timeout):
        end = time.time() + timeout
        while time.time() < end:
            try:
                for ln in open(os.path.join(O, "logcat.txt"), errors="replace"):
                    if f"SIG gear={want}" in ln and float(ln.split()[0]) >= since - 0.5:
                        return True
            except (OSError, ValueError, IndexError):
                pass
            time.sleep(0.5)
        return False

    def drive_to(self, target, rate=3.0, hold=0.0, tag="drive"):
        """사람이 페달을 조금씩 밟듯, 페이지 안에서 50ms마다 ACCEL/BRAKE 패드를 눌렀다 뗐다 하며 목표 속도를 따라간다.
        (CDP 왕복이 러너에서 1초 넘게 걸려 바깥 루프로는 페달을 조절할 수 없었다)"""
        v0 = self.speed()
        ts(f"VC_DRIVE {tag} {v0:.0f}->{target}")
        v = self.page.evaluate("([t, r, h]) => window.__drive(t, r, h)", [target, rate, hold])
        ts(f"VC_DRIVE_END {tag} v={v:.1f}")
        return v

    def stop(self, rate=4.0, tag="stop"):
        v0 = self.speed()
        self.drive_to(0, rate=rate, tag=tag)
        self.pad("brakePad", True)
        t0 = time.time()
        while self.speed() > 0.2 and time.time() - t0 < 8:
            time.sleep(0.2)
        time.sleep(1.0)
        self.pad("brakePad", False)
        ts(f"VC_STOPPED {tag} from {v0:.0f}")

    def hold(self, pad, until=None, max_s=5.0, tag=""):
        """한 페달을 마우스로 꾹 누르고 있기(오조작 장면)."""
        ts(f"VC_HOLD {pad} {tag}")
        self.move("#acceleratorPad" if pad == "accel" else "#brakePad", steps=8)
        self.m.down()
        t0 = time.time()
        while time.time() - t0 < max_s:
            v = self.speed()
            if until is not None and until(v):
                break
            time.sleep(0.03)
        self.m.up()
        ts(f"VC_RELEASE {pad} v={self.speed():.1f}")

    def telltale(self, area_label, value_label):
        """Emulation Controls(API 브라우저)의 Telltales 카드에서 area를 고르고 On을 누른다. 카드가 0.9초마다 다시 그려져
        요소가 바뀔 수 있어 매번 새로 찾고, 끝내 실패하면 같은 값을 REST로 넣고 그렇게 기록한다."""
        p = self.page
        self.click("#apiBrowserOpen", "API browser")
        time.sleep(1.0)
        self.move("#apiSearch")
        self.m.down(); self.m.up()
        p.keyboard.type("Telltale", delay=90)
        time.sleep(1.0)
        card = lambda: p.locator("article.api-card", has=p.locator("h3", has_text="Telltales")).first
        done = False
        for attempt in range(6):
            try:
                sel = card().locator("select.api-area")
                b = sel.bounding_box(timeout=3000)
                if b:
                    self.m.move(b["x"] + b["width"] / 2, b["y"] + b["height"] / 2, steps=10)
                ts(f"VC_SELECT telltale area {area_label}")
                sel.select_option(label=area_label, timeout=5000)
                time.sleep(1.0)
                btn = card().locator("button.api-action", has_text=value_label).first
                b = btn.bounding_box(timeout=3000)
                self.m.move(b["x"] + b["width"] / 2, b["y"] + b["height"] / 2, steps=10)
                time.sleep(0.2)
                ts(f"VC_CLICK telltale {area_label} {value_label}")
                btn.click(timeout=5000)
                done = True
                break
            except Exception as e:
                ts(f"VC_TELLTALE_RETRY {attempt} {type(e).__name__}")
                time.sleep(1.0)
        time.sleep(1.8)
        try:
            ts("VC_TELLTALE_STATUS " + card().locator(".api-status").inner_text(timeout=3000))
        except Exception as e:
            ts(f"VC_TELLTALE_STATUS ? {type(e).__name__}")
        p.keyboard.press("Escape")
        ts("VC_API_BROWSER_CLOSED")
        if not done:
            ts(f"VC_TELLTALE_REST_FALLBACK {area_label} {value_label}")
            sh(f"python3 poc/pleos/vc.py telltale {area_label} {value_label}")

    @staticmethod
    def center_of(loc):
        b = loc.bounding_box()
        return b["x"] + b["width"] / 2, b["y"] + b["height"] / 2


def main():
    with sync_playwright() as pw:
        br = pw.chromium.launch(
            channel="chrome", headless=False,
            args=["--kiosk", f"--window-size={VW},{VH}", "--window-position=0,0", "--no-first-run",
                  "--disable-infobars", "--noerrdialogs", "--disable-session-crashed-bubble", "--hide-scrollbars"])
        ctx = br.new_context(no_viewport=True)
        ctx.add_init_script(CURSOR_JS)
        # 3D 고속도로(three.js WebGL)는 GPU 없는 러너에서 CPU를 다 써 버려(클릭 하나에 40초) 막고 2D 도로(Forest 테마)로 띄운다
        ctx.add_init_script("try{localStorage.setItem('virtual-console-theme','forest')}catch(e){}")
        ctx.route("**/vendor/**", lambda r: r.abort())
        ctx.route("**/highway-view.js", lambda r: r.abort())
        ctx.route(lambda u: u.endswith((".glb", ".gltf", ".hdr")), lambda r: r.abort())
        page = ctx.new_page()
        page.goto("http://127.0.0.1:8088/", wait_until="load")
        time.sleep(6)
        vc = Console(page)
        page.screenshot(path=os.path.join(O, "vc_loaded.png"))
        # 테마: 3D 고속도로(WebGL)는 GPU 없는 러너에서 무거우므로 2D(Forest)로. P에서만 바뀜
        theme = page.inner_text("#themeToggle")
        ts(f"VC_THEME {theme}")
        if "Highway" in theme:
            vc.click("#themeToggle", "theme Forest")
            time.sleep(2)
        page.screenshot(path=os.path.join(O, "vc_ready.png"))
        ts(f"VC_VIEWPORT {page.evaluate('[innerWidth, innerHeight]')}")

        t_ = time.time(); page.evaluate("1"); ts(f"VC_RTT {time.time() - t_:.3f}s")
        vc.gear("Park")
        ts("READY")

        # ---------------- 장면 A: 아침 병원 가는 길 · 주행 중 경고등(TPMS) → 쉬운 말 해설 → "응" → 정비소 경유지
        front(); time.sleep(3)
        rec_start("A")
        time.sleep(2)
        vc.gear("Drive")
        time.sleep(1)
        vc.drive_to(40, rate=3.0, hold=3, tag="A start")
        vc.telltale("TPMS", "On")
        # 서드파티 앱은 경고등 API 권한(PRIVATE_CAR_SAFETY)이 없어 같은 값을 앱에 직접 넘긴다
        ts("APP_TELLTALE TPMS On")
        adb(f"am broadcast -a {PKG}.TELLTALE --es type TPMS --ez on true -p {PKG}")
        vc.drive_to(40, rate=3.0, hold=10, tag="A cruise")
        voice("응, 안내해 줘")
        vc.drive_to(40, rate=3.0, hold=7, tag="A cruise2")
        page.screenshot(path=os.path.join(O, "vcA_end.png"))
        ts("FRONT"); front()
        vc.drive_to(40, rate=3.0, hold=4, tag="A cruise3")
        rec_stop("A")
        # 녹화 밖: 병원까지 계속 주행 후 주차(거리·시간이 돌아보기 요약에 들어감)
        for i in range(4):
            vc.drive_to(45, rate=3.0, hold=25, tag=f"A road {i}")
            front()
        vc.stop(rate=4.0, tag="A arrive")
        vc.gear("Park")
        time.sleep(5)

        # ---------------- 장면 B: 낮, 마트 주차장 후진 · 페달 오조작 의심
        front(); time.sleep(3)
        rec_start("B")
        time.sleep(3)
        vc.gear("Reverse")
        time.sleep(4)
        # 브레이크 대신 가속 페달을 꾹(후진) — 페이지 물리: 후진은 가속 0.45배
        vc.hold("accel", until=lambda v: v >= 15, max_s=4, tag="wrong pedal")
        vc.hold("brake", until=lambda v: v <= 0.2, max_s=4, tag="brake")
        warned = "다른 페달" in sh("grep 'SPEAK' " + O + "/logcat.txt | tail -5")
        ts(f"PEDAL_WARNING_SPOKEN {warned}")
        time.sleep(1)
        vc.gear("Park")
        time.sleep(5)
        page.screenshot(path=os.path.join(O, "vcB_parked.png"))
        sh(f"adb exec-out screencap -p > {O}/sB_parked.png", 30)
        button("record")
        time.sleep(11)
        sh(f"adb exec-out screencap -p > {O}/sB_record.png", 30)
        button("close")
        time.sleep(2)
        rec_stop("B")

        # ---------------- 장면 C: 저녁, 집에 도착 → 주차하면 오늘 요약이 가족에게 자동으로 감 → 돌아보기
        vc.gear("Drive")
        vc.drive_to(30, rate=3.0, hold=40, tag="C road")
        front(); time.sleep(1)
        rec_start("C")
        vc.drive_to(30, rate=3.0, hold=4, tag="C near home")
        vc.stop(rate=4.0, tag="C home")
        time.sleep(1)
        vc.gear("Park")
        time.sleep(7)
        button("review")
        time.sleep(11)
        page.screenshot(path=os.path.join(O, "vcC_end.png"))
        sh(f"adb exec-out screencap -p > {O}/sC_sent.png", 30)
        rec_stop("C")
        ctx.close(); br.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        ts(f"ERROR {e!r}")
        raise
