# MojoRemake

A community remake of [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher),
which is itself based on [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher).
MojoRemake keeps the proven Android game-launching engine and gives it a new
identity and a modern, Modrinth-inspired interface.

MojoRemake runs Minecraft: Java Edition on Android — from old versions to
current snapshots — with support for Forge, Fabric, and mods such as OptiFine.

## Status

MojoRemake is under active development. The rebrand and the new UI are in
progress. Game launching, runtime handling, renderers, authentication, mod
loaders, and instance storage are inherited from MojoLauncher and must keep
working throughout the remake.

## Features

- Runs almost every Minecraft: Java Edition version, from rd-132211 to current
  snapshots.
- Forge and Fabric mod loading, `.jar` installers, and popular mods such as
  OptiFine.
- Instance-based management instead of the old profile system.
- mrpack and CurseForge modpack import.
- OpenLTW (GLES3 core contexts) and GL4ES (GLES2 compatibility) renderers.
  Vulkan is provided by system drivers or by Turnip on Qualcomm devices.
- Account support inherited from the engine, including Microsoft and
  third-party authentication.
- Targets Android 5.0 (API 21) through current Android versions, including old
  GLES2-only devices.

## Builds

There are no stable MojoRemake releases yet. Builds are produced by GitHub
Actions:

- [Actions](https://github.com/thedeadsoul108/MojoLauncherRemake/actions)

## Building

Clone with submodules:

```bash
git clone --recursive https://github.com/thedeadsoul108/MojoLauncherRemake.git
cd MojoLauncherRemake
./gradlew :app_mojo:assembleDebug
```

On Windows, use `.\gradlew.bat`.

The `glfw`, `sdl`, `mojoexec`, and `NoBlocklist` submodules are required for a
full build. `--recursive` handles them; to fetch them later, run:

```bash
git submodule update --init --recursive
```

CI uses JDK 17 and Gradle 8.14.5. See `.github/workflows/android.yml`.

## Project layout

- `app_mojo/` — Android app and Java-side launcher.
- `app_mojo/src/main/jni/` — JNI/C code (`pojavexec`), linking SDL, GLFW, AWT,
  and mojoexec.
- `glfw/`, `sdl/`, `mojoexec/` — native submodules for WSI and EGL/Vulkan
  sharing.
- `forge_installer/` — Forge installer support.
- `NoBlocklist/` — support library submodule.
- `docs/` — project notes and progress.

## License

MojoRemake is distributed under the [GNU LGPLv3](LICENSE), inherited from
MojoLauncher. Original copyright notices and attribution must be preserved.

## Credits

MojoRemake is built on the work of the MojoLauncher and PojavLauncher projects
and their contributors.

- [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) — GNU LGPLv3.
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) — GNU LGPLv3.

### Third-party components and licenses

- Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
- [Holy GL4ES](https://github.com/artdeell/gl4es_extra_extra/): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
- [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
- [GLFW](https://github.com/MojoLauncher/glfw): [zlib license](https://github.com/MojoLauncher/glfw/blob/glfw34/LICENSE.md).
- [SDL](https://github.com/MojoLauncher/MojoSDL): [zlib license](https://github.com/MojoLauncher/MojoSDL/blob/main/LICENSE.txt).
- [LWJGL2-GLFW](https://github.com/MojoLauncher/lwjgl2-glfw): 3-Clause BSD license.
- [LWJGL3](https://github.com/LWJGL/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
- [mojoexec](https://github.com/MojoLauncher/mojoexec): [MIT License](https://github.com/MojoLauncher/mojoexec/blob/master/LICENSE).
- [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
- [pro-grade](https://github.com/pro-grade/pro-grade) (Java sandboxing security manager): [Apache License 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt).
- [bhook](https://github.com/bytedance/bhook) (exit code trapping): [MIT License](https://github.com/bytedance/bhook/blob/main/LICENSE).
- [Authlib-Injector](https://github.com/yushijinhun/authlib-injector) (authentication via ely.by): [AGPL-3.0](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE).
- [alsoft](https://github.com/kcat/openal-soft/) (audio output): [GNU Library General Public License](https://github.com/kcat/openal-soft/blob/master/COPYING) and [modified PFFFT](https://github.com/kcat/openal-soft/blob/master/LICENSE-pffft).
- [oboe](https://github.com/google/oboe): [Apache License 2.0](https://github.com/google/oboe/blob/main/LICENSE).
- Thanks to [Mineskin](https://mineskin.eu/) for providing Minecraft avatars.
