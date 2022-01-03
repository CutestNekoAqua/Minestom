package net.minestom.server.light.batch;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Section;
import net.minestom.server.light.LightType;
import net.minestom.server.light.LightUtils;
import net.minestom.server.light.SectionData;
import net.minestom.server.utils.chunk.ChunkUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class ChunkLightBatch {

    private final HashMap<Point, ImmutableLightBatchData> lightMap = new HashMap<>();
    private final Chunk chunk;
    private final List<List<byteForPos>> blockLevels = new ArrayList<>();
    private final List<List<byteForPos>> skyLevels = new ArrayList<>();

    public ChunkLightBatch(Chunk chunk) {
        this.chunk = chunk;
        for (int i = 0; i < 16; i++) {
            blockLevels.add(new ArrayList<>());
            skyLevels.add(new ArrayList<>());
        }
    }

    public void set(int x, int y, int z, LightType type, byte intensity) {
        set(new Pos(x, y, z), type, intensity);
    }

    public void set(Point point, LightType type, byte intensity) {
        int index = (int) ((point.y() - point.y() % 16) / 16);
        List<List<byteForPos>> sectionArray;
        if(type == LightType.SKY)
            sectionArray = skyLevels;
        else
            sectionArray = blockLevels;
        List<byteForPos> section = sectionArray.get(index);
        Optional<byteForPos> opt = sectionArray.get(index).stream().filter((e) -> e.pos.distance(point) == 0).findFirst();
        opt.ifPresent(section::remove);
        section.add(new byteForPos(point, intensity));
    }

    public ImmutableLightBatchData get(int x, int y, int z) {
        return get(new Pos(x, y, z));
    }

    public ImmutableLightBatchData get(Point point) {
        return get(point, null);
    }

    public ImmutableLightBatchData get(int x, int y, int z, LightType type) {
        return get(new Pos(x, y, z), type);
    }

    public ImmutableLightBatchData get(Point point, LightType type) {
        if(type == null) return new ImmutableLightBatchData(LightType.EMPTY, point, (byte) -1, chunk.getBlock(point));
        return new ImmutableLightBatchData(type, point, getLightLevel(point, type), chunk.getBlock(point));
    }

    public byte getLightLevel(Point point, LightType type) {
        List<List<byteForPos>> sectionArray;
        if(type == LightType.SKY)
            sectionArray = skyLevels;
        else
            sectionArray = blockLevels;
        int index = (int) ((point.y() - point.y() % 16) / 16);
        Optional<byteForPos> opt = sectionArray.get(index).stream().filter((e) -> e.pos.distance(point) == 0).findFirst();
        return opt.isPresent() ? opt.get().aByte : -1;
    }

    public SectionData[] computeSectionsData() {
        SectionData[] sectionDatas = new SectionData[16];
        for (int i = 0; i < 16; i++) {
            List<byteForPos> blockData = blockLevels.get(i);
            List<byteForPos> skyData = skyLevels.get(i);
            byte[] blockArray = new byte[LightUtils.ARRAY_SIZE];
            byte[] skyArray = new byte[LightUtils.ARRAY_SIZE];
            for (byteForPos e : blockData) {
                LightUtils.set((int) e.pos.x(), (int) e.pos.y(), (int) e.pos.z(), e.aByte, blockArray);
            }
            for (byteForPos e : skyData) {
                LightUtils.set((int) e.pos.x(), (int) e.pos.y(), (int) e.pos.z(), e.aByte, skyArray);
            }
            sectionDatas[i] = new SectionData(chunk.getSection(i), blockArray, skyArray);
        }
        return sectionDatas;
    }

    public void applyLightSettings() {
        for (int i = 0; i < 16; i++) {
            List<byteForPos> blockData = blockLevels.get(i);
            List<byteForPos> skyData = skyLevels.get(i);
            Section section = chunk.getSection(i);
            byte[] blockArray = section.getBlockLight();
            byte[] skyArray = section.getSkyLight();
            if(blockArray == null) blockArray = new byte[LightUtils.ARRAY_SIZE];
            if(skyArray == null) skyArray = new byte[LightUtils.ARRAY_SIZE];
            for (byteForPos e : blockData) {
                LightUtils.set((int) e.pos.x(), (int) e.pos.y(), (int) e.pos.z(), e.aByte, blockArray);
            }
            for (byteForPos e : skyData) {
                LightUtils.set((int) e.pos.x(), (int) e.pos.y(), (int) e.pos.z(), e.aByte, skyArray);
            }
            section.setBlockLight(blockArray);
            section.setSkyLight(skyArray);
            LightUtils.updateLightCache(section);
        }
    }

    private record byteForPos(Point pos, byte aByte) {
    }

}
