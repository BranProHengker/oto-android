# Contributing to Oto

Thanks for wanting to help out. The contribution process is kept simple and focused.

> [!NOTE]
> Oto is an independent project. It is not affiliated with, endorsed by, or sponsored by YouTube, Google, Spotify, Apple, Last.fm, Shazam or any other service it can talk to. See the [Disclaimer](#disclaimer) below.

## Getting set up

- Any device with a screen
- Android SDK / Android Studio and JDK 17/21
- The project is Kotlin + Jetpack Compose, split into the `app` module and a set of `providers:*` modules (InnerTube, lyrics providers, Last.fm, Discord RPC, etc.)
- Build from the terminal with Gradle:

  ```bash
  ./gradlew assembleArm64FossDebug     # debug build, fastest to iterate on
  ```

## Making a change

1. **Fork and branch.** Use a short descriptive branch name, for example `fix/player-crash` or `feature/lyrics-sync`.
2. **Keep it focused.** One fix or feature per pull request makes it much easier to review.
3. **Match the surrounding code.** Follow the naming, structure and comment style of the files you are touching. Don't reformat code you aren't changing.
4. **Check older devices.** Oto supports Android 8.0+, so anything that uses a newer platform API needs an `SDK_INT` guard.
5. **Don't trade features for speed.** Performance work should keep every existing animation and visual intact.
6. **Test it.** Run it on a real device or emulator, and verify compiler and unit tests.
7. **Open the pull request.** Fill in the PR template and link any issues.

## Reporting bugs

Open an [issue](https://github.com/BranProHengker/oto-android/issues) and include:

- Oto version and your Android version and device
- What you did, what you expected, and what happened instead
- Logs (`adb logcat`) and a screenshot or recording if applicable

## Third-party services and content

- Don't commit API keys, tokens, cookies or account credentials.
- Don't add code whose purpose is to bypass DRM, paywalls or access controls.
- Don't add trackers or analytics. Oto does not collect user data.
- Playback and metadata come from third-party services that can change or break at any time.

## Licensing

Oto is licensed under the [GNU General Public License v3.0](../LICENSE). By contributing you agree that your contribution is released under the same license. Keep the existing copyright and attribution notices in files you modify, and credit the source of any code you bring in from another project (it must be GPL-3.0 compatible).

## Disclaimer

Oto is provided as is, without warranty of any kind. It is an unofficial client built for personal and educational use.

- It is **not affiliated with or endorsed by** YouTube, YouTube Music, Google, Spotify, Apple, Last.fm, Shazam, Discord or any other third-party service or rights holder.
- Oto does not host, store or distribute any music. Content is fetched from third-party services at the user's request.
- You are responsible for how you use the app, including following the terms of service of the services you connect to and the copyright laws that apply where you live.
- Unofficial forks and builds are not supported.
