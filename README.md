# EsayBuildAuto

NeoForge mod workspace targeting Minecraft `1.21.10`, set up with the official ModDev Gradle plugin.

## Entwickeln

- `./gradlew build` baut das Mod-JAR unter `build/libs/`.
- `./gradlew runClient` startet einen Entwicklungsclient.
- `./gradlew runServer` startet einen lokalen Server.
- `./gradlew runData` generiert Ressourcen ueber den Data Generator.

Vor dem ersten Import in IntelliJ den Gradle-Wrapper einmal ausfuehren (`./gradlew tasks`), damit alle Abhaengigkeiten heruntergeladen werden. IntelliJ kann das Projekt anschliessend ueber die `build.gradle` im Wurzelverzeichnis importieren.

Die Mod-Metadaten (z. B. `mods.toml`) werden aus Vorlagen erzeugt - dafuer sorgt der Task `generateModMetadata`, der automatisch beim Synchronisieren/Build laeuft.
