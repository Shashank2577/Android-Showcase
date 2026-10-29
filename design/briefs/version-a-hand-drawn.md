You own **Version A — "Hand-drawn Minimal"** of the redesign of **Burrow**, a private on-device AI Android app. The user wants each design direction handled by its own session so they can talk to each one individually; you are the Version A session. Start working right away.

## The product (shared by Versions A and C)
App: "Burrow" — a private, on-device AI assistant for Android (Samsung Galaxy Z Fold 7 cover screen, portrait phone, 412dp wide).
Everything runs on the phone; nothing is uploaded. Three tabs in a bottom navigation bar: Photos, Meetings, People.
Use realistic, consistent sample content: the user is Sam. Recent meeting "Q3 roadmap sync" (Tue 28 Oct, 10:02, 42 min, English, 3 speakers: Priya Shah, Marco Rossi, and Sam (you)).
Topics: Product planning, Budget, Hiring. Photo library: 2.4 GB can be freed, 38 similar sets, 214 extra shots, 61 throwaway shots, 120 screenshots, 4,812 photos checked.
Use real-looking photo thumbnails (beach sunset, dog, birthday cake, mountain hike, family dinner).
Material 3 Android conventions (status bar, gesture nav area, 48dp touch targets), but with a distinctive, premium visual identity — not default Material.

Burrow already exists as a working Android app (Kotlin/Compose, `app/` module, branch `ccr-ee5a227c-c9ufzg` of this repo). This task is ONLY the redesign prototype, not Android code. Its three jobs, end to end, all on the phone:
- Photos: scan the gallery, group near-duplicate shots of one moment, keep the best shot (eyes open, best faces, keeper score), preselect the rest for the trash, flag blurry/accidental shots and screenshots, show the space to free, search photos by what's in them.
- Meetings: record (keeps going with the screen off) or import a file / a Samsung call recording; then on-device language → transcript → who spoke when → topics → highlights → follow-ups; a meeting page with player + speaker timeline, speakers with talk share and "Sounds like Sam · 86%" voice-match suggestions, highlights, follow-ups and a tap-to-seek chat transcript; share notes with personal details redacted.
- People: name a speaker once and the app learns their voice; later meetings suggest them; a person page shows their conversations, talk time, what they talk about, quotes and a voice-sample action.

## Screens to cover (with the sample content to use)
- `welcome`: Onboarding / welcome screen. Hero illustration or abstract art suggesting privacy (a small burrow / shield / phone).
Headline: 'Your AI lives here. Not in the cloud.' Three short value rows with icons: 'Clean up your gallery — best shots kept, clutter gone',
'Meeting notes that know who said what', 'Remembers the people you talk to'. A reassurance line: 'Runs entirely on this phone · nothing is uploaded'.
Primary button 'Get started', secondary text button 'How privacy works'.
- `photos`: Photos tab home. Top app bar 'Photos' with settings icon. A big hero stat: 'You can free up 2.4 GB' with subtitle
'214 extra shots in 38 similar sets · 61 throwaway shots · 4,812 photos checked'. A slim scan status row: 'Last scan 2 hours ago · Camera · last 90 days' with a 'Scan again' action.
Three review entry cards, each with a 4-thumbnail strip and a chevron: 'Similar shots — 38 moments with extra takes · 1.6 GB', 'Throwaway shots — blurry, accidental or empty · 412 MB', 'Screenshots — 120 · 380 MB'.
Then a 'Find photos' search field with placeholder 'dog, beach, receipt, smiling…' and suggestion chips: Beach · 212, Dog · 98, Food · 156, Mountains · 44, Receipts · 31.
Bottom navigation: Photos (selected), Meetings, People.
- `similar`: Similar shots review screen. Top bar with back arrow, title 'Similar shots', and a 'Select all' text action.
A helper line: 'Each set is one moment. We keep the best shot and select the rest for the trash.'
A vertical list of moment cards. Each card: header 'Beach sunset · 5 similar · Sat 25 Oct', reason line '★ Everyone's eyes are open · frees 18 MB', a 'Keep all' text button,
and a horizontal row of square photo tiles: the first tile has a gold 'Best' star badge and is kept; the others show a red selected check overlay meaning 'will be deleted'.
Show 3 cards (beach sunset, dog in park, birthday cake). A sticky bottom action bar: '176 selected · 1.4 GB' and a destructive button 'Move to trash'.
Note under it: 'Recoverable for 30 days'.
- `meetings`: Meetings tab home. Top bar 'Meetings' with settings icon. A live 'Recording · 12:48' banner card at the top with a pulsing red dot and pause/stop buttons.
Search field 'Search every transcript'. Two quick action chips: 'Import a file', 'Calls & recordings'.
A list of meeting cards, newest first: (1) 'Q3 roadmap sync' · Tue 28 Oct 10:02 · 42 min · 3 speakers — topic pills Product planning, Budget, Hiring — overlapping avatars P, M, S — one highlight line in quotes;
(2) 'Call with Mum' · phone icon · Mon 27 Oct · 18 min · topic pills Family, Travel; (3) 'Design crit' · processing state with a small progress indicator 'Transcribing part 2 of 3…'.
A large extended floating action button 'Record' with a mic icon. Bottom navigation with Meetings selected.
- `record`: Live recording screen. Top bar with back arrow and title 'Recording'. Optional title field showing 'Q3 roadmap sync'.
A very large elapsed timer '12:48'. A big live audio level visualization (bars or waveform) reacting to voices. Status chip 'Recording · stays on this phone'.
Live hint: 'Notes are prepared on the phone after you stop'. Bottom controls: a secondary pause button and a big round stop button (record red) labelled 'Stop & save'.
A small footnote about calls: 'For phone calls, turn on call recording in Samsung's Phone app and import them.'
- `notes`: Meeting notes screen (the hero screen of the app). Top bar: back, share icon, more icon.
Header: title 'Q3 roadmap sync', meta 'Tue 28 Oct · 10:02 · 42 min · English', topic pills Product planning · Budget · Hiring.
An audio player card: play button, scrubber 14:20 / 42:05, and directly beneath it a multi-lane speaker timeline (3 colored lanes, one per speaker) with a playhead.
'Who was there' section: 3 speaker rows with avatar, name, talk time and share bar — Priya Shah 18 min 43%, Marco Rossi 13 min 31%, 'Speaker 3' 11 min 26% with a suggestion banner 'Sounds like Sam (you) · 86% voice match' with ✓ and ✕ buttons.
'Highlights' section: 3 key sentences with speaker color dot, name and timestamp (e.g. 'We ship the offline mode in November and hold the price.' — Priya · 08:12).
'Follow-ups' section: checklist items 'Marco to send the revised budget by Friday', 'Hire two Android engineers before Q4', 'Book the launch venue'.
A 'Read transcript' button at the bottom.
- `transcript`: Transcript screen for 'Q3 roadmap sync'. Top bar with back and a search icon, a compact sticky mini player at top (play, 14:20, scrubber).
A chat-style transcript: messages grouped by speaker, each with avatar, colored name, timestamp, and text. Speakers: Priya Shah (one color), Marco Rossi (another), Sam (you) (a third).
The currently playing message is highlighted. Include ~7 messages of realistic product-meeting dialogue about the offline mode launch, budget and hiring.
A floating chip at bottom: 'Share notes · personal details removed'.
- `people`: People tab home. Top bar 'People' with add-person and settings icons.
'Voices to name' section (an inbox): 2 cards — 'Speaker 2 in Design crit — sounds like Marco Rossi' with a sample quote and a 'Name' button; 'Speaker 1 in Call with dentist' with quote and 'Name' button.
'Your people' list: rows with large colored avatar, name, meta and top topics: Priya Shah — 12 conversations · 4h 10m talking · Product planning · Hiring; Marco Rossi — 9 conversations · 2h 45m · Budget · Design; Mum — 6 calls · Family · Travel; Sam (you).
Bottom navigation with People selected.
- `person`: Person profile screen for 'Priya Shah'. Top bar with back and more icon. Large avatar, name, meta '12 conversations · 4h 10m talking · last seen today', 'Speaks English, Hindi'.
A 'Voice' card: 'Learned from 7 recordings' with a confidence meter and 'Record a voice sample' action.
'What she talks about': horizontal bars — Product planning 38%, Hiring 24%, Budget 18%, Design 12%.
'In her words': 2 quote cards with meeting names.
'Conversations' list: Q3 roadmap sync (spoke 18 min · 43%), Hiring loop (22 min · 51%), Launch retro (9 min · 30%).
- `assign`: A modal bottom sheet over a dimmed meeting notes screen: title 'Who is Speaker 3?'. A quoted sample line: '“I can take the hiring plan and share it on Thursday.”' with a small play button for a 5-second clip.
A ranked list of people with radio buttons and voice-match percentages: Sam (you) 86% (preselected), Priya Shah 41%, Marco Rossi 33%. Then a 'New person' text field and a 'This is me' checkbox.
Helper text: 'Each confirmation teaches Burrow their voice.' Buttons: 'Cancel' and primary 'Save'.

## Flows the prototype must support
welcome → Get started → Photos. Photos → Similar shots (toggle tiles, Keep all, Move to trash → toast → back with the free-up number reduced). Switch between Photos / Meetings / People. Meetings → Record (live timer + levels, pause, Stop & save → back to Meetings with a new "processing" item). Meetings → "Q3 roadmap sync" notes (play/scrub, tap a highlight to seek, confirm/dismiss the voice-match suggestion, open the "Who is Speaker 3?" sheet and Save). Notes → Read transcript (current line highlighted, tap a line to seek). People → Priya → person page → a conversation → its notes. People → "Name" on an unnamed voice → the assign sheet.

## Your design direction (the user's words: "make it a minimal app with hand-drawn typography as the main theme, minimal design")
VISUAL DIRECTION A — "Hand-drawn Minimal": a radically MINIMAL app whose main theme is HAND-DRAWN TYPOGRAPHY.
Headlines, big numbers and key labels are hand-lettered, as if written with a black felt-tip marker — use the Google Fonts 'Caveat'
(or 'Kalam') loaded via <link> for all display text, large (40–64px) and slightly imperfect. Body/UI text stays a quiet, clean sans
(Inter, 15–16px, #6B6B6B for secondary) so it is still easy to read. Background plain white #FFFFFF; ink #1A1A1A; exactly ONE accent:
a marker-yellow highlight #FFE45C used like a highlighter swipe behind a key word or number, plus hand-drawn black doodles:
wobbly underlines, circled words, little arrows, ticks and crosses, scribbled stars, sketchy hairline dividers.
Almost nothing on screen: one idea and one primary action per screen, huge white space, no cards, no shadows, no gradients, no photos
except where content needs them (photo thumbnails are shown small and plain). No bottom tab bar with icons — use three hand-lettered
words at the bottom ('photos · meetings · people') with the current one circled by hand. Buttons are text written in marker inside a
hand-drawn rough outline (sketchy rounded rectangle), or a simple hand-underlined text link. It should feel like a calm notebook page
with someone's handwriting — personal, warm, playful, and extremely uncluttered.

Stitch project: `projects/2292025524667259051` ("Burrow — Version A · Hand-drawn Minimal"). It is new and may have no screens yet — generate all 10, starting with `welcome` with `designSystem` left empty (so Stitch builds the project's design system from it), then reuse that design system. Prompt each screen with the product brief + the design direction + the screen spec.
Directions the user REJECTED (don't drift back toward them): "Quiet Paper" (warm cream paper, serif, terracotta — "too generic"), a dark violet/cyan glow look and a lavender Swiss/Material look (both "ugly").

## How to reach Stitch (Google's UI design tool)
Stitch is an MCP server over HTTP at https://stitch.googleapis.com/mcp, authenticated with the header `X-Goog-Api-Key`. The key is given in your session's first message (not stored in the repo).
Keep the key OUT of git, out of any artifact/page and out of any file outside your scratchpad (it is already in the user's chat history; they have been told to rotate it afterwards).
MCP servers added mid-session don't load, so talk to Stitch directly with this tiny client. Save it as `mcp.py` in your scratchpad next to a `key` file (chmod 600) holding the key, run `python3 -I mcp.py init` once, then `python3 -I mcp.py list` to see the tools (create_project, list_screens, get_screen, generate_screen_from_text, edit_screens, generate_variants, download_file, list_design_systems, update_design_system, …). Generation takes about a minute per screen; never retry a generation that timed out — poll get_screen/list_screens instead. Under load the service sometimes answers HTTP 503: wait ~10 s and retry that call once.

```python
import json, sys, urllib.request, os
D = os.path.dirname(os.path.abspath(__file__))
KEY = open(os.path.join(D, 'key')).read().strip()
URL = "https://stitch.googleapis.com/mcp"
SESS = os.path.join(D, 'session')

def call(method, params=None, notify=False, timeout=600):
    body = {"jsonrpc": "2.0", "method": method}
    if params is not None: body["params"] = params
    if not notify: body["id"] = 1
    h = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream", "X-Goog-Api-Key": KEY}
    if os.path.exists(SESS): h["Mcp-Session-Id"] = open(SESS).read().strip()
    req = urllib.request.Request(URL, data=json.dumps(body).encode(), headers=h, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        sid = r.headers.get("Mcp-Session-Id")
        if sid: open(SESS, "w").write(sid)
        raw = r.read().decode()
    if not raw.strip(): return None
    if raw.lstrip().startswith("{"): return json.loads(raw)
    out = None
    for line in raw.splitlines():
        if line.startswith("data:"):
            out = json.loads(line[5:])
    return out

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "init":
        r = call("initialize", {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "claude-code", "version": "1"}})
        print(json.dumps(r)[:1500]); call("notifications/initialized", notify=True)
    elif cmd == "list":
        r = call("tools/list", {})
        for t in r["result"]["tools"]:
            print("==", t["name"], ":", t.get("description","")[:400].replace("\n"," "))
            print("   params:", json.dumps(t.get("inputSchema",{}).get("properties",{}))[:900])
    elif cmd == "call":
        args = json.loads(open(sys.argv[3]).read()) if len(sys.argv) > 3 else {}
        r = call("tools/call", {"name": sys.argv[2], "arguments": args})
        json.dump(r, open(os.path.join(D, "last.json"), "w"), indent=1)
        s = json.dumps(r)
        print(s[:int(sys.argv[4]) if len(sys.argv) > 4 else 3000])

```
For each screen pass `deviceType: "MOBILE"`, `modelId: "GEMINI_3_8_FLASH"` and the project's design system (`list_design_systems` → `assets/...`) as `designSystem`, so every screen stays consistent. A generated screen's `htmlCode.name` and `screenshot.name` are fetched with `download_file` (the result's `structuredContent.html` is the page; the screenshot comes back base64).

## What to deliver
1. Finish the screen set in Stitch (run `list_screens` first and reuse what exists — don't regenerate it). Look at every screenshot yourself and regenerate or `edit_screens` anything off-brief, clumsy or generic. Quality bar: it must not look like a template — the user has already rejected directions as "ugly" and "too generic".
2. Build ONE complete, clickable, end-to-end prototype from the Stitch HTML: a phone-sized frame (Galaxy Z Fold 7 cover screen, ~412 px wide) where every flow works — buttons, back, sheets and the key interactions below navigate to the right screen or change state (selections toggle, timers tick, things fade/delete, toasts confirm). Keep Stitch's markup and visual design; wire it with a small router and event handlers. Artifact pages may only load scripts from cdnjs / cdn.jsdelivr.net / unpkg / cdn.tailwindcss.com (pin `https://cdn.tailwindcss.com/3.4.17`) and stylesheets from Google Fonts — so download every image (e.g. lh3.googleusercontent.com) and inline it as a data: URI (JPEG/WebP ~70%, ≤600 px, keep the page well under 16 MB).
3. Publish it with the Artifact tool (run its `quickstart` first, as its instructions say), send the user the link, and send a few key screenshots with SendUserFile so they can judge it fast.
4. Then keep iterating with the user — they will talk to THIS session directly about this design. Prefer Stitch `edit_screens` / `generate_variants` for visual changes, then rebuild and republish the same artifact.
5. Save the prototype source (HTML, images, a short README linking the Stitch project `https://stitch.withgoogle.com/projects/<id>`) in the Android-Showcase repository under `design/version-a-hand-drawn/` then commit and push it on your own session's working branch (the one your environment instructions designate). Never push to `main` or `ccr-ee5a227c-c9ufzg`, and never commit the API key.
