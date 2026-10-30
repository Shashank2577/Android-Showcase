"""Word/character error rates of on-device ASR candidates on Google FLEURS test clips.

Usage: venv/bin/python evaluate.py N   (N utterances per language)
"""
import csv, json, re, sys, time, unicodedata
from pathlib import Path

import jiwer
import numpy as np
import onnxruntime as ort
import sherpa_onnx
import soundfile as sf

N = int(sys.argv[1]) if len(sys.argv) > 1 else 50
ROOT = Path(__file__).parent
LANGS = {"hi": "hi_in", "ta": "ta_in", "bn": "bn_in"}


def norm(t: str) -> str:
    t = unicodedata.normalize("NFC", t.lower())
    t = re.sub(r"[।॥.,!?;:\"'()\[\]{}\-–—|/\\]", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def clips(lang):
    d = ROOT / "fleurs" / LANGS[lang]
    rows = list(csv.reader(open(d / "test.tsv"), delimiter="\t", quoting=csv.QUOTE_NONE))
    out = []
    seen = set()
    for r in rows:
        if r[0] in seen:
            continue
        seen.add(r[0])
        p = next((d / "audio").rglob(r[1]), None)
        if p:
            out.append((p, r[3] if len(r) > 3 else r[2]))
        if len(out) >= N:
            break
    return out


def load(p):
    a, sr = sf.read(p, dtype="float32")
    if a.ndim > 1:
        a = a.mean(1)
    assert sr == 16000, sr
    return a


def sherpa_runner(rec):
    def run(audio):
        s = rec.create_stream()
        s.accept_waveform(16000, audio)
        rec.decode_stream(s)
        return s.result.text
    return run


# --- Burrow's own Android pipeline, ported line for line from MelFeatures.kt + CtcDecoder.kt ---
def burrow_mel(x):
    x = x.astype(np.float32)
    y = np.empty_like(x)
    y[0] = x[0]
    y[1:] = x[1:] - 0.97 * x[:-1]
    win = (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(400) / 400)).astype(np.float32)
    n_frames = 1 + len(x) // 160
    padded = np.pad(y, (256, 256 + 512))
    frames = np.stack([padded[f * 160 + 56: f * 160 + 56 + 400] * win for f in range(n_frames)])
    spec = np.fft.rfft(np.pad(frames, ((0, 0), (56, 56))), n=512)
    power = (spec.real ** 2 + spec.imag ** 2).astype(np.float32)

    def hz2mel(h):
        h = np.asarray(h, float); f = 200 / 3; m = h / f
        return np.where(h >= 1000, 1000 / f + np.log(h / 1000) / (np.log(6.4) / 27), m)

    def mel2hz(m):
        m = np.asarray(m, float); f = 200 / 3
        return np.where(m >= 1000 / f, 1000 * np.exp(np.log(6.4) / 27 * (m - 1000 / f)), f * m)

    freqs = np.arange(257) * 16000 / 512
    pts = mel2hz(np.linspace(hz2mel(0), hz2mel(8000), 82))
    fb = np.zeros((80, 257), np.float32)
    for b in range(80):
        l, c, r = pts[b], pts[b + 1], pts[b + 2]
        fb[b] = np.maximum(0, np.minimum((freqs - l) / (c - l), (r - freqs) / (r - c))) * (2 / (r - l))
    m = np.log(power @ fb.T + 2 ** -24)
    m = (m - m.mean(0)) / (m.std(0, ddof=1) + 1e-5)
    return m.astype(np.float32)  # [T, 80]


def burrow_runner(model, tokens):
    sess = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
    inputs = sess.get_inputs()
    feat = next(i for i in inputs if len(i.shape) == 3)
    bands_first = feat.shape[1] == 80 or feat.shape[2] != 80
    length = next((i for i in inputs if i.name != feat.name), None)
    toks = {}
    for line in open(tokens, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        cut = line.rfind(" ")
        toks[int(line[cut + 1:])] = line[:cut]
    blank = next((k for k, v in toks.items() if v in ("<blk>", "<blank>")), max(toks))

    def run(audio):
        m = burrow_mel(audio)
        x = m.T[None] if bands_first else m[None]
        feeds = {feat.name: x}
        if length is not None:
            feeds[length.name] = np.array([m.shape[0]], np.int64)
        logits = sess.run(None, feeds)[0][0]
        ids = logits.argmax(-1)
        out, prev = [], -1
        for i in ids:
            if i != blank and i != prev:
                out.append(toks.get(int(i), ""))
            prev = i
        return "".join(out).replace("▁", " ").strip()
    return run


def nemo(model, tokens):
    return sherpa_runner(sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=str(model), tokens=str(tokens), num_threads=4))


def omni(model, tokens):
    return sherpa_runner(sherpa_onnx.OfflineRecognizer.from_omnilingual_asr_ctc(model=str(model), tokens=str(tokens), num_threads=4))


M = ROOT / "m"
CANDIDATES = {
    "IndicConformer-hi int8 (Burrow 3.1, sherpa)": ({"hi"}, lambda l: nemo(M / "ngiftsun-hi/model.int8.onnx", M / "ngiftsun-hi/tokens.txt")),
    "IndicConformer-hi int8 (Burrow 3.1, Burrow's own pipeline)": ({"hi"}, lambda l: burrow_runner(M / "ngiftsun-hi/model.int8.onnx", M / "ngiftsun-hi/tokens.txt")),
    "IndicConformer-hi int8-matmul": ({"hi"}, lambda l: nemo(M / "ngiftsun-hi/model.int8-matmul.onnx", M / "ngiftsun-hi/tokens.txt")),
    "IndicConformer 22-lang pack int8 (per language)": ({"hi", "ta", "bn"}, lambda l: nemo(M / f"parismita/{l}/model.int8.onnx", M / "parismita/tokens.txt")),
    "Omnilingual CTC-300M fp32": ({"hi", "ta", "bn"}, lambda l: omni(M / "omni/model.onnx", M / "omni/tokens.txt")),
}
for extra in sys.argv[2:]:
    name, path, toks, kind = extra.split("|")
    CANDIDATES[name] = ({"hi", "ta", "bn"}, (lambda p, t, k: lambda l: (omni if k == "omni" else nemo)(Path(p), Path(t)))(path, toks, kind))

results = {}
for lang in LANGS:
    data = [(load(p), norm(t)) for p, t in clips(lang)]
    secs = sum(len(a) for a, _ in data) / 16000
    for name, (langs, make) in CANDIDATES.items():
        if lang not in langs:
            continue
        try:
            run = make(lang)
        except Exception as e:
            print(f"{lang} {name}: could not load ({e})", flush=True)
            continue
        t0 = time.time()
        hyps = [norm(run(a)) for a, _ in data]
        dt = time.time() - t0
        refs = [r for _, r in data]
        wer = jiwer.wer(refs, hyps)
        cer = jiwer.cer(refs, hyps)
        results.setdefault(name, {})[lang] = {"wer": round(wer * 100, 1), "cer": round(cer * 100, 1), "rtf": round(dt / secs, 3), "n": len(data)}
        print(f"{lang} {name}: WER {wer*100:.1f}%  CER {cer*100:.1f}%  RTF {dt/secs:.3f}  (n={len(data)})", flush=True)
        print("   e.g. ref:", refs[0][:90], "\n        hyp:", hyps[0][:90], flush=True)

json.dump(results, open(ROOT / "results.json", "w"), ensure_ascii=False, indent=1)
