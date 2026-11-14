#!/usr/bin/env python3
"""Pleos Connect Virtual Console (http://127.0.0.1:8088, adb forward) REST 호출.
usage: vc.py gear Drive | vc.py controls <speed_kph> <accel_pct> <brake_pct> | vc.py state
       vc.py telltale <name-substring> <On|Off>   (catalog의 Telltales 카드를 찾아 씀)
       vc.py ramp <from> <to> <seconds> <accel> <brake>  (0.2초 간격으로 속도 변경)
모든 호출을 $OUT/vc_calls.log 에 시각과 함께 남긴다."""
import json, os, sys, time, urllib.parse, urllib.request

BASE = "http://127.0.0.1:8088"
LOG = os.path.join(os.environ.get("OUT", "."), "vc_calls.log")


def call(method, path, params=None):
    url = BASE + path + ("?" + urllib.parse.urlencode(params) if params else "")
    req = urllib.request.Request(url, method=method)
    t = time.time()
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            body = r.read().decode("utf-8", "replace")
            code = r.status
    except Exception as e:  # keep going, the log shows it
        body, code = str(e), -1
    with open(LOG, "a") as f:
        f.write(f"{t:.3f} {method} {url} -> {code} {body[:300]}\n")
    return code, body


def controls(speed, accel, brake):
    return call("PUT", "/vehicle/controls", {"speed": speed, "accelerator": accel, "brake": brake})


def find_telltale(name, value_label):
    code, body = call("GET", "/vehicle/catalog")
    cat = json.loads(body)
    feats = []

    def walk(o):
        if isinstance(o, dict):
            if "write_endpoint" in o and "control" in o:
                feats.append(o)
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(cat)
    cand = [f for f in feats if "telltale" in json.dumps(f).lower()]
    for f in cand:
        ctl = f["control"]
        areas = ctl.get("areas") or f.get("areas") or []
        area = None
        for a in areas:
            txt = json.dumps(a, ensure_ascii=False).lower()
            if name.lower() in txt:
                area = a.get("value", a.get("id")) if isinstance(a, dict) else a
                break
        if area is None and name.lower() not in json.dumps(f).lower():
            continue
        val = None
        for k, lab in (f.get("value_labels") or {}).items():
            if str(lab).lower() == value_label.lower():
                val = k
        opts = ctl.get("options") or f.get("options") or []
        if val is None:
            for o in opts:
                if isinstance(o, dict) and str(o.get("label", "")).lower() == value_label.lower():
                    val = o.get("value")
        if val is None:
            val = {"on": 1, "off": 0, "blink": 2}[value_label.lower()]
        params = dict(ctl.get("params") or {})
        field = (ctl.get("field_by_area") or {}).get(str(area)) or ctl.get("field")
        params[field] = val
        if ctl.get("area_field") and area is not None:
            params[ctl["area_field"]] = area
        return f["write_endpoint"], params
    return None, None


def heartbeat():
    """Virtual Console 웹 페이지와 같은 브라우저 세션 유지 신호(1초마다). 이게 없으면 DDS 브리지가 꺼진 채 503."""
    import uuid
    cid = str(uuid.uuid4())
    while True:
        try:
            urllib.request.urlopen(urllib.request.Request(BASE + "/browser/session?client=" + cid, data=b"", method="POST"), timeout=5).read()
        except Exception as e:
            with open(LOG, "a") as f:
                f.write(f"{time.time():.3f} heartbeat error {e}\n")
        time.sleep(1)


def wait_ready(timeout=180):
    t0 = time.time()
    while time.time() - t0 < timeout:
        code, body = call("GET", "/vehicle/state")
        try:
            st = json.loads(body)
            g = st["gear"].get("bridge_ready")
            dds = st["controls"].get("dds_ready", {}).get("all")
            if g and dds:
                print("ready after", round(time.time() - t0, 1), "s")
                return True
        except Exception:
            pass
        time.sleep(2)
    print("NOT ready after", timeout, "s")
    return False


def main():
    a = sys.argv[1:]
    if a[0] == "heartbeat":
        heartbeat()
    elif a[0] == "wait_ready":
        wait_ready()
    elif a[0] == "gear":
        print(call("PUT", "/vehicle/gear", {"gear": a[1]}))
    elif a[0] == "controls":
        print(controls(a[1], a[2], a[3]))
    elif a[0] == "state":
        print(call("GET", "/vehicle/state")[1])
    elif a[0] == "catalog":
        print(call("GET", "/vehicle/catalog")[1])
    elif a[0] == "telltale":
        ep, params = find_telltale(a[1], a[2])
        print("telltale", ep, params)
        if ep:
            print(call("PUT", ep, params))
    elif a[0] == "ramp":
        v0, v1, sec, acc, brk = float(a[1]), float(a[2]), float(a[3]), a[4], a[5]
        n = max(1, int(sec / 0.2))
        for i in range(1, n + 1):
            t0 = time.time()
            controls(round(v0 + (v1 - v0) * i / n, 1), acc, brk)
            time.sleep(max(0, 0.2 - (time.time() - t0)))


if __name__ == "__main__":
    main()
