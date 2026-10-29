You own **Version D — "Almost Nothing"**: five tiny micro-apps under one parent brand, designed from a brief the user wrote. The user wants each design direction handled by its own session so they can talk to each one individually; you are the Version D session. Start working right away.

## The brief (from the user)
BRAND: "Almost Nothing" — a family of five tiny micro-apps. Tagline: "Less app. More moment."
Core principle: ONE APP. ONE MOMENT. ONE ACTION. THEN GET OUT OF THE WAY. No dashboards, no tabs, no bottom navigation, no feeds,
no profiles, no settings screens, no onboarding carousels, no engagement tricks, no streaks, no history.
Personality: bold, minimal, human, a little playful, quietly rebellious.
DESIGN TOKENS (strict): background pure white #FFFFFF for the core interface; primary text ink black #171717; secondary text warm gray #77736E.
Headlines in a BOLD GROTESK (very large, heavy, tight tracking — e.g. Bricolage Grotesque / Archivo Black feel); UI text in a clean sans (Inter);
an editorial serif ONLY for story/letter content. Hand-drawn symbols: thick black ink marker line art with a flat solid accent-color fill offset
slightly from the outline (like a risograph / marker sketch), imperfect and human. Buttons: simple solid black pill or the app-accent color,
black text on accent; one primary action per screen, at most one quiet secondary text link. Corners soft but not overly rounded (~14px).
Lots of empty white space — the screen should feel almost empty. A small "almost nothing" wordmark (lowercase, bold, with a small black dot) at top-left.
The app accent color must NOT take over the screen: keep the interface white; use the accent only for the hand-drawn symbol, the main interaction,
and one expressive moment. No gradients, no shadows, no cards grid, no icons row, no stock photos, no emoji.
Privacy: local-first, temporary by default, no account. Small gray privacy notes are welcome. Android phone portrait, 412dp wide.

The five apps should feel like five small doors into the same world: same icon silhouette (rounded square, accent-color background, black hand-drawn symbol), same sketch style, same black ink, same white core interface, same bold typography, a different color and symbol per app, ONE expressive color moment per workflow.

| App | Core emotion | Main interaction | Color |
|---|---|---|---|
| Scream | Release | Speak | Coral #F26B61 |
| Overheard | Curiosity | Listen | Mustard #E8C77B |
| Liar Liar | Play | Answer | Lime #B8D96A |
| Parked | Relief | Remember | Sky blue #7EACD0 |
| Unsent | Closure | Write | Lavender #B7A4D8 |

Shared components to keep consistent (the brief's Sketch Symbols): AN / Wordmark, AN / Dot, AN / Primary Button, AN / Secondary Button, AN / App Icon, AN / Sketch Symbol, AN / Permission Card, AN / Result Card, AN / Delete Confirmation, AN / Toast, AN / Empty State, AN / Loading Animation.
Guardrails from the brief: Scream is not therapy and must not diagnose; Overheard is consent-first fiction (no covert recording; the story is labeled as imagined); Liar Liar must never present results as real lie detection (no percentages); Parked needs a manual "I'm back" (not only geofencing); Unsent never sends anything. Everything is temporary by default and deleted at the end of its flow.

## Artboards (design order: Unsent → Scream → Parked → Overheard → Liar Liar)
### Unsent — accent LAVENDER #B7A4D8 — "Say it. Don't send it." — symbol: a hand-drawn folded paper / letter
  - `Unsent / 01-home`: Home. Lavender hand-drawn paper/letter symbol, large. Headline: 'What do you wish you could say?'. Main button: 'Write it out'. Tiny note: 'Nothing here is ever sent.'
  - `Unsent / 02-writing`: Writing. A full-screen quiet writing surface like a private letter. Small label 'To:' with optional recipient 'Dad'. Large text area with the user mid-sentence in the editorial serif: 'I never said this, but I was proud of you the day you…' with a blinking cursor. No toolbar, no character count, no send button. Only a small 'Done' text action top right.
  - `Unsent / 03-read-back`: Read it back. The text appears as a simple letter in the editorial serif, addressed 'Dad,' with 3 short paragraphs. Actions: primary 'Let it go', secondary text 'Keep writing'.
  - `Unsent / 04-release`: Release. The letter is folding/dissolving into the lavender hand-drawn sketch (show a mid-animation fold with motion lines). Text: 'You said what you needed to say.' Small gray note: 'Deleted from this phone.'
### Scream — accent CORAL #F26B61 — "Let it out. Leave it here." — symbol: a big coral scribbled circle
  - `Scream / 01-home`: Home. Large coral hand-scribbled circle in the center. Headline: 'What's taking up space?'. Supporting text: 'Say it out loud. This stays here.'. Main button: 'Let it out'. Tiny privacy note: 'Processed on your device'.
  - `Scream / 02-recording`: Recording. FULL-SCREEN near-black background (#171717). A large organic, animated coral blob/scribble form that swells with the voice (not a generic waveform). Big white timer '0:42'. A round white stop button. No transcript.
  - `Scream / 03-processing`: Processing. White background. The coral shape has contracted into a small coral dot in the center. Text: 'Letting that settle…'. Nothing else.
  - `Scream / 04-response`: Response. White background, small coral scribble. Response in large readable typography: 'That sounds like a lot to carry today. You don't have to solve it all right now.' Primary button 'Let it go', secondary text 'Say more'. Tiny gray note: 'Not therapy. Just a moment.'
  - `Scream / 05-exit`: Exit. The response text fading away (show it ghosted at 20% with drifting letters). Small coral dot. Gray note: 'Audio and response deleted.' Returning home.
### Parked — accent SKY BLUE #7EACD0 — "You'll find your way back." — symbol: a hand-drawn blue map pin
  - `Parked / 01-home`: Home. Large blue hand-drawn map pin. Headline: 'Where did you leave it?'. Main button: 'Remember where I parked'.
  - `Parked / 02-save`: Save location. A small, simple, desaturated map preview with a blue hand-drawn pin (rounded 14px). Address line 'Westfield car park, Level 3'. An optional photo slot showing a concrete pillar with a big '3B' painted on it. A note field filled: 'Level 3. Near the blue pillar. Opposite the elevator.'. Primary 'Save', secondary text 'Cancel'.
  - `Parked / 03-saved`: Saved. Large blue pin sketch. Headline: 'You'll find your way back.'. Location summary 'Level 3 · Pillar 3B · saved 2:14 pm'. Small photo thumbnail. Button: 'Find my car'.
  - `Parked / 04-find`: Find my car. A simple map with a hand-drawn dashed walking route from a black dot (you) to the blue pin. Big '240 m · 3 min walk'. The photo and note below. Button: 'I'm back'.
  - `Parked / 05-clear`: Clear. Headline: 'Back where you belong.' A small blue pin with a hand-drawn check mark. Gray note: 'Parking memory deleted.'
### Overheard — accent MUSTARD #E8C77B — "Every moment has a story." — symbol: two overlapping hand-drawn speech bubbles
  - `Overheard / 01-home`: Home. Warm white. Large mustard hand-drawn speech-bubble sketch. Headline: 'A story from a moment.'. Supporting text: 'Catch a phrase. Imagine the rest.'. Main button: 'Listen for a moment'. Small note: 'Use only conversations you have permission to record.'
  - `Overheard / 02-recording`: Recording. Minimal. Two overlapping mustard hand-drawn speech bubbles gently pulsing. A short countdown ring '0:04 / 0:05'. Stop button. Feels curious, not like surveillance.
  - `Overheard / 03-transcript`: Transcript preview. Quote in large type: '“I told him I would never come back…”'. Small label 'What we heard'. Actions: primary 'Make a story', secondary text 'Try again'.
  - `Overheard / 04-fiction`: Fiction result. Bold black title 'A little fiction'. The story sentence big in the editorial serif: 'They were planning a trip, but someone already knew they wouldn't go.' Small mustard scribble. Clear label: 'Imagined, not real.' Buttons: 'Another story' and text 'Done'.
  - `Overheard / 05-disappear`: Disappear. The sentence folding/fading away into the mustard scribble. Gray note: 'No archive. It's gone.'
### Liar Liar — accent LIME #B8D96A — "Play with the truth." — symbol: a hand-drawn eye
  - `Liar Liar / 01-home`: Home. Lime hand-drawn eye icon, large. Headline: 'Can you keep a straight face?'. Main action: 'Start a round'. Secondary text: 'How to play'. Tiny note: 'Just a game. Not a lie detector.'
  - `Liar Liar / 02-question`: Question. Bold black question set huge over a lime scribble: 'Have you ever blamed someone else for something you did?'. Actions: primary 'Use this question', secondary text 'Write my own'. Small 'Player 2 of 4 · Maya'.
  - `Liar Liar / 03-answer`: Answer. Large hand-drawn microphone symbol with lime fill. Prompt: 'Tell your story.'. Big record button and timer '0:12'.
  - `Liar Liar / 04-reveal`: Reveal. Game-show style playful reveal: the hand-drawn eye mid-blink, big bold text 'Truth vibes: suspicious.' with a small subline 'That was almost convincing.' Clearly entertainment — no percentages. Button 'Let the group vote'.
  - `Liar Liar / 05-vote`: Group vote. Three huge stacked choice buttons: 'Truth', 'Lie', 'No idea' (one tapped state in lime). Small tally after voting: 'Truth 1 · Lie 3'.
  - `Liar Liar / 06-round-complete`: Round complete. A small celebratory hand-drawn lime confetti sketch. Headline 'Maya kept a straight face.' Actions: 'Next player', text 'Play again', text 'Done'.

Stitch project: `projects/15560744580770073640` ("Version D · Almost Nothing"). Its design system exists (seeded by `Unsent / 01-home`) — reuse it. Already generated: Unsent 01-home and 02-writing, Parked 01-home, Overheard 01-home, Liar Liar 01-home (confirm with list_screens; a few more may have finished when this was handed over). Generate the rest, prompting each with the brief + the app's accent/symbol + the artboard spec.

## Prototype specifics
ONE prototype with a tiny launcher showing the five app icons (the only multi-app screen; it stands in for the phone's home screen), each opening its own single-flow micro-app. Every flow runs start to finish with real interactions: Scream's coral form reacting to a simulated amplitude while the timer runs → "Letting that settle…" contracting to a dot → the response → fade back home. Overheard's 5-second capture → transcript → "A little fiction" → disappear. Liar Liar's question → answer timer → blinking-eye reveal → three-way vote with a tally → round complete. Parked's save (note + photo) → saved → find (route + distance) → "I'm back" → cleared. Unsent's writing surface (really typeable) → read back → the letter folding into the lavender sketch → "You said what you needed to say." Short, purposeful, hand-drawn-feeling motion; respect prefers-reduced-motion.

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
5. Save the prototype source (HTML, images, a short README linking the Stitch project `https://stitch.withgoogle.com/projects/<id>`) in the Android-Showcase repository under `design/version-d-almost-nothing/` then commit and push it on your own session's working branch (the one your environment instructions designate). Never push to `main` or `ccr-ee5a227c-c9ufzg`, and never commit the API key.
