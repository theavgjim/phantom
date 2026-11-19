package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.ColorSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.core.setting.Setting;
import dev.phantom.core.setting.StringListSetting;
import dev.phantom.integration.event.Render3DEvent;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.event.WorldChangeEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.Colors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Highlights user-chosen blocks. Scanning is incremental and budgeted on the game thread: a few
 * chunks per tick, skipping sections that cannot contain a target via the section palette.
 */
public final class BlockEsp extends ClientModule {
    private final StringListSetting blocks = add(new StringListSetting("Blocks", "Block ids, comma separated",
            List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris")));
    private final NumberSetting radius = add(new NumberSetting("Radius", "Chunk radius", 4, 1, 12, 1, " ch"));
    private final NumberSetting budget = add(new NumberSetting("Chunks/Tick", "Scan budget per tick (performance)", 2, 1, 8, 1));
    private final NumberSetting maxBlocks = add(new NumberSetting("Max Blocks", "Render cap", 1500, 100, 5000, 100));
    private final ColorSetting color = add(new ColorSetting("Color", "Box colour", 0xFF26C6DA));

    private final Map<Long, List<BlockPos>> found = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private Set<Block> targets = Set.of();
    private int total;

    public BlockEsp() {
        super("BlockESP", "Highlights specific blocks such as ores", Category.VISUALS);
        restricted();
        on(TickEvent.class, e -> tick());
        on(Render3DEvent.class, this::render);
        on(WorldChangeEvent.class, e -> {
            found.clear();
            queue.clear();
            total = 0;
        });
    }

    @Override
    protected void onEnable() {
        rebuildTargets();
        found.clear();
        queue.clear();
    }

    @Override
    protected void onDisable() {
        found.clear();
        queue.clear();
        total = 0;
    }

    @Override
    protected void onSettingChanged(Setting<?> s) {
        if (s == blocks) {
            rebuildTargets();
            found.clear();
            queue.clear();
        }
    }

    private void rebuildTargets() {
        Set<Block> set = new HashSet<>();
        for (String id : blocks.get()) {
            Identifier ident = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (ident != null && Registries.BLOCK.containsId(ident)) set.add(Registries.BLOCK.get(ident));
        }
        targets = Set.copyOf(set);
    }

    private void tick() {
        if (!inWorld() || targets.isEmpty()) return;
        ChunkPos center = mc.player.getChunkPos();
        int r = radius.getInt();
        // Drop chunks that are out of range.
        found.keySet().removeIf(k -> Math.abs(ChunkPos.getPackedX(k) - center.x) > r
                || Math.abs(ChunkPos.getPackedZ(k) - center.z) > r);
        if (queue.isEmpty()) {
            // Next sweep, nearest chunks first, so changes (mined ores) refresh continuously.
            for (int d = 0; d <= r; d++) {
                for (int dx = -d; dx <= d; dx++) {
                    for (int dz = -d; dz <= d; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == d) queue.add(ChunkPos.toLong(center.x + dx, center.z + dz));
                    }
                }
            }
        }
        for (int i = 0; i < budget.getInt() && !queue.isEmpty(); i++) scanChunk(queue.poll());
        int n = 0;
        for (List<BlockPos> l : found.values()) n += l.size();
        total = n;
    }

    private void scanChunk(long key) {
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
        if (chunk == null) {
            found.remove(key);
            return;
        }
        Set<Block> t = targets;
        List<BlockPos> hits = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = chunk.getPos().getStartX(), baseZ = chunk.getPos().getStartZ();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(state -> t.contains(state.getBlock()))) continue;
            int baseY = chunk.sectionIndexToCoord(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        if (t.contains(s.getBlock())) hits.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
        }
        if (hits.isEmpty()) found.remove(key);
        else found.put(key, hits);
    }

    private void render(Render3DEvent e) {
        int cap = maxBlocks.getInt(), n = 0;
        int c = color.argb(), fill = Colors.withAlpha(c, 40);
        for (List<BlockPos> list : found.values()) {
            for (BlockPos p : list) {
                if (n++ >= cap) return;
                Box b = new Box(p);
                e.renderer.filledBox(b, fill);
                e.renderer.box(b, c);
            }
        }
    }

    @Override
    public String getSuffix() {
        return Integer.toString(total);
    }
}
