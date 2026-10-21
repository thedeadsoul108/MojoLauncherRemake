# MojoRemake — progress and handoff

Last updated: 2026-10-21

## What this project is

MojoRemake is a remake of MojoLauncher for Android. It keeps the existing
game-launching engine and replaces the product identity and UI with a new,
Modrinth-inspired design.

- Repository: https://github.com/thedeadsoul108/MojoLauncherRemake
- Base: MojoLauncher `v3_openjdk` (commit `29c2ccc`)
- Working branch: `dev` (default branch of the fork)
- Upstream remote: `upstream` → `MojoLauncher/MojoLauncher`
- Fork remote: `origin` → `thedeadsoul108/MojoLauncherRemake`
- Local clone: shallow, `--single-branch`; submodules not initialized locally

## Non-negotiable behavior

The remake must not break:

- game launching and the JRE/runtime pipeline;
- native libraries (`pojavexec`, SDL, GLFW, mojoexec);
- renderers (OpenLTW, GL4ES, Vulkan/Turnip);
- accounts and authentication;
- mod loaders and installers (Forge, Fabric, NeoForge, Quilt, OptiFine);
- instance storage and migration.

Keep `LICENSE`, copyright notices, and third-party attribution. MojoRemake is a
derivative of MojoLauncher under LGPLv3.

## Done

- [x] Fork created and wired as `origin`; upstream kept as `upstream`.
- [x] `dev` branch created, pushed, and set as the fork's default branch.
- [x] Full-autonomy `AGENTS.md` policy replaces the upstream policy.
- [x] `CLAUDE.md` points at `AGENTS.md`.
- [x] `README.md` and `README_RU.md` rewritten for MojoRemake.
- [x] CI verified on the fork: both debug APKs build successfully. Upstream-only
  Mesa and publishing steps are skipped on forks.
- [x] CI versioning implemented and verified (run `39394222720`, build #4):
  - `appVersion=1.0.1` in `gradle.properties` is the single version source.
  - Debug APKs are named
    `MojoRemake-v1.0.1-b<run>-full-debug.apk` and
    `MojoRemake-v1.0.1-b<run>-noruntime-debug.apk`.
  - `versionName` is `v1.0.1` (full) / `v1.0.1-noruntime` (noruntime).
  - `versionCode` is `(major*10000 + minor*100 + patch)*1000 + CI run`.
  - Debug package IDs are unique per CI run
    (`git.artdeell.mjlaunch.debug.b<run>` and
    `git.artdeell.mjlaunch.noruntime.debug.b<run>`), so full/noruntime and
    different CI runs can be installed side by side.
  - Storage provider authorities and the custom-controls target package follow
    the final variant application ID.
- [x] UI/UX remake implemented and documented (`docs/UI_REMAKE_PLAN.md`):
  - New dark design system (colors, spacing, type, widgets) and original
    vector icons/splash mark with an adaptive launcher icon.
  - New shell in `LauncherActivity`: top bar with back/title/account chip,
    Home/Instances/Browse/Downloads/Settings tabs, bottom nav on phones and a
    navigation rail at `screenWidthDp >= 600`.
  - Home dashboard, instance library with search/sort and empty state,
    instance creation flow, rebuilt editor with dirty-state guard, rebuilt
    Browse with 350ms debounce and import, Downloads screen, custom Settings
    hub, About screen, and account manager.
  - Branding swept to MojoRemake; upstream Discord/Telegram/Play links removed.
  - Notification permission is requested contextually from Downloads/Settings,
    never at first launch; insets stay owned by `Tools.setInsetsMode`.
- [x] UI verified in CI, run
  [`39418819416`](https://github.com/thedeadsoul108/MojoLauncherRemake/actions/runs/39418819416)
  (build #8, commit `3350c68`):
  - `MojoRemake-v1.0.1-b8-full-debug.apk` —
    `git.artdeell.mjlaunch.debug.b8`, `versionName v1.0.1`
  - `MojoRemake-v1.0.1-b8-noruntime-debug.apk` —
    `git.artdeell.mjlaunch.noruntime.debug.b8`,
    `versionName v1.0.1-noruntime`
  - Both: `versionCode 10001008`, `minSdk 21`, `targetSdk 36`, label
    `MojoRemake`, and the new shell layouts/resources present in the APK.
- [x] Commits: `50aee53` (design system/branding), `4c7c62a` (shell and
  screens), `3b32803` (plan), `f359a7c` and `3350c68` (AAPT style-parent
  fixes).
- [x] Modrinth Browse and installation (`30867d6`, `cf8e4bf`):
  - v2 search/detail/version APIs with percent-encoded JSON facets, project
    type/version/loader/category/environment filters, sort and pagination;
  - 350 ms debounce, session cache, disk cache with TTL, retry with exponential
    backoff, explicit 429 `Retry-After` handling, skeleton rows, cached-offline
    results and an error/retry state;
  - installs mods, resource packs, shader packs and data packs into
    `mods/`, `resourcepacks/`, `shaderpacks/` and `datapacks/` respectively;
  - downloads only over HTTPS to a temporary sibling, requires and verifies
    the Modrinth SHA-1, then renames into place; partial files are removed on
    failure;
  - retains local `.mrpack`/Modrinth pack import and the legacy modpack APIs.
- [x] In-app skin editor (`450974b`, `9a79c5c`):
  - Crafatar previews for remote players and a local body renderer for drafts;
  - 64x32 classic and 64x64 slim validation, 2 MiB cap, PNG signature/IHDR
    checks, variant selection, URL/player lookup and local persistence;
  - Microsoft profile skin upload through the authenticated Minecraft API;
  - Ely.by and local skins are stored locally because neither provides a public
    third-party skin-upload API;
  - capes are listed read-only with an honest explanation: no public API exists
    to change the active cape.
- [x] Instance management (`8ec70e4`, `20760c3`):
  - per-instance notes in the editor and instance metadata;
  - duplication into a new root with a safe `Copy of …` name and cleanup on
    failure;
  - streaming export through `ACTION_CREATE_DOCUMENT`, deterministic entry
    order and timestamps, path-containment checks, and state restored after
    rotation;
  - content manager listing mods, resource packs, shader packs and data packs,
    enable/disable by adding or removing `.disabled`, background scanning, and
    Modrinth SHA-1 update checks for enabled files (up to 20 per run, with an
    explicit limit notice).
- [x] First-run onboarding and Downloads (`5901bd8`):
  - onboarding only when both the instance library and account list are empty,
    with persistent completion flag; create-instance, sign-in and skip exits;
  - Downloads empty state offers a direct Browse Modrinth action.
- [x] Lifecycle and rotation hardening (`9bc7be9`, `d7bac5d`, `40af95b`):
  - load markers reset and stale callbacks discarded after view recreation on
    Home, Instances, Accounts, Browse and the content screen;
  - task-count callbacks dropped after detach;
  - content switches revert while a scan/check/toggle is active, and buttons
    are disabled while busy;
  - persisted skins are revalidated on load; upload completion after rotation
    re-enables the new view's actions;
  - stream copies handle zero-length reads correctly.
- [x] JVM unit tests run in CI before APK builds:
  - skin validation, skin variant rules, deterministic export, path containment,
    content scanning/enable-disable naming, update-check eligibility, Modrinth
    facet/version URL encoding and task-count detach behaviour;
  - 14 tests pass locally and in the CI unit-test step.
- [x] Final CI verification (run
  [`39521502939`](https://github.com/thedeadsoul108/MojoLauncherRemake/actions/runs/39521502939),
  build #20, commit `890c01e`):
  - `MojoRemake-v1.0.1-b20-full-debug.apk`:
    `git.artdeell.mjlaunch.debug.b20`, `versionName v1.0.1`;
  - `MojoRemake-v1.0.1-b20-noruntime-debug.apk`:
    `git.artdeell.mjlaunch.noruntime.debug.b20`,
    `versionName v1.0.1-noruntime`;
  - both have `versionCode 10001020`, `minSdk 21`, `targetSdk 36`, label
    `MojoRemake`, the new shell/content/skin/onboarding layouts, and the
    expected adaptive icons; the published MD5 files match the downloaded APKs;
  - the full APK additionally contains the five JRE archives
    (`bin-arm`, `bin-arm64`, `bin-x86`, `bin-x86_64`, `universal`);
    the noruntime APK contains no JRE archives;
  - earlier remake runs `39436338573`, `39519281205`, `39519871813`,
    `39521148344` and `39521502939` all passed the unit-test step and both APK
    builds.

## Known limitations

- **No on-device testing in this environment.** Rotation, TalkBack, gesture
  insets, 360 dp layout, tablets and Android 5/old GLES2 devices are checked
  only by code review and resource compilation, not on hardware or emulators.
- **Live service calls are not exercised by CI.** Browse, content update checks,
  Microsoft upload and Ely.by flow compile and have their pure logic tested, but
  no real account/network session was available. Microsoft endpoints can change
  without notice.
- **Capes are read-only.** Minecraft exposes no public cape-equip endpoint, and
  Ely.by does not expose third-party cape management. The UI reports this rather
  than pretending the change worked.
- **Ely.by/local skin uploads are local-only.** They persist in app storage and
  render locally; they are not uploaded to Ely.by. Microsoft uploads use the
  profile API.
- **Update checks report, but do not install.** They are limited to 20 enabled
  files per run, match only files known to Modrinth by SHA-1, and do not resolve
  dependencies or choose release channels.
- **Downloads shows active tasks only.** There is no persistent completed-task
  history or cancel button because `ProgressKeeper` exposes no cancellation or
  completion result.
- **Export is a plain ZIP, not `.mrpack`.** It includes the instance directory
  it can see; shared-data instances keep their shared content outside that root.
  Large exports have no cancel/progress UI.
- **New strings are English-only.** Existing locales are preserved but fall back
  to English for the remake's new screens.
- **The launcher icon/splash are original provisional vectors.** Final artwork
  was not supplied.
- **JVM tests only.** No Robolectric/Android instrumentation suite was added, so
  fragment lifecycle guards are verified by characterization tests (task-count
  relay) and static review rather than runtime UI tests.

## Next steps

1. **Device smoke test** (the main remaining verification gap): first launch,
   local/Microsoft/Ely.by login, create + edit instance, modpack import,
   Modrinth search/install/update check, skin upload, download progress, game
   launch, rotation, and gesture-nav insets on Android 5/7/10/14.
2. **Live-service check** on a real account: Microsoft skin upload and profile
   refresh, Modrinth rate limiting/offline cache, and a 429 response.
3. **Localization**: the new screens are English-only; other locales fall back
   to English by design (`tools:ignore="MissingTranslation"`). Translate when
   the string set is frozen.
4. **Final art**: replace the provisional original vector launcher icon/splash
   with final artwork when available; keep the current files as the fallback.
5. **Optional follow-ups from device feedback**: update installation with
   dependency resolution, persistent Downloads history/cancel, tablet rail
   spacing, TalkBack labels, and OEM-specific inset behaviour.

## Build notes

- Full Android SDK/NDK builds do not work in Termux. Use GitHub Actions.
- Local submodules can be fetched with
  `git submodule update --init --recursive`.
- CI workflow: `.github/workflows/android.yml` ("MojoRemake CI", JDK 17,
  Gradle 8.14.5, `assembleFullDebug` and `assembleNoruntimeDebug`).
- Version bumps: edit `appVersion` in `gradle.properties`; never reuse a
  version for different code. `versionCode` is derived automatically.
- `CURSEFORGE_API_KEY` is optional; without it the build uses a dummy key.
  Release/publishing steps are gated to the upstream owner and are skipped on
  the fork.
- On Android 9 and older, game storage still uses the shared legacy
  `/sdcard/games/PojavLauncher` path; side-by-side test builds share that data.
  On Android 10+ each package gets app-specific external storage.

## Session rules

- Start new sessions from the repository root so `AGENTS.md` loads.
- Read this file first, then `AGENTS.md`.
- Keep commits focused; Conventional Commits are preferred.
