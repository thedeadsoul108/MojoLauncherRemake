# MojoRemake

Неофициальный ремейк [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher),
который основан на [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher).
MojoRemake сохраняет проверенный движок запуска игр для Android и получает новое
имя и современный интерфейс в духе Modrinth.

MojoRemake запускает Minecraft: Java Edition на Android — от старых версий до
актуальных снапшотов — с поддержкой Forge, Fabric и модов вроде OptiFine.

## Статус

MojoRemake находится в активной разработке. Ребрендинг и новый интерфейс ещё
делаются. Запуск игры, работа с runtime, рендереры, авторизация, модлоадеры и
хранение инстансов унаследованы от MojoLauncher и должны продолжать работать
на протяжении всего ремейка.

## Возможности

- Запуск почти всех версий Minecraft: Java Edition — от rd-132211 до текущих
  снапшотов.
- Forge и Fabric, установщики `.jar` и популярные моды вроде OptiFine.
- Управление через инстансы вместо старых профилей.
- Импорт модпаков mrpack и CurseForge.
- Рендереры OpenLTW (контексты GLES3) и GL4ES (совместимость с GLES2).
  Vulkan — через системные драйверы или Turnip на устройствах Qualcomm.
- Поддержка аккаунтов из движка, включая Microsoft и стороннюю авторизацию.
- Поддержка Android 5.0 (API 21) и новее, включая старые устройства с GLES2.

## Сборки

Стабильных релизов MojoRemake пока нет. Сборки создаются в GitHub Actions:

- [Actions](https://github.com/thedeadsoul108/MojoLauncherRemake/actions)

## Сборка

Клонируйте репозиторий вместе с сабмодулями:

```bash
git clone --recursive https://github.com/thedeadsoul108/MojoLauncherRemake.git
cd MojoLauncherRemake
./gradlew :app_mojo:assembleDebug
```

В Windows используйте `.\gradlew.bat`.

Для полной сборки нужны сабмодули `glfw`, `sdl`, `mojoexec` и `NoBlocklist`.
Флаг `--recursive` скачивает их сразу; позже можно выполнить:

```bash
git submodule update --init --recursive
```

CI использует JDK 17 и Gradle 8.14.5. См. `.github/workflows/android.yml`.

## Структура проекта

- `app_mojo/` — Android-приложение и Java-часть лаунчера.
- `app_mojo/src/main/jni/` — JNI/C-код (`pojavexec`), связывающий SDL, GLFW,
  AWT и mojoexec.
- `glfw/`, `sdl/`, `mojoexec/` — нативные сабмодули для WSI и обмена
  EGL/Vulkan.
- `forge_installer/` — поддержка установщика Forge.
- `NoBlocklist/` — вспомогательный сабмодуль.
- `docs/` — заметки по проекту и прогресс.

## Лицензия

MojoRemake распространяется под [GNU LGPLv3](LICENSE), унаследованной от
MojoLauncher. Оригинальные копирайты и указание авторства должны сохраняться.

## Благодарности

MojoRemake построен на работе проектов MojoLauncher и PojavLauncher и их
участников.

- [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) — GNU LGPLv3.
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) — GNU LGPLv3.

### Сторонние компоненты и лицензии

- Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
- [Holy GL4ES](https://github.com/artdeell/gl4es_extra_extra/): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
- [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
- [GLFW](https://github.com/MojoLauncher/glfw): [zlib license](https://github.com/MojoLauncher/glfw/blob/glfw34/LICENSE.md).
- [SDL](https://github.com/MojoLauncher/MojoSDL): [zlib license](https://github.com/MojoLauncher/MojoSDL/blob/main/LICENSE.txt).
- [LWJGL2-GLFW](https://github.com/MojoLauncher/lwjgl2-glfw): 3-Clause BSD license.
- [LWJGL3](https://github.com/LWJGL/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
- [mojoexec](https://github.com/MojoLauncher/mojoexec): [MIT License](https://github.com/MojoLauncher/mojoexec/blob/master/LICENSE).
- [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
- [pro-grade](https://github.com/pro-grade/pro-grade) (песочница Java): [Apache License 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt).
- [bhook](https://github.com/bytedance/bhook) (перехват кода выхода): [MIT License](https://github.com/bytedance/bhook/blob/main/LICENSE).
- [Authlib-Injector](https://github.com/yushijinhun/authlib-injector) (авторизация через ely.by): [AGPL-3.0](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE).
- [alsoft](https://github.com/kcat/openal-soft/) (вывод звука): [GNU Library General Public License](https://github.com/kcat/openal-soft/blob/master/COPYING) и [modified PFFFT](https://github.com/kcat/openal-soft/blob/master/LICENSE-pffft).
- [oboe](https://github.com/google/oboe): [Apache License 2.0](https://github.com/google/oboe/blob/main/LICENSE).
- Спасибо [Mineskin](https://mineskin.eu/) за аватары Minecraft.
