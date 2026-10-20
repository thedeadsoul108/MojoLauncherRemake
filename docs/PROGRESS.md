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
- [x] No app code has been modified yet.

## Next steps

1. **Minimal rebrand (safe layer)**
   - Display name and strings: `app_mojo/src/main/res/values/strings.xml`
     (`app_short_name`), `app_mojo/src/main/AndroidManifest.xml`.
   - App identity: `app_mojo/build.gradle` (`applicationId`, `namespace`
     stays `git.artdeell.mojo` for now), `settings.gradle` (`rootProject.name`).
   - URLs and constants: `app_mojo/src/main/java/git/artdeell/mojo/Tools.java`.
   - Icon and splash assets in `app_mojo/src/main/res/`.
   - README/NOTICE/privacy docs.
   - Decide the final application ID before any release; changing it later
     changes app identity, provider authorities, and update paths.
2. **Build verification**
   - Push to `dev` and let GitHub Actions build the debug APKs.
   - Fix any fork-specific CI issues before UI work starts.
3. **UI remake**
   - Shell: `LauncherActivity`, `BaseActivity`, `MainMenuFragment`,
     `app_mojo/src/main/res/layout/`.
   - Theme and colors: `app_mojo/src/main/res/values/`.
   - Keep game/instance flows reachable while the UI is replaced.
4. **Feature work after the shell is stable**
   - Instance list/home, mod browser/installer UX, account flow, settings,
     downloads/progress, and error states.

## Build notes

- Full Android SDK/NDK builds do not work in Termux. Use GitHub Actions.
- Local submodules can be fetched with
  `git submodule update --init --recursive`.
- CI workflow: `.github/workflows/android.yml` (JDK 17, Gradle 8.14.5,
  `assembleFullDebug` and `assembleNoruntimeDebug`).
- `CURSEFORGE_API_KEY` is optional; without it the build uses a dummy key.
  Release/publishing steps are gated to the upstream owner and are skipped on
  the fork.

## Session rules

- Start new sessions from the repository root so `AGENTS.md` loads.
- Read this file first, then `AGENTS.md`.
- Keep commits focused; Conventional Commits are preferred.
