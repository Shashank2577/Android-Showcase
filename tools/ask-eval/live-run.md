# ask Burrow, live run

## what did Priya promise?

- `find_person(name: "Priya")` → done: Priya Shah · 1 talk
- `search_talks(query: "promise")` → done: Q3 roadmap sync
- `follow_ups(person: "Priya Shah")` → done: 1 follow-up

**Answer** (9.9 s): Priya promised to book the launch venue next week, according to the "Q3 roadmap sync" talk.

Sources: person/2, talk/1/580.0, talk/1/2260.0

## when is Marco sending the budget?

- `search_talks(query: "budget")` → done: Q3 roadmap sync
- `follow_ups(days: 1, person: "Marco Rossi", talk_id: 1)` → done: 1 follow-up

**Answer** (5.5 s): Marco said he would send the budget by Thursday.

Sources: talk/1/1420.0, talk/1/1700.0

## who talks about hiring?

- `search_talks(query: "hiring")` → done: Q3 roadmap sync

**Answer** (4.5 s): The talk titled "Q3 roadmap sync" mentions hiring, and someone said, "I can take the hiring plan if that helps."

Sources: talk/1/1980.0

## what was the Q3 roadmap sync about?

- `search_talks(query: "Q3 roadmap sync")` → done: Q3 roadmap sync

**Answer** (5.3 s): The Q3 roadmap sync was about the roadmap, budget, and hiring. Sam Carter started the discussion by saying, "Let's start with where the roadmap stands for Q3."

Sources: talk/1/20.0

## do I have any photos of dogs?

- `find_photos(query: "dog")` → done: 0 photos

**Answer** (4.6 s): I did not find any photos of dogs on your phone. You can try searching for "dog" again or check your photo albums.

Sources: 

## rename the roadmap talk to Q3 planning

- `search_talks(query: "roadmap")` → done: Q3 roadmap sync
- `rename_talk(talk_id: 1, title: "Q3 planning")` → declined: you said no
- asked the user: rename “Q3 roadmap sync” to “Q3 planning” → no

**Answer** (5.8 s): Okay, I left it as it was: I didn't rename “Q3 roadmap sync” to “Q3 planning”.

Sources: talk/1/20.0

## what did we decide about the cloud tier?

- `search_talks(query: "cloud tier")` → done: Q3 roadmap sync

**Answer** (4.9 s): I found a talk from today's roadmap sync where Priya Shah said, "Yes. No cloud tier; everything keeps working without signal."

Sources: talk/1/860.0

## what did Arjun say yesterday?

- `search_talks(days: 1, person: "Arjun")` → done: no one called Arjun

**Answer** (5.0 s): I couldn't find any recorded talks where Arjun was mentioned yesterday. You could try searching for a different name or a different time frame.

Sources: 

