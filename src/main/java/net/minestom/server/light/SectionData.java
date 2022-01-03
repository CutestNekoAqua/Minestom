package net.minestom.server.light;

import net.minestom.server.instance.Section;

public record SectionData(Section segment, byte[] blockLight, byte[] skyLight) {}
