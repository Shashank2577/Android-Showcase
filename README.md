# Burrow: private on-device AI for your photos, meetings and people

Burrow is an Android app that solves three everyday jobs end to end, using small AI models that run **only on your phone**. It's built on the [Desert Ant Labs](https://desertant.com) on-device models (`ai.desertant:*:4.2.1`).

## Photos: clear the clutter

Your camera roll fills up with bursts, retakes, blurry shots and screenshots.

1. **Scan**: pick all photos or one album, and a period (30 days, 90 days, 1 year or all time). The scan runs in the background with a notification, and you can lock the phone. Photos already scanned are skipped, so rescans are quick.
2. **Similar shots**: near-duplicates taken moments apart are grouped into one *moment*. The best shot is starred and kept (eyes open, then the best faces, then the keeper score), and the others are preselected for deletion. Tap to change a choice, hold to look closer, or "Keep all".
3. **Throwaway shots and screenshots**: blurry, accidental, test or empty shots are listed separately, and so are screenshots.
4. **Move to trash**: one system-confirmed step moves the selection to Android's trash, where it stays recoverable for 30 days. A header shows how much space you can free.
5. **Find photos** by what's in them: "dog", "beach", "receipt", "smiling".

Uses **Eye**: scene and quality tags, faces, embeddings, `Eye.duplicates` and `Eye.best`.

## Meetings: record, understand, remember

1. **Record** a meeting. It keeps going with the screen off, from a foreground service with pause/stop in the notification. You can also **import** any audio or video file, or bring in **call recordings**: Android doesn't let apps record calls, but Samsung's Phone app can (Phone → Settings → Record calls), and Burrow lists those recordings for one-tap import.
2. **Processing** happens in the background, on the phone: language (Ear) → transcript with word timings (Voz, in ~10-minute chunks cut at pauses) → who spoke when (Who) → topics and highlights (Gist) → follow-ups → voice matching to people.
3. **The meeting page** shows:
   - the title (auto-named from the topics), date, length, language and topics;
   - a player with a speaker timeline: tap anywhere to jump there;
   - **Who was there**: each speaker's talk time and share, with "Sounds like Alice · 84% voice match ✓ ✗";
   - **Highlights**: key sentences spread across the meeting;
   - **Possible follow-ups**: commitments such as "I'll send…" or "we need to…" (English phrasing);
   - **Transcript** as chat bubbles coloured per speaker; tap a line to play it, the current line lights up, and you can search inside it.
4. **Search across every transcript**, and **share notes**, optionally with names, numbers, emails and addresses **redacted on the phone first** (Redact).

On the Fold 7's inner screen, the meeting list and the open meeting sit side by side, and within a meeting the overview and the transcript are side by side too.

## People: speaker profiles

Who tells speakers apart within one recording. Burrow links them across recordings:

- **Name a speaker once** ("Who is this?" → pick a person, "This is me", or a new name). Burrow learns that voice from the recording.
- **Next time** that voice is heard, the meeting suggests the person with a match score, and you confirm or dismiss it. Every confirmation improves their voice profile. You can also record a 15-second voice sample on a person's page.
- **A person's page** shows their conversations, total talk time and share per meeting, the languages they speak, **what they talk about** (Gist topics rolled up across meetings, weighted to recent ones), quotes in their own words, and links to every meeting.
- The **voices to name** inbox collects unnamed speakers from recent meetings.

*How voice matching works:* the Who SDK doesn't expose its internal voice embeddings, so Burrow computes its own compact voice signature: MFCC statistics plus median pitch over each speaker's own turns. Each match is a suggestion you confirm; nothing is linked without you. It works best for a handful of regular contacts recorded on the same phone.

## Privacy

Photos, recordings, transcripts, people and voice profiles are analysed and stored only in the app's private storage on the device. The app goes online only to download each model's weights from Hugging Face the first time it's used. Sharing notes is always your explicit action.

## Also inside

**Settings → Model lab** keeps a playground for each of the 11 Android models (Voz, Ear, Who, Clear, Eye, Moderator, Redact, Gist, Emo, Tongue, Shapes), for trying them on their own.

## Build

Requirements: JDK 17 and Android SDK 35 (or Android Studio). The build targets **arm64-v8a**, Android 11+.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (R8-optimized)
./gradlew testDebugUnitTest # voice signatures, speaker attribution, chunking, follow-ups
```

The release APK is signed with the debug key so it installs directly; use your own signing config before publishing. To run on an x86_64 emulator, add `"x86_64"` to `abiFilters` in `app/build.gradle.kts`.

Optional license key: Desert Ant models are free for most apps. If you have a publishable key, enter it in Settings or put `desertant.apiKey=pk_live_...` in `local.properties`.

## Notes and limits

- **Size:** the APK is about 225 MB, because each native model carries its own runtime. First use downloads the weights: Voz about 337 MB, Eye 66 MB, the others 1–32 MB.
- **Speed:** a few hundred photos take a few minutes. A meeting takes a fraction of its length to process once the models are on the phone.
- **Long recordings:** up to 2 hours. Very long recordings may hit the phone's memory limit, and the app says so if that happens.
- **Transcription languages:** Voz covers 25 European languages. Meetings in other languages are flagged.

## License

The Desert Ant models and SDK are under the [Desert Ant Labs Source-Available License](https://license.desertant.com/): free for most apps, with a commercial license required at scale.
