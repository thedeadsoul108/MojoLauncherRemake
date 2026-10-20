# MojoRemake UI remake plan

Status: implementing in the first UI session (2026-10-20).

This document records the audit that drove the remake, the target shell and
screens, the design system, the migration order, and the acceptance criteria.
It is intentionally implementation-facing: every section maps to files in this
repository.

## 1. Audit summary

The existing launcher is functional but visually and structurally dated:

- `LauncherActivity` is a single content host with a top account spinner, a
  settings icon that doubles as a home button, and a bottom `ProgressLayout`.
  There is no tab navigation and no consistent screen title/back affordance.
- `MainMenuFragment` is a wall of full-width 9-patch buttons (wiki, Discord,
  custom controls, install jar, logs, folder), plus a fixed version spinner and
  a huge Play button. Branding and links point at upstream projects.
- Instances are managed through a modal spinner (`mcVersionSpinner`) rather than
  a library. `Instances.loadDisplay()` silently creates a first instance, so an
  honest empty state is impossible.
- The instance editor is a raw form with 32dp gaps, no sections, no validation,
  no dirty-state guard, and destructive delete sitting next to save.
- Create-instance is a wall of loader buttons. Several buttons share layouts
  intended for other screens, and the old layout uses constraint attributes
  inside a `LinearLayout` (ignored at runtime).
- Browse is a search box over a list with no debounce, no clear action, weak
  empty/error states, and an import button far away from the search context.
- Settings is the old preference root, reached through the settings/home toggle.
  About, version, build flavor, credits, and notifications are not surfaced.
- Accounts are only reachable through the top spinner. There is no account
  manager, no active-account indication outside the spinner, and avatars are
  decoded during adapter binding.
- Colors are a small gray/blue set; several UI colors are hardcoded in Java.
  Icons are a mix of pixel-art vectors and upstream webp assets. Dialogs and
  cards are square.
- Notification permission is requested at first launch, before the user has
  started any download.
- Insets are already owned by `Tools.setInsetsMode`, which pads
  `android.R.id.content` and paints `InsetBackground`. This is sound; the remake
  must not add a second insets owner.

Constraints that shape the plan:

- Java + XML Views, AppCompat/Fragments only. No Compose, Kotlin, Material,
  Room, Hilt, MVVM framework, image-loading library, or new permissions.
- Keep the engine, JRE/runtime pipeline, renderers, auth, mod loaders,
  modpacks, instance storage/migration, downloader, and flavors untouched.
- Keep `applicationId`, `namespace`, Java package, Gradle flavors/build types,
  signing, and the CI versioning contract.
- Keep preference keys, `ExtraConstants`, `ExtraCore` events, and all existing
  install/launch flows reachable.

## 2. Target shell

```text
LauncherActivity
└── shell (already inside Tools.setInsetsMode padding)
    ├── top app bar: back | title/wordmark | account chip
    ├── body
    │   ├── navigation rail (>= 600dp)   [Home Instances Browse Downloads Settings]
    │   └── FragmentContainerView (R.id.container_fragment)
    ├── ProgressLayout (compact, tap opens Downloads)
    └── bottom navigation (< 600dp)
```

- `LauncherActivity` remains the only launcher activity and
  `R.id.container_fragment` remains the content host.
- Root tabs replace the fragment and clear the back stack.
- Deep screens use `Tools.swapFragment(...)` and show a back arrow + title.
- A hidden, zero-impact `AccountSpinner` stays in the shell so all existing
  `MOJANG_LOGIN_TODO` / `MICROSOFT_LOGIN_TODO` / `ELYBY_LOGIN_TODO` listeners,
  token refresh, login progress, and spinner selection semantics keep working.
  A new visible account chip reads from `Accounts` and opens the manager.
- The old settings/home toggle is removed. Settings is a root tab.
- Root back exits; deep back pops; the existing Microsoft OAuth
  `canGoBack()` special case stays first in `LauncherActivity.onBackPressed()`.

## 3. Screen map

| Root tab | Implementation | Notes |
| --- | --- | --- |
| Home | `HomeFragment` | selected instance hero, Play/Edit/Create, quick actions, recent instances, active-download card |
| Instances | `InstancesFragment` + `InstanceCardAdapter` | search, sort, responsive card list, select/edit, overflow Play/folder/delete, empty state |
| Browse | rebuilt `SearchModFragment` | 350ms debounce, clear/filter, Modrinth/CurseForge badge, loading/empty/error + retry, local import |
| Downloads | `DownloadsFragment` | live `ProgressKeeper` task snapshots, notification CTA, empty state |
| Settings | `SettingsFragment` | links to existing preference fragments, notifications, accounts, About |

Deep screens:

- Create instance: rebuilt `ProfileTypeSelectFragment` with Vanilla-like,
  Modded, and Modpacks sections. Reuses every existing installer fragment.
- Instance editor: rebuilt `InstanceEditorFragment` with Game, Performance,
  Storage, Danger zone, sticky Save, inline validation, and a discard guard.
- Browse import: Browse root with a one-shot local-import request.
- Account manager: `AccountManagerFragment` (add/switch/remove; active badge).
- About: `AboutFragment` (version, build, flavor, links, credits, license).
- Settings subpages: existing `LauncherPreference*Fragment` classes.
- Auth: existing `SelectAuthFragment`, `LocalLoginFragment`,
  `MicrosoftLoginFragment`, `ElyByLoginFragment` (chooser + local form
  restyled; OAuth WebView unchanged).
- Custom controls, gamepad mapper, runtime manager, file selector, logs/errors:
  remain reachable and keep their existing behavior.

## 4. Design system

### 4.1 Color tokens

Semantic resources (see `res/values/colors.xml`):

| Token | Value | Use |
| --- | --- | --- |
| `bg` | `#0B0D10` | app background |
| `surface` | `#15181E` | cards, nav |
| `surface_raised` | `#1C2028` | menus, chips, pressed surfaces |
| `border` | `#2A303B` | 1dp borders |
| `text_primary` | `#F2F5F8` | titles/body |
| `text_secondary` | `#A5AEBD` | metadata |
| `text_muted` | `#8894A4` | hints/disabled |
| `accent` | `#1BD96A` | primary action/active |
| `accent_pressed` | `#16B858` | pressed |
| `on_accent` | `#04130A` | text on accent |
| `danger` | `#FF5D5D` | destructive |
| `warning` | `#F2B84B` | warnings |
| `info` | `#5CA8FF` | informational |

Contrast was checked against WCAG 2.2 AA (4.5:1 for normal text) on the
darkest relevant surface. `text_muted` was originally `#6F7887`, which only
reached 3.99:1 on `surface`; it was lightened to `#8894A4`, which measures
5.77:1 on `surface`, 5.3:1 on `surface_raised` and 6.32:1 on `bg`.
Measured ratios on `surface`: `text_primary` 16.25:1, `text_secondary`
7.95:1, `accent` 9.46:1, `danger` 5.91:1, `info` 7.2:1, `warning` 9.93:1;
`on_accent` on `accent` is 10.14:1.

Legacy aliases kept for engine screens: `background_app`,
`background_status_bar`, `background_bottom_bar`, `minebutton_color`,
`minebutton_secondary`, `background_overlay`, `primary_text`,
`secondary_text`, `divider`, `icon_outline_color`,
`control_button_color`, `control_button_pressed_color`.

No new UI color is hardcoded in Java.

### 4.2 Typography

System sans only. New screens use `sp` (no `ssp`) and the following styles:

- `TextAppearance.Mojo.Display` 30sp bold
- `TextAppearance.Mojo.ScreenTitle` 24sp medium
- `TextAppearance.Mojo.SectionTitle` 18sp medium
- `TextAppearance.Mojo.CardTitle` 16sp medium
- `TextAppearance.Mojo.Body` 15sp
- `TextAppearance.Mojo.Meta` 13sp secondary
- `TextAppearance.Mojo.Badge` 12sp medium

Line spacing 1.35x; all-caps only for badges.

### 4.3 Shape, spacing, components

- Spacing scale: 4/8/12/16/20/24/32dp.
- Card radius 16dp; button/input radius 12dp; chips fully rounded.
- Buttons 48dp minimum; icon buttons 48dp touch target with 24dp icon.
- Components: `bg_card`, `bg_card_selected`, `bg_card_hero`, `bg_input`,
  `bg_button_primary`, `bg_button_secondary`, `bg_button_danger`,
  `bg_chip`, `bg_chip_accent`, `bg_nav_item`, `bg_account_chip`,
  `bg_action_tile`, `bg_avatar`.
- Styles: `Widget.Mojo.Button.Primary/Secondary/Text/Danger`,
  `Widget.Mojo.Input`, `Widget.Mojo.Card`, `Widget.Mojo.Chip`,
  `Widget.Mojo.IconButton`, `Widget.Mojo.Divider`, `Widget.Mojo.ProgressBar`.

### 4.4 Iconography

Original 24dp vector line icons (`ic_ui_*`), ~1.8-2dp stroke, rounded
caps/joins. The old `ic_px_*` icons stay for engine/preference screens until
those screens are intentionally restyled. Loader logos (`ic_fabric`, `ic_forge`,
`ic_quilt`, `ic_neoforge`) remain because they accurately identify loaders.

The app icon is a new geometric green "M" monogram on a near-black rounded
square, generated for legacy densities and supplied as an adaptive vector
foreground with a monochrome layer. It is provisional and original; no upstream
logo is reused. Splash uses the same mark on the app background.

## 5. Data and state decisions

- New UI uses `Instances.loadAllInstances()` + `loadSelectedInstance()` instead
  of `loadDisplay()` so a real empty library exists. `DisplayInstance` gains a
  public `getInstanceRoot()` for selection/sorting. `Instances` gains a small
  set of public helpers for safe delete/selection fallback.
- Instance lists and accounts load on `MojoApplication.sExecutorService`, then
  post to the UI. Instance icons are pre-warmed off the main thread; the icon
  provider cache is synchronized.
- Root tab selection is saved in instance state and restored. Restored deep
  fragments keep their state; the shell derives title/back state from the
  resumed fragment.
- The editor snapshot-compares fields for dirty state and registers an
  `OnBackPressedCallback`. Discard is explicit; save writes through existing
  `Instance` fields and `Instances.renameInstanceDirectory`.
- Browse keeps `CommonApi`, pagination, and `ModItemAdapter` search semantics.
  The fragment adds debounce/clear/retry and does not fake a Mods tab.
- Downloads reads a read-only `ProgressKeeper` snapshot and a global progress
  callback. It never invents pause/cancel or speed values.
- Notification permission is requested only from Downloads/Settings. First
  launch no longer prompts.

## 6. Migration order

1. Design tokens, styles, drawables, original icons, app icon/splash, strings.
2. Shell layout + `LauncherActivity` root tabs/back/title/account chip/hidden
   spinner/contextual notification API.
3. Home + instance library + adapter + compact cards.
4. Create instance + sectioned editor + delete confirmation.
5. Browse rebuild, Downloads, Settings root, About, Account manager, auth
   chooser/local form restyle.
6. Branding sweep, accessibility labels, responsive/insets review, static
   checks.
7. Commit in focused chunks, push `dev`, run CI, verify both APKs.

## 7. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Losing spinner login/refresh semantics | Keep a hidden `AccountSpinner` in the shell; do not remove any ExtraCore listeners |
| First-instance auto-creation | Use `loadAllInstances()` + `loadSelectedInstance()` in new UI |
| Broken back stack / duplicate roots | Root switches pop everything and replace without back stack; deep flows use `Tools.swapFragment` |
| Editor data loss | Snapshot dirty guard + inline validation; preserve `argsMode` |
| Double insets | Keep `Tools.setInsetsMode` as sole owner; no `WindowInsetsCompat` padding in shell/fragments |
| Main-thread disk I/O | Executor for instances/accounts/delete/icon prewarm; no network on main |
| CI-only build environment | Validate XML/Java statically locally; rely on GitHub Actions for authoritative builds |
| Upstream brand assets | New vector icon set + generated monogram; loader logos only where they identify a loader |

## 8. Acceptance criteria

- `docs/UI_REMAKE_PLAN.md` exists and matches the implemented screens.
- Five root tabs work; root switches clear back stack; deep back returns.
- Home has instance/account/task empty and active states with working actions.
- Instances: load/search/sort/select/create/edit/delete, empty and error states.
- Create instance covers every existing loader/modpack flow.
- Editor preserves all fields, validates, guards dirty back, deletes safely.
- Browse: debounced search, clear, filter, loading/empty/error/retry, import.
- Downloads: live task list from `ProgressKeeper`, notification CTA, empty state.
- Settings root reaches every existing preference screen; About shows version,
  build/flavor, links, license, credits.
- Account manager: add/switch/remove, active state, empty state.
- Visible branding is MojoRemake; upstream community links removed.
- Notification permission is contextual only.
- Insets remain single-owner; phone/tablet nav adapts; touch targets and
  content descriptions are present.
- `full` and `noruntime` debug builds are green in CI with unchanged artifact
  names, package IDs, and version names.
