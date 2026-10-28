# Desert Ant Showcase (Android)

An Android app that runs **every Desert Ant Labs model that ships for Android**, on the device, with real inputs: your microphone, your audio and video files, your photos, your typing and your finger on a canvas. It also chains the models into end-to-end pipelines.

Built on the official SDK (`ai.desertant:*:4.2.1` on Maven Central). The models run locally through LiteRT. Weights download once from Hugging Face; after that, text, audio and photos never leave the phone.

## What's inside

| Model | Playground |
| --- | --- |
| **Voz**: speech to text | Record or pick audio/video. Get the transcript plus word chips with timestamps; tap a word to hear it. An optional Ear check warns when the language isn't one Voz supports. Share the transcript. |
| **Ear**: spoken language ID | Identifies the language across 102 languages, with a configurable window count, reliability, and whether Voz can transcribe it. |
| **Who**: speaker diarization | Per-speaker timeline lanes, talk-time share, a minimum-share filter (`keepingSpeakers`), and tap-to-play turns. |
| **Clear**: speech enhancement | Strength, loudness presets (Apple Podcasts, Spotify, YouTube, Broadcast or off) and output sample rate. A/B playback of before and after, measured LUFS and true peak, and WAV export. |
| **Eye**: photo understanding | *One photo:* tags at the recommended or strict cutoff, kind, aesthetic/keeper/cover scores, rotation, faces with pose and expressions, and box overlays for faces, landmarks, people, pets and the best square crop. *Library:* pick up to 30 photos to find the best shot (`Eye.best`), near-duplicate groups (`Eye.duplicates`), tag search, and similar photos (`Eye.similar`). |
| **Moderator**: NSFW detection | Threshold, policy and quality options, per-region scores, and a preview that stays blurred while unchecked or flagged. |
| **Redact**: PII redaction | Color-highlighted detections, a per-category filter (ORG is opt-in), a confidence slider, and a round-trip demo: edit the "LLM output" and `restore()` fills the originals back in. |
| **Gist**: topic tagging | Top topics or all 36 scores, plus a channel roll-up across several posts (`channelTopics`). |
| **Emo**: emoji suggestions | Live suggestions as you type, with a count and skin-tone choice; tap one to insert it. |
| **Tongue**: text language ID | Live detection per keystroke (bundled, no download), candidates, "too close to call", the reliability verdict and the script router. |
| **Shapes**: sketch to shape | A drawing canvas: each stroke becomes a clean shape, or stays as ink when it isn't one. Arrows get heads, and a scribble erases what it covers. Rotation and confidence options. |

**Pipelines**

- **Private meeting notes**: Clear → Ear → Voz → Who → Redact → Gist. Produces a "who said what" transcript with PII removed and topics tagged, with per-step timings, and lets you share the notes.
- **Smart compose**: Tongue → Redact → Gist → Emo, live on a message as you type.

**Settings** has download-all, per-model download and unload (frees native memory; the files stay cached), cache size, and an optional license key.

Models that aren't on Android in SDK 4.2.1 (Align, Clips, Title, Uhm, and the closed-beta Face, Schemer and Toxic) are not included.

## Build & run

Requirements: Android Studio Ladybug or newer (or JDK 17 + Android SDK 35), and a device or emulator on **arm64-v8a or x86_64**, API 24+.

```bash
./gradlew installDebug        # or open the folder in Android Studio and press Run
```

CI (`.github/workflows/android.yml`) builds a debug APK on every push and uploads it as an artifact.

### Optional license key

Most apps can use the SDK for free without a key. If Desert Ant Labs gave you a publishable key, either enter it in **Settings** or put it in `local.properties` (not committed):

```properties
desertant.apiKey=pk_live_...
```

## Notes

- First use of each model downloads its weights (Voz is about 337 MB, Eye 66 MB, the rest 1–32 MB). Do it on Wi-Fi, or press **Download all** in Settings.
- Audio decoding uses `MediaExtractor`/`MediaCodec`, so WAV, MP3, AAC/M4A, OGG/Opus, FLAC and the audio of MP4/WebM all work. Input is capped at 10 minutes.
- Speeds on a phone CPU running LiteRT are slower than Desert Ant's Neural Engine benchmarks. Each result card shows the measured time.

## License

The Desert Ant models and SDK are under the [Desert Ant Labs Source-Available License](https://license.desertant.com/): free for most apps, with a commercial license required at scale.
