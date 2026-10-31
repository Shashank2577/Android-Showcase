---
name: base-platform
description: Keep Burrow in step with the base platform (the baseApp repo) that every new app is built on - its design tokens, doodles and model catalog came from Burrow, and changes now flow both ways. Use when changing Burrow's theme, doodles, components or models, or when baseApp's design or catalog changed.
---

# Burrow and the base platform

Burrow's hand-drawn design system is the origin of the platform's look. It now lives, cross-platform, in the `baseApp` repo (checked out next to this one):

| Burrow | baseApp |
| --- | --- |
| `ui/theme/Theme.kt` colours and `Hand.*` sizes | `design/tokens.json` |
| `ui/hand/Doodles.kt` | `design/doodles.json` |
| `ui/hand/HandUi.kt` components | `packages/base_design` (Flutter) |
| `core/ModelCatalog.kt`, `speech/SpeechPacks.kt` | `catalog/models.json`, used through the `:basemodels` store |

`baseApp/design/THEME.md` is the platform version of `design/burrow-a/theme/THEME.md`. The wobble is bit-for-bit the same in both (tested in `baseApp/packages/base_design/test/design_test.dart`).

## A design change made here

If you change a colour, size, doodle or component in Burrow and it is not Burrow-only, make the same change in baseApp so every app gets it: edit `baseApp/design/tokens.json` or `doodles.json` (or port the component to `packages/base_design`), then follow baseApp's `propagate-change` skill. Otherwise the next sync will undo the drift in the other apps but not here.

## A change made in baseApp

`baseApp/./base sync` writes generated files into `design/base-platform/` here:

- `BaseTokens.kt` (package `base.platform.tokens`): the tokens as Compose values;
- `base-tokens.css`: the same tokens for the HTML prototype in `design/burrow-a/prototype`.

They are outside the source set until Theme.kt is wired to them. To wire it once:

1. Move `BaseTokens.kt` to `app/src/main/kotlin/base/platform/tokens/BaseTokens.kt`, and change the sync target in `baseApp/platform.yaml` to that path.
2. In `Theme.kt`, build `Light`/`Dark` `BurrowColors` from `BaseTokens.Light.*`/`BaseTokens.Dark.*`, and `Hand.*` font sizes from `BaseTokens.HandType.*`.
3. Run `./gradlew :app:testDebugUnitTest --tests '*DesignSystemTest*' --tests '*ScreenshotsTest*'` and compare `app/build/shots/` with `design/burrow-a/theme/shots/`: nothing should move, because the values are the same today.

After that a token change reaches Burrow with `./base sync` and a build. New doodles: copy the path string into `Doodles.kt` (same format). New shared components: port the Flutter widget to Compose in `HandUi.kt` with the existing `wobble`/`handLine`/`swipe`/`handCircle` helpers.

## Models

Burrow uses the platform's shared model store (protocol v1, `baseApp/docs/MODEL_STORE.md`), so every app of the vendor on the phone downloads a model once:

- The store is the `:basemodels` module (`SharedModelStore`, `AndroidSiblings`, `BaseModelsProvider`). Its sources and `app/src/main/assets/base/models.json` (the catalog) are written by `baseApp/./base sync`; never edit them here. Change them in `baseApp/packages/base_models/android` and sync. Only `basemodels/build.gradle.kts` is Burrow's.
- `ShowcaseApp.models` is the store. `ModelHub` creates every Desert Ant model with its store folder (`desertant.<key>`) and installs through `store.ensure(...)`, so a sibling app's copy is used before the SDK downloads. Speech packs are catalog models `speech.indic.<code>` and `speech.omnilingual`, downloaded (resumable, SHA-256 checked) or copied by the store.
- The provider and its signature permission come from the module's manifest, with the vendor values in `app/build.gradle.kts` (`manifestPlaceholders`). Sibling copy only works between apps signed with the same key: Burrow's release build is signed with the debug key today, so sign every app of the vendor with one release key before publishing.
- The first start after the update moves already-downloaded speech packs into the store (`SpeechPacks.adoptPreStorePacks`) and deletes the SDK's old copies in `cacheDir` (`Housekeeping.dropPreStoreModelCache`); Desert Ant models download (or copy from a sibling) once more.
- Test the store with `./gradlew :basemodels:testDebugUnitTest`.

New model in Burrow: add it to `baseApp/catalog/models.json` (with `./base checksums`), sync, then `models.ensure(id)` and `models.pathOf(id, file)`.
