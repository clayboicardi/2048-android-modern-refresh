# Privacy policy

Applies to the Android app "2048" (package `com.clayboicardi.game2048`, and its debug build `com.clayboicardi.game2048.debug`), version 3.0.0 and later, built from https://github.com/clayboicardi/2048-android-modern-refresh.

Last updated: 2026-10-28.

## Summary

This app collects nothing. It has no network access, no accounts, no ads and no analytics. Game data stays on your device. Android's own backup may copy it to your Google account if you enabled device backup.

## What the app stores on your device

The app keeps only these files in its private storage:

- `settings.json`: your settings, such as theme, palette, motion, haptics, controls and display options;
- `records.json`: best scores and statistics for each board size;
- `game-3.json` to `game-8.json`: the saved game for each board size, including its move history.

They contain no personal information. The app does not read your contacts, location, files, accounts or device identifiers. Uninstalling the app deletes these files.

## What leaves your device

The app itself sends nothing. It does not declare Android's internet permission, so it cannot open network connections.

- **Android backup.** If backup is turned on in Android's settings, Android, not this app, may include the files above in your device backup or in a transfer to a new device. You control this in Android's settings.
- **Sharing.** If you tap Share on the game-over card, the app passes a short text summary of that game to the app you choose. Nothing is shared unless you choose to.
- **Links.** "Source code" opens the project page in your browser, and "App language" opens Android's settings. Those apps have their own privacy policies.

## Permissions

The app requests no runtime permissions and never shows a permission prompt. Its manifest contains one permission, `com.clayboicardi.game2048.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which the AndroidX Core library adds automatically. It is a signature-level permission: only apps signed with the same key can hold it, and on Android versions before 13 it keeps the app's own internal broadcast receivers private. It gives the app no access to your data, your device or other apps. The debug build carries the same permission under its own package name, `com.clayboicardi.game2048.debug`.

## Children

The app collects no data from anyone, including children.

## Changes and contact

Changes to this policy are made in this file, and its history is public in the repository. Questions: https://github.com/clayboicardi/2048-android-modern-refresh/issues
