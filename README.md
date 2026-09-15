# Asset Browser

A client-side Fabric mod for Minecraft Java Edition 26.2. It adds a
"Loaded Assets" debug screen for browsing every resource the client has loaded.

## Install

1. Install Fabric Loader 0.19.3 or newer for Minecraft 26.2.
2. Put `assetbrowser-1.0.0.jar` in the client's `mods` folder.

Fabric API is not required. The server does not need the mod.

## Development login

The development client includes DevAuth Neo and enables it automatically. Run
`./gradlew runClient`; the first launch prompts you to sign in, and later
launches reuse the stored token from the project-local `.devauth` directory.
That directory is ignored by Git because it contains sensitive account tokens.
DevAuth is development-only and is not included in the distributable JAR.

## Loaded Assets browser

Open it from a button on the title screen or the in-game pause menu. It shows
every resource (`assets/...`) currently loaded across all active resource
packs as a folder tree, with:

- A preview pane: images (with animation playback), pretty-printed JSON/text,
  and `.ogg` playback with a waveform seek bar.
- "Used by" / "References" panels that cross-link an asset to what loads it
  and what it in turn references.
- Filtering by resource pack and file type, alongside free-text search.
- Draggable pane splitters, with sizes remembered across reopening the screen.
- Copying a previewed file to the system clipboard as a real file, revealing
  it in the OS file manager, or (for a numbered variant/stage family like
  `frosted_ice_0.png..frosted_ice_3.png`, shown as a single collapsed group)
  copying every file in that group at once.

## Build

Use Java 25 and run:

```sh
./gradlew build
```

The distributable JAR is written to `build/libs/`. Run `./gradlew test` to run
the unit tests (covering the asset browser's pure logic — grouping, reference
resolution, blockstate summaries — not anything needing a running game).
