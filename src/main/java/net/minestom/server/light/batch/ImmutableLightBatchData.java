package net.minestom.server.light.batch;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.light.LightType;

public record ImmutableLightBatchData(LightType lightType,
                                      Point point, byte lightLevel,
                                      Block block) {}
