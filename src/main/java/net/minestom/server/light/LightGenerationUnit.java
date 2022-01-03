package net.minestom.server.light;

import net.minestom.server.coordinate.Point;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.util.List;

public interface LightGenerationUnit {

    @NotNull Point size();

    @NotNull Point absoluteStart();

    @NotNull Point absoluteEnd();

    interface Section extends LightGenerationUnit {
        net.minestom.server.instance.Chunk chunk();
        net.minestom.server.instance.Section section();
    }

    interface Chunk extends LightGenerationUnit {
        net.minestom.server.instance.Chunk chunk();
        @NotNull List<net.minestom.server.instance.Section> sections();

        default net.minestom.server.instance.Section section(int index) {
            return sections().get(index);
        }
    }
}