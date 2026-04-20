package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Electric tunnel bore — mines a 3×3 area per strike, consuming EU per block.
 *
 * <p>Three tiers differ in mining level, speed, EU capacity, and cost per block.
 * Speed drops to 1.0 (hand speed) when the buffer is empty; the 3×3 bonus only
 * fires when there is enough charge for at least the primary block.
 */
public class BoreItem extends Item {

    public final int maxEu;
    public final int euPerBlock;

    public BoreItem(ToolMaterial material, int maxEu, int euPerBlock, Properties properties) {
        super(properties.stacksTo(1).pickaxe(material, 1.0f, -2.8f));
        this.maxEu = maxEu;
        this.euPerBlock = euPerBlock;
    }

    // ── EU access ─────────────────────────────────────────────────────────────

    public static int getEu(ItemStack stack) {
        return stack.getOrDefault(OmniTechDataComponents.EU_STORED.get(), 0);
    }

    public static void setEu(ItemStack stack, int eu) {
        if (eu <= 0) stack.remove(OmniTechDataComponents.EU_STORED.get());
        else stack.set(OmniTechDataComponents.EU_STORED.get(),
                stack.getItem() instanceof BoreItem b ? Math.min(eu, b.maxEu) : eu);
    }

    // ── Speed — falls to hand speed when discharged ───────────────────────────

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (getEu(stack) <= 0) return 1.0f;
        return super.getDestroySpeed(stack, state); // reads TOOL data component
    }

    // ── 3×3 mining ────────────────────────────────────────────────────────────

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state,
            BlockPos pos, LivingEntity entity) {
        // No durability damage — we use EU instead
        if (level.isClientSide() || !(entity instanceof Player player)) return true;
        if (player.isCreative()) return true;

        int eu = getEu(stack);
        if (eu < euPerBlock) return true; // no charge — primary mined without EU

        setEu(stack, eu - euPerBlock);

        Direction[] axes = planeAxes(player.getLookAngle());
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                int current = getEu(stack);
                if (current < euPerBlock) return true;

                BlockPos target = pos.relative(axes[0], a).relative(axes[1], b);
                BlockState tState = level.getBlockState(target);
                if (tState.isAir()) continue;
                if (!level.mayInteract(player, target)) continue;

                level.destroyBlock(target, true, player);
                setEu(stack, getEu(stack) - euPerBlock);
            }
        }
        return true;
    }

    private static Direction[] planeAxes(Vec3 look) {
        Direction facing = Direction.getNearest(
                (int) Math.round(look.x), (int) Math.round(look.y), (int) Math.round(look.z),
                Direction.NORTH);
        return switch (facing) {
            case NORTH, SOUTH -> new Direction[]{Direction.EAST, Direction.UP};
            case EAST, WEST   -> new Direction[]{Direction.NORTH, Direction.UP};
            case UP, DOWN     -> new Direction[]{Direction.EAST, Direction.NORTH};
        };
    }

    // ── EU bar ────────────────────────────────────────────────────────────────

    @Override public boolean isBarVisible(ItemStack stack) { return true; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return maxEu > 0 ? Math.round(13f * getEu(stack) / maxEu) : 0;
    }

    @Override public int getBarColor(ItemStack stack) { return 0x44AAFF; }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal(getEu(stack) + " / " + maxEu + " EU")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("3×3 Tunnel Bore  (" + euPerBlock + " EU/block)")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
