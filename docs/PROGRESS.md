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
- [x] No game-engine or UI code has been modified yet; only build/CI versioning.

## Next steps

1. **UI/UX remake (main work)**
   - Read `prompt.txt` at the repository root (local file, intentionally not
     committed) for the full first-session brief.
   - Shell: `LauncherActivity`, `BaseActivity`, `MainMenuFragment`,
     `app_mojo/src/main/res/layout/`.
   - Theme and colors: `app_mojo/src/main/res/values/`.
   - Keep game/instance flows reachable while the UI is replaced.
   - Write `docs/UI_REMAKE_PLAN.md`, then implement in the same session.
2. **Minimal rebrand (part of UI work)**
   - Display name and strings: `app_mojo/src/main/res/values/strings.xml`
     (`app_short_name`), `app_mojo/src/main/AndroidManifest.xml`.
   - App identity: `app_mojo/build.gradle` (`applicationId`, `namespace`
     stays `git.artdeell.mojo` for now), `settings.gradle` (`rootProject.name`).
   - URLs and constants: `app_mojo/src/main/java/git/artdeell/mojo/Tools.java`.
   - Icon and splash assets in `app_mojo/src/main/res/`.
   - README/NOTICE/privacy docs.
   - Do not change the base application ID or the CI debug/flavor suffixes.
3. **Feature work after the shell is stable**
   - Instance list/home, mod browser/installer UX, account flow, settings,
     downloads/progress, and error states.

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
