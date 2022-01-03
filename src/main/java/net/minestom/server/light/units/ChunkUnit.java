package net.minestom.server.light.units;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Chunk;
import net.minestom.server.light.LightGenerationUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.util.List;

public class ChunkUnit implements LightGenerationUnit.Chunk {

    private final net.minestom.server.instance.Chunk chunk;

    public ChunkUnit(net.minestom.server.instance.Chunk chunk) {
        this.chunk = chunk;
    }

    @Override
    public @NotNull Point size() {
        return new Pos(16,256 + 64,16);
    }

    @Override
    public @NotNull Point absoluteStart() {
        return new Pos(16 * chunk.getChunkX(), -64, 16 * chunk.getChunkZ());
    }

    @Override
    public @NotNull Point absoluteEnd() {
        return new Pos(16 * chunk.getChunkX() + 15, 256, 16 * chunk.getChunkZ() + 15);
    }

    @Override
    public net.minestom.server.instance.Chunk chunk() {
        return chunk;
    }

    @Override
    public @NotNull List<net.minestom.server.instance.Section> sections() {
        return chunk().getSections();
    }
}
