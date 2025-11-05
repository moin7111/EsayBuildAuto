# EsayBuildAuto

NeoForge mod workspace targeting Minecraft `1.21.10`, set up with the official ModDev Gradle plugin.

## Entwickeln

- `./gradlew build` baut das Mod-JAR unter `build/libs/`.
- `./gradlew runClient` startet einen Entwicklungsclient.
- `./gradlew runServer` startet einen lokalen Server.
- `./gradlew runData` generiert Ressourcen ueber den Data Generator.

Vor dem ersten Import in IntelliJ den Gradle-Wrapper einmal ausfuehren (`./gradlew tasks`), damit alle Abhaengigkeiten heruntergeladen werden. IntelliJ kann das Projekt anschliessend ueber die `build.gradle` im Wurzelverzeichnis importieren.

Die Mod-Metadaten (z. B. `mods.toml`) werden aus Vorlagen erzeugt - dafuer sorgt der Task `generateModMetadata`, der automatisch beim Synchronisieren/Build laeuft.

## Server-Sicherheit (Insta-Build)

Der serverseitige Insta-Build-Modus (PasteMode `ATOMIC`) unterliegt Sicherheitspruefungen. Konfiguriere sie ueber `config/esaybuildauto-common.toml`:

- `server.instaBuild.enabled`: Schaltet Insta-Build global an/aus.
- `server.instaBuild.minPermissionLevel`: Mindest-Permission-Level (0-4) fuer Vanilla-Operatorrechte.
- `server.instaBuild.requireWhitelist`: Erzwingt Whitelist-Eintrag unabhaengig von Permission-Level.
- `server.instaBuild.playerWhitelist`: Spielername oder UUID, die immer zugelassen sind.
- `server.instaBuild.allowedTeams`: Scoreboard-Teams, die Insta-Build nutzen duerfen.
- `server.instaBuild.allowedTags`: Entity-Tags (via `/tag`), die Zugriff gewaehrleisten.
- `server.instaBuild.auditLog`: Schreibt Entscheidungen nach `world/easybuild/insta_build_audit.log`.

Alle Entscheidungen werden geloggt; verweigerte Anfragen senden eine `BuildFailed`-Antwort mit `PERMISSION_DENIED` an den Client.
