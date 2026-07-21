<img
    align="right" alt="title" width="200px"
    src="https://avatars.githubusercontent.com/u/126124815?s=400&u=c274f4ff6a9ff62698476de6bf66cacf1d6bed8e"
/>

# Weave Lunar Loader

Weave Lunar Loader is a performance-focused fork of Weave Loader with dedicated Lunar Client integration. It allows
developers to easily create mods by providing a simple and intuitive API for interacting with Minecraft's codebase,
while supporting injection into clients that are somewhat closed-off to developers.

## Fork Scope

This fork keeps Weave's mod format and APIs while moving the reusable Lunar-specific behavior out of the former
custom agent loader. Its Lunar integration currently provides:

- Native Lunar toast notifications discovered structurally, without hardcoded obfuscated class names.
- Lazy, version-scoped discovery cached under `~/.weave/cache/lunar-notifications`, with class fingerprints that
  invalidate the cache when Lunar changes relevant bytecode without changing its displayed version.
- A five-second retry bound while Lunar is still starting; temporary startup state does not delete a valid cache.
- Allocation-free event dispatch when an event has no subscribers.
- Constant-time modifier lookup during class transformation.
- Allocation-free package checks in Lunar's transformed classloader.

This project intentionally contains no authentication, key validation, public keys, licensing dialogs, key generator,
Discord bot, or auth-gated mod-loading behavior from the former custom loader.

### Legacy AgentMod Compatibility

The former loader's AgentMod v1 and v2 APIs are translated onto Weave without starting a second Java agent or a
second Mixin environment. Native Weave jars and legacy AgentMod jars can be placed together in either
`~/.weave/mods/` or `~/.weave/mods/<minecraft-version>/`. Legacy events, packet cancellation, commands, GUIs,
notifications, and runtime class transforms are routed through Weave, while legacy per-mod data is stored in
`~/.weave/mod-data/<minecraft-version>/<mod-id>/`. Existing data is copied there once from the former
`~/.minecraft/agentmods-data/<mod-id>/` location without deleting the original.

Legacy commands are registered with Weave's native command bus, including command-name suggestions, argument
completion through `Command.onTabComplete`, and integration with server-provided completions. The chat interceptor is
retained only as a fallback if the version-specific command API is unavailable.

The old `~/.minecraft/agentmods/` directory is not scanned.

Legacy compatibility debug logging is disabled by default. Enable reflection traces, command routing diagnostics, and
stack traces only when troubleshooting with the JVM argument `-Dweave.legacyAgentMods.debug=true`.

### Lunar Notification API

The API is safe to call on non-Lunar clients and returns `false` when native notifications are unavailable.

```kotlin
import net.weavemc.api.lunar.LunarClient

LunarClient.sendNotification("My Mod", "Loaded successfully")
```

## Supported Clients / Versions

<table>
<tr><th>Supported Clients</th><th>Supported Versions</th></tr>
<tr><td>
        
| Client  |     Supported      |
|---------|:------------------:|
| Vanilla | :white_check_mark: |
| Forge   | :white_check_mark: |
| Lunar   | :white_check_mark: |
| Badlion |        :x:         |
| Feather |        :x:         |
| Labymod | :white_check_mark: |

</td><td>
    
| Version |            Supported             |
|---------|:--------------------------------:|
| 1.7     | :white_check_mark:<sup>(1)</sub> |
| 1.8     |        :white_check_mark:        |
| 1.12    |        :white_check_mark:        |
| 1.16    |        :white_check_mark:        |
| 1.17    |        :white_check_mark:        |
| 1.18    |        :white_check_mark:        |
| 1.19    |        :white_check_mark:        |
| 1.20    |        :white_check_mark:        |

</td></tr>
</table>

<sup>(1)</sup>Currently, Weave does not work on Forge 1.7.

**If you find any issues with supported clients, please report them to us.**

## Installation

To install Weave-Loader, you can either download a pre-built release from
the [releases page](https://github.com/adoringlol/Weave-Loader/releases), or build it yourself from source.

### Building with Gradle

- First clone the project with [Git][git] then `cd` into the project directory: 

```bash
git clone --recursive https://github.com/adoringlol/Weave-Loader
cd Weave-Loader
```

You then need to give permission to the Gradle wrapper and run the `build` task. This can be done a bit differently
depending on your operating system:

<details open>
<summary><b>UN*X</b> (Linux, BSDs, macOS, etc.)</summary>

```bash
chmod +x ./gradlew
./gradlew build
```
</details>

<details>
<summary><b>Windows</b></summary>

```powershell
.\gradlew.bat build
```
</details>

Lastly, the finalised Loader Agent JAR can be found at `loader/build/libs/weave-lunar-loader-<version>-all.jar`.

## Usage

To use Weave-Loader, you have two options:
- Use [Weave-Manager](https://github.com/Weave-MC/Weave-Manager) to handle the process of attaching Weave to your preferred Minecraft client automatically.
- Manually add the Weave-Loader agent mentioned in the previous step to the JVM arguments when launching Minecraft.
  - You will need to include the following argument: `-javaagent:$PATH_TO_AGENT`

Weave will automatically load mods from `~/.weave/mods/`.

## Contributing

We welcome contributions from anyone interested in improving Weave-Loader. If you find a bug or have an idea for a new
feature, feel free to submit a pull request.

---

<div align="right">

Weave-Loader is licensed under the [GNU General Public License v3.0][license].

</div>

[git]:     https://git-scm.com/

[license]: https://github.com/adoringlol/Weave-Loader/blob/master/LICENSE
