import json, sys, wave, os, time
lines_path, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
from melo.api import TTS
import nltk
lines = json.load(open(lines_path, encoding="utf-8"))
models = {}
def get(lang):
    if lang not in models:
        models[lang] = TTS(language="KR" if lang == "ko" else "EN", device="cpu")
    return models[lang]
# voice mapping: narrator = default speaker, app = alternate English accent / slightly faster Korean
EN_SPK = {"narrator": "EN-US", "app": "EN-BR"}
durs = {}
for l in lines:
    m = get(l["lang"])
    spk = m.hps.data.spk2id
    if l["lang"] == "ko":
        sid = spk["KR"]; speed = float(l.get("speed", 1.0)) * (1.08 if l.get("voice") == "app" else 1.0)
    else:
        sid = spk[EN_SPK.get(l.get("voice"), "EN-US")]; speed = float(l.get("speed", 1.0))
    p = os.path.join(out, l["id"] + ".wav")
    t = time.time()
    m.tts_to_file(l["text"], sid, p, speed=speed, quiet=True)
    with wave.open(p) as w:
        durs[l["id"]] = round(w.getnframes() / w.getframerate(), 3)
        print(l["id"], l["lang"], len(l["text"]), "chars", durs[l["id"]], "s", w.getframerate(), "Hz", w.getnchannels(), "ch", round(time.time()-t,1), "s synth", flush=True)
json.dump(durs, open(os.path.join(out, "durations.json"), "w"), indent=1)
