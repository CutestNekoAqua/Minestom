package net.minestom.server.light.units;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.light.LightGenerationUnit;
import org.jetbrains.annotations.NotNull;

public class SectionUnit implements LightGenerationUnit.Section {

    private final net.minestom.server.instance.Section section;
    private final net.minestom.server.instance.Chunk chunk;
    private final int sectionIndex;

    public SectionUnit(net.minestom.server.instance.Chunk chunk, int sectionIndex) {
        this.chunk = chunk;
        this.section = chunk.getSection(sectionIndex);
        this.sectionIndex = sectionIndex;
    }

    @Override
    public @NotNull Point size() {
        return new Pos(16,16,16);
    }

    @Override
    public @NotNull Point absoluteStart() {
        return new Pos(16 * chunk.getChunkX(), 16 * sectionIndex - 64, 16 * chunk.getChunkZ());
    }

    @Override
    public @NotNull Point absoluteEnd() {
        return new Pos(16 * chunk.getChunkX() + 15, 16 * sectionIndex - 64 + 15, 16 * chunk.getChunkZ() + 15);
    }

    @Override
    public net.minestom.server.instance.Chunk chunk() {
        return chunk;
    }

    @Override
    public net.minestom.server.instance.Section section() {
        return section;
    }
}
