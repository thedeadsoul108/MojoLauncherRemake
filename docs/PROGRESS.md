# MojoRemake — progress and handoff

Last updated: 2026-10-20

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

## Next steps

1. **Device smoke test** (the only remaining verification gap): first launch,
   local/Microsoft/Ely.by login, create + edit instance, modpack
   import, download progress, game launch, rotation, and gesture-nav insets on
   Android 5/7/10/14.
2. **Localization**: the new screens are English-only; other locales fall back
   to English by design (`tools:ignore="MissingTranslation"`). Translate when
   the string set is frozen.
3. **Final art**: replace the provisional original vector launcher icon/splash
   with final artwork when available; keep the current files as the fallback.
4. **Polish pass after device feedback**: tablet rail spacing, TalkBack labels,
   and any OEM-specific inset behavior.

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
