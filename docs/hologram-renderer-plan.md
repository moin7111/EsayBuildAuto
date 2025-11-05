## Ziel

Das bisherige Hologramm wird per Linienboxen angezeigt. Ziel ist ein neuer Renderer,
der wie in der Beispiel-Mod (`Litematica`) die echten Blockmodelle der Schematic
halbtransparent rendert und dabei Meshes cached, um Ruckler zu vermeiden.

## Komponenten

1. **PreviewChunkKey & PreviewChunk**  
   - Partitioniert die geladenen Blöcke in 16×16×16 Chunks.  
   - Enthält pro Render-Layer (`SOLID`, `CUTOUT`, `CUTOUT_MIPPED`, `TRANSLUCENT`) ein Mesh.  
   - Speichert einen Dirty-Status, damit Meshes nur bei Änderungen neu aufgebaut werden.

2. **PreviewMesh**  
   - Wrapper, der CPU-Puffer (BufferBuilder) aufbaut und anschließend in ein
     Runtime-spezifisches GPU-Objekt (Minecraft 1.21: `net.minecraft.client.renderer.RenderBuffers$BufferSource$PolgonBuffer` bzw. `VertexBuffer`-Äquivalent) hochlädt.  
   - Liefert `close()` zum Freigeben.

3. **PreviewChunkCache**  
   - Map von `PreviewChunkKey` → `PreviewChunk`.  
   - Kümmert sich um Lazy-Erstellung, Invalidierung und Rebuilds.  
   - Bietet Iteration über sichtbare Chunks (ggf. Frustum check).

4. **PreviewMeshBuilder**  
   - Baut zu einem `PreviewChunk` die Meshes, indem er über die enthaltenen Blöcke
     iteriert und das Vanilla `BlockRenderDispatcher` + `ModelData` nutzt.  
   - Unterstützt Layer-Sortierung sowie BlockEntity-Rendering-Lücken (erst später).

5. **SchematicPreviewRenderer (Neu)**  
   - Nutzt den Cache statt jeden Block pro Frame zu zeichnen.  
   - Setzt Alpha durch Vertex-Tinting (wie aktuelle Zwischenlösung)  
     oder optional per Shader, sobald wir dort ansetzen.

## Ablauf

1. On `Preview start`:  
   - Bestehende Blockliste aus `SchematicPreviewController` chunkweise einsortieren.

2. Beim Rendern:  
   - Kamera-Position bestimmen.  
   - Für alle sichtbaren `PreviewChunk` sicherstellen, dass ein gültiges Mesh existiert (Lazy rebuild).  
   - Mesh-layers in definierter Reihenfolge rendern (SOLID → CUTOUT → TRANSLUCENT).  
   - Für MISSED / CONFLICT Blocks später farbliche Hervorhebung via Shader/Tint.

3. Bei Änderungen (PreviewClear, BlockUpdate etc.):  
   - Chunks auf dirty setzen und Mesh verwerfen.

## Open Points

- **Alpha/Shader**: Zunächst Vertex-Tinting wie in aktueller Zwischenlösung, optional später Shader.  
- **Frustum Culling**: Anfangs reicht ein einfacher AABB-Check pro Chunk.  
- **Threading**: Erst Single-Thread Mesh-Aufbau; spätere Parallelisierung möglich.

