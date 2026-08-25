# Inlay Hints Extension

Inlay Hints Extension is an IntelliJ IDEA plugin that displays local, line-oriented notes as clickable inline inlay hints at the end of source lines. Notes stay outside source files and the plugin contains no AI features.

## File mapping

The `inlay-hints` directory sits in the project root. A sidecar file mirrors the source file's complete project-relative path and appends `.ihm` to the complete source file name.

```text
project/
├── main.go
├── src/
│   ├── main/java/com/example/Demo.java
│   └── main/kotlin/com/example/Worker.kt
└── inlay-hints/
    ├── main.go.ihm
    └── src/
        ├── main/java/com/example/Demo.java.ihm
        └── main/kotlin/com/example/Worker.kt.ihm
```

This is the 2.0 mapping format and is intentionally incompatible with 1.x. There is no fallback to the old layout: for example, `src/main.go` now maps to `inlay-hints/src/main.go.ihm`, not `inlay-hints/main.go.ihm`.

Line 12 of `Demo.java.ihm` is shown at the end of line 12 in `Demo.java`. Empty lines produce no hint. Ctrl-clicking a hint opens its IHM file at the matching line; an ordinary click does not navigate.

To edit a note without leaving the source editor, type the activation keyword at the end of its source line. The default keyword is ` ^^ ` and includes both the leading and trailing spaces. The keyword is removed from source immediately and a black inline text editor opens at the line end. Press `Enter` to save and return the source caret to the same line end, or `Esc` to cancel. Moving focus outside the inline editor saves the current text immediately without taking focus back. Existing note text is loaded into the editor; clicking or double-clicking a hint does not enter edit mode.

The inline editor uses a composite font so Chinese and other fallback glyphs remain readable when a new hint starts empty.

If the mapped IHM file does not exist, entering the activation keyword creates it immediately with enough blank lines to preserve source-line alignment. The plugin does not add editor shortcut buttons or contain AI functionality.

## Generate explanations with AI

The plugin itself contains no AI features. A language-agnostic Chinese prompt for an AI coding agent is available at [AI_PROMPT_TEMPLATE.md](AI_PROMPT_TEMPLATE.md). It tells the AI to identify namespace declarations, dependency imports, and block terminators by each language's semantics instead of relying on Java-specific keywords.

To use it:

1. Add `/inlay-hints/` to the repository-local `.git/info/exclude` file.
2. Start an AI coding agent with workspace read/write access from the target project root.
3. Open the prompt template. It targets the entire project by default; edit its scan scope first when only selected modules should be processed.
4. Send the complete prompt inside the code block to the AI and allow it to create `.ihm` files under the project-root `inlay-hints` directory. The prompt fully defines the plain-text IHM format, path mapping, and line-alignment protocol, so the AI does not need prior knowledge of or access to this plugin.
5. Verify that every generated IHM path is correct and has exactly the same line positions and line count as its source file.
6. Open the source in IDEA. The plugin renders the generated notes, and Ctrl-clicking a hint opens the matching IHM line for editing.

Back up existing hand-written notes before regeneration. The one-source-line-to-one-IHM-line rule is mandatory; extra headings or wrapped explanation lines will shift every subsequent hint.

## Line synchronization

When editor actions or external updates such as `git pull` add, modify, or remove source lines, the plugin applies the corresponding line operations to the existing IHM file. Synchronization is one-way from source to sidecar; editing an IHM file never modifies source code.

The IHM document is saved after synchronization. Deleting a source line also deletes the note on that line. Modified lines keep their notes by default.

While the source editor remains open, undoing a full-line deletion restores the original IHM note, and redoing it restores the deleted state. The plugin retains the latest 50 synchronization transitions per open source file. History restoration never overwrites IHM content that was manually edited in the meantime.

## Settings

Open **Settings | Tools | Inlay Hints Extension** to:

- show or hide Inlay Hints Extension;
- clear the corresponding IHM line whenever a source line is modified;
- delete the IHM file when inline editing removes its last non-empty hint (disabled by default);
- when automatic IHM deletion is enabled, optionally remove empty parent directories recursively (disabled by default);
- customize the source-line activation keyword used to open the inline hint editor.

## Keep notes out of Git

Add this pattern to the repository-local `.git/info/exclude` file:

```gitignore
/inlay-hints/
```

The plugin shows this reminder once per project when it first detects a sidecar file.

## Compatibility

- IntelliJ IDEA 2023.1 or later
- Java 17 bytecode

## Build

The project is built and verified with JDK 21 and Gradle 8.14.3:

```shell
gradle test buildPlugin
```

To build against an installed IDE, pass its installation directory:

```shell
gradle test buildPlugin -PlocalIdePath=/path/to/IntelliJ-IDEA
```

The resulting ZIP is written to `build/distributions/`.
