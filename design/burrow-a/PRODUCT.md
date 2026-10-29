# Burrow: what it is for

**A private notebook that understands your photos, conversations and words, and never lets them leave the phone.**

Burrow is built on the 11 Desert Ant models that run on Android, entirely on the device. Its whole point is to do jobs people currently hand to cloud apps: Google Photos clean-up, Otter/Fireflies meeting notes, pasting into ChatGPT. Here they happen where nothing can leak. Every feature below is something the models can really do today. No feature depends on a server.

| Model | What it gives Burrow |
| --- | --- |
| Eye | photo tags and scenes, quality and keeper scores, faces, people, pets, sensitive content (IDs, receipts, medical, documents), near-duplicates, the best shot of a burst, smart crops |
| Moderator | nudity detection with per-region scores |
| Voz | speech to text in 25 languages, with word-level timestamps |
| Ear | spoken language identification (102 languages) |
| Who | who spoke when |
| Clear | denoise, dereverb, loudness |
| Gist | topics for any text |
| Redact | finds names, emails, phones, addresses, cards, IDs (27 languages) and swaps them for restorable placeholders |
| Tongue | language of short text |
| Emo | emoji suggestions |
| Shapes | hand-drawn strokes into clean shapes |

## Five real jobs

### 1. Tidy: free space without losing moments (Eye)
Bursts and retakes are grouped into *moments*. Burrow keeps the best shot (everyone's eyes open, then the best faces, then the keeper score) and lets the rest go. It also catches blurry, accidental and empty frames, and screenshots that have done their job (tickets, maps, codes). It always asks first: nothing is deleted without a tap, and trashed photos stay recoverable for 30 days.

### 2. Private sweep and vault: what's sitting in plain sight (Eye + Moderator)
"Who could see this if someone scrolled your gallery?" Burrow finds ID cards, passports, bank cards, receipts, medical photos, documents and intimate photos, and offers one move: **put them in the vault**. The vault is app-private storage, encrypted with a key in the Android Keystore, opened with your fingerprint, excluded from cloud backup, and hidden from the gallery and other apps.

### 3. Share safely: check a photo before it leaves (Eye + Moderator)
Opened from Android's share sheet. Before a photo goes to WhatsApp or anywhere else, Burrow reports what's in it: "2 faces · an ID card in the corner · your home location in the photo's metadata". It offers fixes: crop it out (Eye's smart crop), remove location and camera data, blur faces. Then you share the clean copy.

### 4. Talks: conversations you can find again (Voz + Ear + Who + Clear + Gist + Redact)
Record a meeting or a lecture (it keeps going with the screen off), or bring in a call from Samsung's call recorder. On the phone, Burrow:
1. cleans the audio;
2. checks the language;
3. transcribes it;
4. works out who spoke when;
5. pulls out the topics, highlights and follow-ups ("Marco will send the budget by Friday").

You get a tap-to-play transcript and search across every conversation. Sharing always offers "remove names and numbers first". Audio can auto-delete after 30 days while the notes stay.

### 5. People: remember who said what (Who + Burrow's own voice signature)
Name a voice once, and Burrow suggests that person in later conversations; you confirm every match. A person's page shows what you talk about, open follow-ups between you, and every conversation. Voices are learned only with your permission (a one-time "ask before learning voices" switch). "Forget this person" deletes their voice and unlinks them everywhere.

### 6. Mask: use any AI chat without giving it your secrets (Redact + Tongue)
Paste text, or share it to Burrow, before it goes into ChatGPT, an email or a support form. Names, numbers, addresses and card numbers become `[NAME_1]`, `[PHONE_1]`. Copy the masked text, paste the AI's reply back, and Burrow puts the real details back in, on the phone. Nothing is stored.

## Privacy you can see
- **On-device ledger.** "Today Burrow checked 312 photos, transcribed 1 talk and masked 2 texts. 0 bytes left this phone."
- **Offline after setup.** Models download once over Wi-Fi from Hugging Face, then a "never go online" switch cuts the network.
- **Ask first.** Each permission (photos, microphone, call recordings) is asked for only when a job needs it, with one line explaining why.
- **Delete means delete.** Per item, per person, auto-delete audio, and "wipe everything".
- **Lock.** The app and the vault lock with your fingerprint. Screens with private content are blocked from screenshots and from the recent-apps preview.

## Structure
Four hand-lettered words at the bottom: **today · photos · talks · people**. Mask lives on Today and in Android's share sheet. Privacy lives behind the lock icon on Today.

- **Today** is one quiet page that only shows what needs you: space to free, private photos in plain sight, a talk ready to read, voices to name.
- Every job is one short flow: look → decide → done.

## The prototype's screens
1. welcome
2. ask-first (permissions + one-time model download)
3. today
4. photos
5. moments (keep the best)
6. sweep (private things found)
7. vault (locked → open)
8. share-check
9. talks
10. recording
11. talk notes
12. transcript + share masked
13. people
14. person
15. name-a-voice sheet
16. mask (paste → masked → restore)
17. privacy (ledger + switches)
