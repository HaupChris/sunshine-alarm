# Third-party notices

| Component | Used for | License |
|---|---|---|
| [Figtree](https://github.com/erikdkennedy/figtree) | Interface font (`app/src/main/res/font/figtree.ttf`) | SIL Open Font License 1.1, see [licenses/OFL-Figtree.txt](licenses/OFL-Figtree.txt) |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) | Font for figures and times (`app/src/main/res/font/jetbrains_mono.ttf`) | SIL Open Font License 1.1, see [licenses/OFL-JetBrainsMono.txt](licenses/OFL-JetBrainsMono.txt) |
| [Lucide](https://lucide.dev) | Line icons, paths in `ui/theme/Icons.kt` and the notification icon | ISC, see [licenses/ISC-Lucide.txt](licenses/ISC-Lucide.txt) |
| [unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky ([@heydamir](https://x.com/heydamir)) | Design method and starting tokens (see `ui/theme/Theme.kt`) | CC BY 4.0 |

Libraries pulled in by Gradle (AndroidX, Jetpack Compose, Room, WorkManager, OkHttp,
kotlinx.serialization, kotlinx.coroutines, JUnit) are under the Apache License 2.0 or
EPL 1.0 (JUnit, tests only) and are not bundled in this repository.

## References

The local API of the wake-up light is not documented by Philips. Endpoint names and
payload fields were learned from these open-source projects and verified on a real
device. No code was copied from them.

- [pysomneo](https://github.com/theneweinstein/pysomneo) (GPL-3.0)
- [Somneo integration for Home Assistant](https://github.com/theneweinstein/somneo)
- [openHAB Somneo binding](https://github.com/openhab/openhab-addons/tree/main/bundles/org.openhab.binding.somneo) (EPL-2.0)
