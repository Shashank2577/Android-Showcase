# Burrow: a private notebook for your photos, talks and people

Burrow is an Android app that handles six everyday jobs end to end, using small AI models that run **only on your phone**. These are jobs people usually hand to cloud apps. It's built on the [Desert Ant Labs](https://desertant.com) on-device models (`ai.desertant:*:4.2.1`).

**v3** is a redesign in the *hand-drawn minimal* theme (see [`design/burrow-a/theme`](design/burrow-a/theme/THEME.md)), built around the product rethink in [`design/burrow-a/PRODUCT.md`](design/burrow-a/PRODUCT.md).

| Job | Where | Models |
| --- | --- | --- |
| Tidy: keep the best shot of each moment, let the rest go | photos | Eye |
| Private sweep and vault: IDs, cards, medical and intimate photos taken out of the gallery and encrypted | photos → private things | Eye, then Moderator to confirm intimate photos |
| Share safely: check a photo before it leaves | Android share sheet → *Check with Burrow* | Eye, Moderator, plus the photo's EXIF data |
| Talks: record a conversation and get notes, who spoke when, and follow-ups | talks | Clear, Ear, Voz, Who, Gist, Redact; Hindi via AI4Bharat IndicConformer |
| People: name a voice once and Burrow suggests that person afterwards | people | Who, plus Burrow's own voice signature |
| Mask: swap secrets for placeholders before text goes to an AI, then put them back in the reply | today → mask, any app's text-selection menu → *Mask with Burrow* | Redact, Tongue |

The bottom navigation is four hand-lettered words: **today · photos · talks · people**. *Today* shows only what needs you right now. The padlock in the corner opens the privacy page.

![screens](design/burrow-a/theme/shots/03-today.png)

## Photos: clear the clutter

Your camera roll fills up with bursts, retakes, blurry shots and screenshots.

1. **Scan**: pick all photos or one album, and a period (30 days, 90 days, 1 year or all time). The scan runs in the background with a notification, and you can lock the phone. Photos already scanned are skipped, so rescans are quick.
2. **Similar shots**: near-duplicates taken moments apart are grouped into one *moment*. The best shot is starred and kept (eyes open, then the best faces, then the keeper score), and the others are preselected for deletion. Tap to change a choice, hold to look closer, or "Keep all".
3. **Throwaway shots and screenshots**: blurry, accidental, test or empty shots are listed separately, and so are screenshots.
4. **Move to trash**: one system-confirmed step moves the selection to Android's trash, where it stays recoverable for 30 days. A header shows how much space you can free.
5. **Find photos** by what's in them: "dog", "beach", "receipt", "smiling".

Uses **Eye**: scene and quality tags, faces, embeddings, `Eye.duplicates` and `Eye.best`.

## Talks: record, understand, remember

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

## Private sweep and vault

During the photo scan, Eye flags IDs and bank cards, receipts, medical photos and documents. Any photo Eye thinks might be intimate gets a second check from **Moderator**. *photos → private things* lists what it found, blurred. One move puts the photos in the **vault**:

- Each photo, its thumbnail and the vault's index are encrypted with **AES-256-GCM** under a random data key.
- That data key is stored only wrapped by an **Android Keystore** key that can't be exported and needs your **fingerprint or screen lock for every use**. Opening the vault is one prompt bound to that unwrap (a `BiometricPrompt.CryptoObject`), so it behaves the same on every phone and never "times out" in the middle of adding photos.
- If the screen lock is removed, Android destroys the key. Burrow says so plainly and offers to start a new, empty vault.
- The vault lives in `noBackupFilesDir`, and the app opts out of backup, so the vault never reaches a cloud backup. The gallery and other apps can't see it.
- Once the photos are copied in, Android asks you to delete the originals. If you say no, the vault copies are removed again.
- The vault locks as soon as you leave Burrow, and it forgets its decrypted thumbnails when it does.
- You can put any photo back in the gallery (*Pictures/Burrow*), or delete it for good.

## Talks in Hindi

Ear identifies the spoken language of each talk. Voz covers 25 European languages; when Ear hears **Hindi**, Burrow transcribes it with **AI4Bharat's IndicConformer** instead, running on the phone with ONNX Runtime:

- **The model:** a community int8, CTC-only sherpa-onnx export of IndicConformer for Hindi ([`ngiftsun/indicconformer-hi-int8-sherpa`](https://huggingface.co/ngiftsun/indicconformer-hi-int8-sherpa), derived from [`ai4bharat/indic-conformer-600m-multilingual`](https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual), MIT licence). It's about 140 MB and is downloaded only when you ask, from *privacy → the models & languages*, from onboarding, or from a Hindi talk's page.
- **How it runs:** 80-band log-mel features computed NeMo-style in Kotlin, the audio fed in roughly 20-second pieces cut at pauses, then greedy CTC decoding into words with timings. Diarization, people and the transcript work as for any talk. Topics and follow-ups are skipped for now, because Gist and the follow-up phrases are tuned for European languages.
- **Without the model:** a Hindi talk stops with a clear "download Hindi" step, instead of producing a garbled transcript.
- **More languages:** add another IndicConformer language with one more entry in `speech/IndicSpeech.kt`.

## Share safely

Burrow appears in Android's share sheet for photos as **Check with Burrow**. It looks for faces, ID and bank cards, documents, intimate content (confirmed by Moderator), and location or camera data in the file. Each finding comes with a fix:

- crop to the main subject, using Eye's smart crop;
- blur (pixelate) faces;
- remove location and camera data: the clean copy is re-encoded, so it carries no metadata at all.

Burrow then passes the clean copy on to the app you were sharing to. You can also send the original.

## Mask

Paste text, share text to Burrow, or select text in **any app** and choose **Mask with Burrow**. Redact swaps names, emails, phone numbers, addresses, cards and IDs for placeholders like `[NAME_1]`. You then:

1. copy the masked text, or, in an editable field, put it straight back in place of your selection;
2. paste it into ChatGPT or a form;
3. paste the reply back into Burrow, which restores the real details on the phone.

The masked text is held in memory only, and Burrow forgets it after 30 minutes. A restored reply is copied with Android's "sensitive" clipboard flag.

## Privacy you can check

The **privacy page** (the padlock icon) shows:

- **A ledger**: everything Burrow did today and since setup ("checked 312 photos", "transcribed 1 talk", "learned 1 voice, with your OK"). It's counted on the phone.
- **The real number of bytes Burrow has sent**, read from Android's `TrafficStats` for the app. The only network traffic is requests for model files. There's no upload code, no account and no analytics.

It also has switches, and each one is enforced in code:

- **never go online**: the model hub refuses to download anything. Onboarding offers to turn this on once the models are in place.
- **lock with fingerprint**: Burrow asks again when you come back after 30 seconds away.
- **delete talk audio after 30 days**: the notes and transcript stay.
- **learn voices when I name someone**: when this is off, naming a speaker teaches Burrow nothing.
- **hide every screen from screenshots**: off by default, so you can share screenshots of Burrow. The vault, the private sweep and the mask screen are always blocked from screenshots and the recent-apps preview.

**Wipe everything** forgets every analysis, talk and its audio, person and voice, the vault, the ledger and the settings, with the downloaded models as an option. Your gallery isn't touched. *Forget this person* deletes one person's voice and unlinks them from every talk.

Permissions are requested on the *ask first* onboarding page, with one line saying why each is needed.

## Also inside

**privacy → the models → Model lab** keeps a playground for each of the 11 Android models (Voz, Ear, Who, Clear, Eye, Moderator, Redact, Gist, Emo, Tongue, Shapes), for trying them on their own.

## Build

Requirements: JDK 17 and Android SDK 35 (or Android Studio). The build targets **arm64-v8a**, Android 11+.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (R8-optimized)
./gradlew testDebugUnitTest # voice signatures, speaker attribution, chunking, follow-ups,
                            # and screenshots of every screen into app/build/shots/
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
