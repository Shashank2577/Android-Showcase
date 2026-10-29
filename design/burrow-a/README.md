# Burrow: Version A, hand-drawn minimal

- `PRODUCT.md`: the rethought product (six jobs, privacy model, the screens).
- `prototype/`: a clickable end-to-end prototype with 17 screens and 8 journeys. `index.html` is the shell, and each screen in `screens/` is static HTML with precompiled Tailwind.
  - To run it: `npx serve design/burrow-a/prototype` (or any static server), then open `/`.
  - The page omits the doctype because it is written for the claude.ai artifact host. Browsers still render it fine.
- The screens were generated in Google Stitch: https://stitch.withgoogle.com/projects/1077336223821188806
