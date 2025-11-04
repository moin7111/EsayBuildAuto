EasyBuild Dual-Architecture – Netzwerkprotokoll
==============================================

## 1. Überblick
- **Ziel**: Gemeinsames Mod-Nachrichtenprotokoll für Client ↔ Server in NeoForge.
- **Versionierung**: Kanal-ID `easybuild:core` mit Protokollversion `1` (String).
- **Transport**: Konzeptuell wie früher über `SimpleChannel`; Implementierung erfolgt in NeoForge 21 via Payload-Registrierung (`NetworkRegistry.register`), sobald Phase A abgeschlossen wird.
- **Signierung**: Jedes Packet trägt `playerUuid`, `nonce` und `serverTime` (Server validiert Zeitfenster < ±5 s).

## 2. Lifecycle & States
- **Client-Only Modus**: Channel optional; Nachrichten werden nicht gesendet.
- **Handshake** (nur wenn Server-Mod aktiv):
  1. Client sendet `HelloHandshake` (Versionsliste, ClientCapabilities).
  2. Server antwortet `HelloAcknowledge` (ServerCapabilities, FeatureFlags, ConfigSnapshotHash).
  3. Bei Inkompatibilität sendet Server `HandshakeRejected`.

## 3. Gemeinsame Datenstrukturen
- `SchematicRef` → `{ schematicId: string, version: int, checksum: long }`
- `AnchorPos` → `{ dimension: ResourceLocation, x: int, y: int, z: int, facing: Direction }`
- `ChestRef` → `{ blockPos: BlockPos, dimension: ResourceLocation }`
- `MaterialStack` → `{ itemId: ResourceLocation, count: int }`
- `JobId` → ULID/String; eindeutige Vergabe durch Server.
- `PasteMode` → Enum `{ATOMIC, STEP, SIMULATED}`.

## 4. Nachrichten – Client → Server
| Typ | Zweck | Felder |
| --- | --- | --- |
| `HelloHandshake` | Verbindungsaufbau | `playerUuid`, `clientVersion`, `protocolVersion`, `clientCapabilities` (Set<String>), `nonce`
| `MaterialCheckRequest` | Materialverfügbarkeit prüfen | `playerUuid`, `schematic: SchematicRef`, `anchor: AnchorPos`, `chests: List<ChestRef>`, `clientEstimate: List<MaterialStack>`, `nonce`
| `RequestBuild` | Build-Job starten | `playerUuid`, `schematic`, `anchor`, `mode: PasteMode`, `options` (JsonObject), `requestId` (ULID), `nonce`
| `CancelBuildRequest` | Laufenden Job abbrechen | `playerUuid`, `jobId`, `nonce`
| `AcknowledgeStatus` | UI bestätigt Status | `playerUuid`, `jobId`, `statusCode`, `nonce`

## 5. Nachrichten – Server → Client
| Typ | Zweck | Felder |
| --- | --- | --- |
| `HelloAcknowledge` | Handshake-Antwort | `protocolVersion`, `serverVersion`, `serverCapabilities`, `configHash`, `nonce`, `serverTime`
| `HandshakeRejected` | Abbruch | `reason`, `requiredProtocol`, `serverTime`
| `MaterialCheckResponse` | Ergebnis Materialsichtung | `schematic`, `ok: boolean`, `missing: List<MaterialStack>`, `reserved: boolean`, `reservationExpiresAt`, `nonce`, `serverTime`
| `BuildAccepted` | Job registriert | `jobId`, `mode`, `estimatedDurationTicks`, `reservationToken`, `nonce`, `serverTime`
| `ProgressUpdate` | Fortschritt | `jobId`, `placed`, `total`, `phase` (Enum `{QUEUED, RESERVING, PLACING, PAUSED, ROLLING_BACK, COMPLETED}`), `message`, `nonce`, `serverTime`
| `BuildCompleted` | Abschluss | `jobId`, `success`, `consumed: List<MaterialStack>`, `logRef`, `nonce`, `serverTime`
| `BuildFailed` | Fehlerdetail | `jobId`, `reasonCode`, `details`, `rollbackPerformed: boolean`, `nonce`, `serverTime`
| `MissingMaterials` | Alternative Antwort | `schematic`, `missing`, `suggestedSources`, `nonce`, `serverTime`
| `RegionLocked` | Region belegt | `schematic`, `lockingJob`, `owner`, `etaTicks`, `nonce`, `serverTime`

## 6. Ablauf (Transactional Step Paste)
1. Client sendet `MaterialCheckRequest`.
2. Server scannt Inventare, reserviert Items; bei Erfolg → `MaterialCheckResponse(ok=true, reserved=true)`.
3. Client bestätigt UI, sendet `RequestBuild` (optional nach Bestätigung).
4. Server erzeugt Job, sendet `BuildAccepted`.
5. Job-Worker verarbeitet Batches → regelmäßige `ProgressUpdate`.
6. Erfolg → `BuildCompleted(success=true)`; Fehler → `BuildFailed` (ggf. `phase=ROLLING_BACK`).

## 7. Reservierung & Sicherheit
- Reservierungen besitzen `reservationToken` und Timeout `reservationExpiresAt` (EpochMilli).
- Server prüft `nonce` + `playerUuid` gegen LRU-Cache (Replay-Schutz).
- Rate-Limits pro Spieler; bei Überschreitung `BuildFailed(reason=RATE_LIMIT)`.
- Optionaler Audit-Log: `BuildCompleted` referenziert gespeicherten Datensatz (`logRef`).

## 8. Fehlercodes & Status
- `PERMISSION_DENIED`, `MATERIALS_CHANGED`, `REGION_LOCKED`, `CHUNK_UNLOADED`, `PLUGIN_BLOCKED`, `SERVER_DISABLED`, `RATE_LIMIT`.
- `statusCode` in `AcknowledgeStatus`: `MATERIALS_VIEWED`, `REQUEST_CONFIRMED`, `ABORT_CONFIRMED`.
- `phase` enum unterstützt UI-Anzeige (siehe `ProgressUpdate`).

## 9. Erweiterbarkeit
- Neue Packets erfordern Protokoll-Bump (`protocolVersion` ↑); alte Clients skippen unbekannte IDs.
- `options` (JSON) erlaubt Feature-Flags wie `{ "requireConfirmation": true }` ohne zusätzliche Felder.
- Reserve-/Job-Daten werden serverseitig persistiert (LevelSavedData oder Datenbank) → Wiederanlauf nach Restart.

## 10. Beispiel-Payloads
```json
// MaterialCheckRequest
{
  "playerUuid": "52f9c5c8-6d9f-4cc0-9c69-9b7f5d7d9a10",
  "schematic": { "schematicId": "mega_base", "version": 3, "checksum": 912345678 },
  "anchor": { "dimension": "minecraft:overworld", "x": 128, "y": 64, "z": -240, "facing": "NORTH" },
  "chests": [ { "blockPos": { "x": 130, "y": 64, "z": -238 }, "dimension": "minecraft:overworld" } ],
  "clientEstimate": [ { "itemId": "minecraft:stone", "count": 4096 } ],
  "nonce": 1382749234
}

// ProgressUpdate
{
  "jobId": "01J8MAV7929ZC4Q6P2KQ9A7N9H",
  "placed": 1500,
  "total": 8000,
  "phase": "PLACING",
  "message": "Batch 3/16",
  "nonce": 429132,
  "serverTime": 1730716800123
}
```

## 11. Offene Designpunkte
- Festlegung des Serialisierungsformats (FriendlyByteBuf Schema) → später in `EasyBuildNetwork.java` umgesetzt.
- Schlüsselverwaltung für `reservationToken` (wahrscheinlich UUID via `UUID.randomUUID()`).
- Abstimmungslogik für Multi-Server-Installationen (Proxy/Bungee) bleibt zu spezifizieren.
