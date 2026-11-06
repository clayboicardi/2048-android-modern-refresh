# Getting 2048 onto an iPhone (free Apple ID + SideStore)

This is the honest, no-fee path to keep the app on a phone without the $99/yr Apple Developer
Program. It is **not** the "block Apple's domains in NextDNS" trick from the article that started
this: that trick only applies to *shared enterprise certificates* (dodging a server-side
revocation check). A **free Apple ID** has a different limit — the signing profile carries a hard
7-day expiry that iOS checks **locally on the device**. No DNS rule can touch a local date check.
The real fix for the 7-day limit is to let a tool **re-sign the app automatically**, which is what
SideStore does.

## What CI gives you

The `iOS build` GitHub Actions workflow (`.github/workflows/ios.yml`) builds an **unsigned** IPA on
a free macOS runner and uploads it as the artifact `game2048-ios-unsigned-ipa-<sha>`. It runs on
its own for pull requests and `master` pushes that touch `ios/` or the game engine; you can also
start it from the repo's **Actions** tab ("Run workflow"). Download and unzip the artifact to get
`Game2048-unsigned.ipa`.

Unsigned is correct here: SideStore strips and re-signs on import, so a signature from CI would
just be thrown away.

## One-time SideStore setup (on the iPhone)

SideStore is an open-source AltStore fork that re-signs sideloaded apps **on the device itself**
every 7 days, over Wi-Fi, after a one-time computer-paired setup. You need the wife's Apple ID
(a throwaway Apple ID made for this is fine and keeps it off her main account).

1. Follow the current official guide at <https://sidestore.io> → **Install**. It changes with iOS
   releases, so trust it over any step list baked in here. In outline:
   - Generate a **pairing file** for her iPhone (the guide links a small helper you run once from
     a computer — Windows, macOS or Linux — over USB).
   - Install the **SideStore** app itself onto the phone (the guide's installer handles the first
     signature), then in iOS **Settings → General → VPN & Device Management**, trust the developer
     profile for her Apple ID.
   - Enable SideStore's on-device refresh (its local VPN / anisette). This is what lets it renew
     the 7-day certificate without a computer afterward.
2. In SideStore, sign in with the Apple ID that will own the app.

Expect this step to be fiddly; it is the fragile part, and it lives on the device, not in this
repo. If you get stuck, the SideStore Discord and the guide's troubleshooting are the live sources.

## Installing (and keeping) 2048

1. Get `Game2048-unsigned.ipa` onto the phone (AirDrop, Files, or a link) and open it **in
   SideStore** → it signs it with the free Apple ID and installs it.
2. SideStore auto-refreshes it every 7 days while the phone has Wi-Fi and SideStore's background
   refresh is on. If a refresh is ever missed and the app won't launch, open SideStore and tap
   **Refresh** — it re-signs on the spot.

Free Apple ID limits (not a problem for one game): at most **3** actively sideloaded apps and
**10** app IDs per 7-day window. 2048 is one app.

The saved game and best scores live in the app's own Documents directory and survive re-signing
and normal updates. They are **not** carried over from the Android app (that's a possible
follow-up).

## When it's worth paying instead

If the 7-day dance gets annoying, the $99/yr Apple Developer Program removes the expiry entirely
and unlocks TestFlight: CI can upload a build that lasts 90 days and re-upload before it lapses,
with no per-week fuss. That's the clean option if free-signing upkeep wears thin — nothing in this
repo has to change except adding a signed upload step to `ios.yml`.
