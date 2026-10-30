# Speech model evaluation

How the speech models behind Burrow's talks were chosen for languages Voz doesn't cover. Everything here runs on a laptop CPU with the same ONNX files the app downloads.

## Results

60 utterances per language from the Google FLEURS **test** split. Punctuation is removed and text lower-cased before scoring. WER is the word error rate and CER the character error rate (lower is better). RTF is the real-time factor on a 4-thread laptop CPU (0.04 means 1 minute of audio takes about 2.4 s).

| Model | Hindi WER / CER | Tamil WER / CER | Bengali WER / CER | Download | RTF |
| --- | --- | --- | --- | --- | --- |
| **IndicConformer, 22-language pack, int8** ([parismitaglobalsolutions](https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx)), run through sherpa-onnx | **11.5 / 5.0** | **31.7 / 15.1** | **16.3 / 4.5** | ~190 MB per language | 0.04 |
| same, run through **Burrow's own pipeline** (`MelFeatures.kt` + `CtcDecoder.kt`) | 11.8 / 5.2 | 31.0 / 14.8 | 16.5 / 4.4 | | 0.04 |
| IndicConformer Hindi int8 ([ngiftsun](https://huggingface.co/ngiftsun/indicconformer-hi-int8-sherpa)), used in Burrow 3.1 | 12.0 / 4.9 | — | — | 134 MB | 0.07 |
| IndicConformer Hindi int8-matmul (ngiftsun) | 11.5 / 4.9 | — | — | 189 MB | 0.03 |
| Omnilingual ASR 300M CTC, fp32 ([k2-fsa](https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-2025-11-12)) | 32.8 / 14.9 | 56.4 / 16.9 | 34.8 / 8.8 | 1.3 GB | 0.13 |
| Omnilingual ASR 300M CTC, int8 ([k2-fsa](https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12)) | 33.4 / 14.8 | 56.5 / 16.8 | 34.9 / 8.8 | 349 MB | 0.11 |

Notes:

- Tamil WER is high for every model because Tamil joins many words into one, so a single wrong suffix costs a whole word. Its CER (about 15%) is the fairer measure.
- On a real FLEURS Hindi clip, Burrow's Kotlin features match the Python reference within float rounding (mean absolute difference 9e-6), and the decoded text is identical. See `SpeechTest.dumpFeaturesForComparison`.

## What Burrow does with this

| Talk language (from Ear, or chosen by hand) | Model |
| --- | --- |
| One of Voz's 25 European languages | Voz (Desert Ant) |
| One of India's 22 scheduled languages | IndicConformer, that language's pack |
| Anything else | Omnilingual 300M int8 (1,600+ languages) |

## Not tested yet

- **SraVaani-1.0** (IISc/ARTPARK; 65 Indian languages and dialects; MIT) and the official 22-language **IndicConformer 600M** are gated on Hugging Face. Downloading them needs an account that has accepted their terms, plus an access token. SraVaani also ships as TorchScript, so it would need converting to ONNX first.
- Whisper (large-v3, turbo, or the Hinglish fine-tunes in the parismita repo) needs an encoder-decoder runtime that Burrow doesn't have yet.

## Re-run

```bash
python3 -m venv venv && venv/bin/pip install sherpa-onnx onnxruntime soundfile jiwer numpy
# Place FLEURS test audio under fleurs/<hi_in|ta_in|bn_in>/ and the models under m/; see the paths in evaluate.py.
venv/bin/python evaluate.py 60
```
