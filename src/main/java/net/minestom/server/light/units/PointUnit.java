package net.minestom.server.light.units;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.light.LightGenerationUnit;
import org.jetbrains.annotations.NotNull;

public class PointUnit implements LightGenerationUnit {

    private final Point point;

    public PointUnit(Point point) {
        this.point = point;
    }

    @Override
    public @NotNull Point size() {
        return new Pos(1,1,1);
    }

    @Override
    public @NotNull Point absoluteStart() {
        return point;
    }

    @Override
    public @NotNull Point absoluteEnd() {
        return point;
    }
}
