package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ColorSetting;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.Render3DEvent;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.event.WorldChangeEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.Colors;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Highlights containers. The block-entity scan runs once per second, not per frame. */
public final class StorageEsp extends ClientModule {
    private final ModeSetting mode = add(new ModeSetting("Mode", "How boxes are drawn", "Both", "Outline", "Filled", "Both"));
    private final NumberSetting range = add(new NumberSetting("Range", "Chunk radius to scan", 8, 1, 32, 1, " ch"));
    private final BooleanSetting chests = add(new BooleanSetting("Chests", "Chests and trapped chests", true));
    private final ColorSetting chestColor = add(new ColorSetting("Chest Color", "", 0xFFFFA726));
    private final BooleanSetting enderChests = add(new BooleanSetting("Ender Chests", "", true));
    private final ColorSetting enderColor = add(new ColorSetting("Ender Chest Color", "", 0xFFAB47BC));
    private final BooleanSetting barrels = add(new BooleanSetting("Barrels", "", true));
    private final ColorSetting barrelColor = add(new ColorSetting("Barrel Color", "", 0xFF8D6E63));
    private final BooleanSetting shulkers = add(new BooleanSetting("Shulkers", "", true));
    private final ColorSetting shulkerColor = add(new ColorSetting("Shulker Color", "", 0xFFEC407A));
    private final BooleanSetting furnaces = add(new BooleanSetting("Furnaces", "Furnaces, smokers, blast furnaces", false));
    private final ColorSetting furnaceColor = add(new ColorSetting("Furnace Color", "", 0xFF90A4AE));
    private final BooleanSetting hoppers = add(new BooleanSetting("Hoppers & Droppers", "", false));
    private final ColorSetting hopperColor = add(new ColorSetting("Hopper Color", "", 0xFF78909C));

    private record Target(Box box, int color) {
    }

    private List<Target> targets = List.of();
    private int timer;

    public StorageEsp() {
        super("StorageESP", "Highlights chests, barrels, shulkers and more", Category.VISUALS);
        restricted();
        on(TickEvent.class, e -> {
            if (--timer <= 0) {
                timer = 20;
                scan();
            }
        });
        on(Render3DEvent.class, this::render);
        on(WorldChangeEvent.class, e -> {
            targets = List.of();
            timer = 20;
        });
    }

    @Override
    protected void onEnable() {
        timer = 0;
    }

    @Override
    protected void onDisable() {
        targets = List.of();
    }

    @Override
    protected void onSettingChanged(dev.phantom.core.setting.Setting<?> s) {
        timer = 0;
    }

    private void scan() {
        if (!inWorld()) {
            targets = List.of();
            return;
        }
        List<Target> out = new ArrayList<>();
        int r = Math.min(range.getInt(), mc.options.getClampedViewDistance());
        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    int color = colorFor(be);
                    if (color != 0) out.add(new Target(boxFor(be), color));
                }
            }
        }
        targets = out;
    }

    private int colorFor(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return chests.isOn() ? chestColor.argb() : 0;
        if (be instanceof EnderChestBlockEntity) return enderChests.isOn() ? enderColor.argb() : 0;
        if (be instanceof BarrelBlockEntity) return barrels.isOn() ? barrelColor.argb() : 0;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.isOn() ? shulkerColor.argb() : 0;
        if (be instanceof AbstractFurnaceBlockEntity) return furnaces.isOn() ? furnaceColor.argb() : 0;
        if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity) return hoppers.isOn() ? hopperColor.argb() : 0;
        return 0;
    }

    private static Box boxFor(BlockEntity be) {
        BlockPos p = be.getPos();
        if (be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity) {
            double i = 1 / 16.0;
            return new Box(p.getX() + i, p.getY(), p.getZ() + i, p.getX() + 1 - i, p.getY() + 14 / 16.0, p.getZ() + 1 - i);
        }
        return new Box(p);
    }

    private void render(Render3DEvent e) {
        boolean outline = !mode.is("Filled"), fill = !mode.is("Outline");
        for (Target t : targets) {
            if (fill) e.renderer.filledBox(t.box, Colors.withAlpha(t.color, 45));
            if (outline) e.renderer.box(t.box, t.color);
        }
    }

    @Override
    public String getSuffix() {
        return Integer.toString(targets.size());
    }
}
