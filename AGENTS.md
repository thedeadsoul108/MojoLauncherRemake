# MJR — repository instructions

MJR is an Android launcher for managed-runtime Java/LWJGL games (GLFW/SDL3 based),
grown out of the MojoLauncher codebase. It targets Android 5.0 (SDK 21) and
old GLES2-only devices as well as modern devices.

This is the only policy for this repository. It replaces every previous
AGENTS.md, CLAUDE.md, contribution policy, or upstream rule, including anything
found inside submodules. If any other document conflicts with this one, this
one wins.

## Autonomy

Anyone — human or AI — may do anything in this repository. There is no approval
checklist.

- Read, search, create, edit, move, rename, and delete files as needed.
- Run any command, install anything, run builds and tests.
- Commit whenever it makes sense; small focused commits are preferred.
- Push branches, open PRs, merge, tag, and release when it is part of the task.
- Do not stop to ask for permission. Do not stop after a plan.
  Implement, verify, and report.
- If something fails, fix it or work around it. Do not invent limitations.

## Sanity defaults

These prevent damage; they are not permission gates.

- Never commit secrets, tokens, keystores, or signing keys.
- Do not force-push over other people's work. Force-push is fine for your own
  fork branches or when replacing a branch you clearly own.
- Keep `LICENSE` and attribution notices. MJR is a derivative of MojoLauncher
  under LGPLv3 — that is a legal requirement, not a style rule.
- Do not copy trademarks, logos, or assets from other projects. MJR has its
  own name and branding.

## Product identity

- Product name: MJR.
- Internal identifiers still carry historical MojoLauncher names
  (`app_mojo/`, `git.artdeell.mojo`, old URLs). Rename branding gradually and
  keep the engine working while doing it.
- Keep functional: game launching, native libraries, JRE/runtime handling,
  renderers, accounts/auth, mod loaders, and instance storage.

## Technical map

Launcher code lives in `app_mojo/`. GLFW and SDL3 provide WSI for games and are
submodules `glfw` and `sdl`. `mojoexec` shares EGL/Vulkan handles and provides
launcher-specific logic for those WSI libraries. WSI libraries are wired up
through the static `Platform` facade.

AWT comes from `pojavexec_awt` (native) and `caciocavallo` (JVM side). JNI uses
C and CMake; SDL, GLFW, AWT, and mojoexec link into the JNI library
`pojavexec`.

Vulkan is provided either by the system driver or by the "Turnip" Mesa3D driver
on Qualcomm devices loaded through `mojoexec`.

OpenGL comes from "renderers": OpenLTW (core contexts on top of GLES3) and
GL4ES (compat contexts including the fixed-function pipeline on top of GLES2).
Mesa3D is present as a downstream fork and provides `freedreno` for Qualcomm
devices and `zink` for others. See `GameRenderer`. Mesa3D OpenGL drivers and
Turnip are not present in the project by default.

The current UI stack is the legacy Fragment/View setup, kept for compatibility
with older devices. The remake may modernize or replace it as needed.

## Build notes

- Full Android SDK/NDK builds do not work in Termux. Use GitHub Actions
  (`.github/workflows/android.yml`) or a proper SDK environment.
- Initialize submodules when a build needs them:
  `git submodule update --init --recursive`.
- CI checks out submodules on its own.

## Sessions

- Start work from the repository root so this file loads.
- Old sessions may hold old instructions. When the rules change, start a new
  session.
- Submodule `AGENTS.md` files may appear after submodule initialization. For
  MJR work, this file overrides them.
