/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.BoreMenu;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/**
 * Modular electric tunnel bore — mines a 3×3 area per strike.
 *
 * <p>Right-click opens the bore to swap its two parts, stored in
 * {@link OmniTechDataComponents#BORE_CONTENTS}:
 * <ul>
 *   <li>slot {@link #SLOT_HEAD} — a {@link BoreHeadItem}: harvest tier, speed, kJ per block;
 *       loses one durability per block mined</li>
 *   <li>slot {@link #SLOT_BATTERY} — a {@link BatteryItem}: capacity and the charge itself</li>
 * </ul>
 * Without a head, or with a flat battery, it digs at hand speed. Sneaking mines a single block.
 */
public class BoreItem extends Item implements ChargeableItem {

    public static final int SLOT_HEAD = 0;
    public static final int SLOT_BATTERY = 1;

    /** Set while the extra blocks of a 3×3 strike are broken, so they don't fan out again. */
    private static final ThreadLocal<Boolean> BORING = ThreadLocal.withInitial(() -> false);

    public BoreItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ── Parts ─────────────────────────────────────────────────────────────────

    /** Copies of {head, battery}; either may be {@link ItemStack#EMPTY}. */
    public static List<ItemStack> getParts(ItemStack bore) {
        ItemContainerContents c = bore.getOrDefault(OmniTechDataComponents.BORE_CONTENTS.get(),
                ItemContainerContents.EMPTY);
        return List.of(slot(c, SLOT_HEAD), slot(c, SLOT_BATTERY));
    }

    private static ItemStack slot(ItemContainerContents c, int slot) {
        return slot < c.getSlots() ? c.getStackInSlot(slot).copy() : ItemStack.EMPTY;
    }

    public static ItemStack getHead(ItemStack bore)    { return getParts(bore).get(SLOT_HEAD); }
    public static ItemStack getBattery(ItemStack bore) { return getParts(bore).get(SLOT_BATTERY); }

    public static void setParts(ItemStack bore, ItemStack head, ItemStack battery) {
        if (head.isEmpty() && battery.isEmpty()) bore.remove(OmniTechDataComponents.BORE_CONTENTS.get());
        else bore.set(OmniTechDataComponents.BORE_CONTENTS.get(),
                ItemContainerContents.fromItems(List.of(head, battery)));
    }

    private static BoreHeadItem headItem(ItemStack head) {
        return head.getItem() instanceof BoreHeadItem h ? h : null;
    }

    // ── ChargeableItem — delegates to the plugged-in battery ─────────────────

    @Override
    public int getEnergy(ItemStack stack) {
        ItemStack bat = getBattery(stack);
        return bat.getItem() instanceof BatteryItem b ? b.getEnergy(bat) : 0;
    }

    @Override
    public int getCapacity(ItemStack stack) {
        ItemStack bat = getBattery(stack);
        return bat.getItem() instanceof BatteryItem b ? b.getCapacity(bat) : 0;
    }

    @Override
    public void setEnergy(ItemStack stack, int kj) {
        List<ItemStack> parts = getParts(stack);
        ItemStack bat = parts.get(SLOT_BATTERY);
        if (!(bat.getItem() instanceof BatteryItem b)) return;
        b.setEnergy(bat, kj);
        setParts(stack, parts.get(SLOT_HEAD), bat);
    }

    /** True if the bore has a head and enough charge to cut one more block. */
    public boolean canCut(ItemStack stack) {
        BoreHeadItem head = headItem(getHead(stack));
        return head != null && getEnergy(stack) >= head.energyPerBlock;
    }

    // ── Mining behaviour comes from the head ──────────────────────────────────

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!canCut(stack)) return 1.0f;
        return headItem(getHead(stack)).miningSpeed(state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        BoreHeadItem head = headItem(getHead(stack));
        return head != null && head.isCorrectForDrops(state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state,
            BlockPos pos, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) return true;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness == 0f) return true;                  // torches, grass… cost nothing
        if (!cut(stack, serverLevel, player)) return true;
        if (BORING.get() || player.isShiftKeyDown()) return true;

        // Mine the rest of the 3×3 through the normal break path, so protection, drops,
        // XP and events all behave as if the player broke each block (and pay via cut()).
        BORING.set(true);
        try {
            Direction[] axes = planeAxes(player.getLookAngle());
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    ItemStack held = player.getMainHandItem();
                    if (held != stack || !canCut(stack)) return true;

                    BlockPos target = pos.relative(axes[0], a).relative(axes[1], b);
                    BlockState t = level.getBlockState(target);
                    if (t.isAir()) continue;
                    float th = t.getDestroySpeed(level, target);
                    // unbreakable, not this head's job, or much harder than the block aimed at
                    if (th < 0f || !isCorrectToolForDrops(stack, t) || th > hardness * 2f + 1f) continue;

                    level.levelEvent(null, LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, target, Block.getId(t));
                    player.gameMode.destroyBlock(target);
                }
            }
        } finally {
            BORING.set(false);
        }
        return true;
    }

    /** Pays for one block: energy from the battery, one durability from the head. */
    private boolean cut(ItemStack stack, ServerLevel level, ServerPlayer player) {
        List<ItemStack> parts = getParts(stack);
        ItemStack head = parts.get(SLOT_HEAD);
        ItemStack bat = parts.get(SLOT_BATTERY);
        BoreHeadItem h = headItem(head);
        if (h == null || !(bat.getItem() instanceof BatteryItem b)) return false;
        int eu = b.getEnergy(bat);
        if (eu < h.energyPerBlock) return false;

        b.setEnergy(bat, eu - h.energyPerBlock);
        head.hurtAndBreak(1, level, player, broken ->
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f,
                        0.8f + level.getRandom().nextFloat() * 0.4f));
        setParts(stack, head, bat);
        return true;
    }

    /**
     * Mending for heads inside held bores (vanilla only mends items in equipment slots).
     * Called from {@code ExperienceOrbBoreMendingMixin}; mirrors
     * {@code ExperienceOrb.repairPlayerItems}.
     *
     * @return XP left over after repairing
     */
    public static int repairHeldHeadsWithXp(ServerPlayer player, int amount) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (amount <= 0) return 0;
            ItemStack bore = player.getItemInHand(hand);
            if (!(bore.getItem() instanceof BoreItem)) continue;
            List<ItemStack> parts = getParts(bore);
            ItemStack head = parts.get(SLOT_HEAD);
            if (!head.isDamaged()
                    || !EnchantmentHelper.has(head, EnchantmentEffectComponents.REPAIR_WITH_XP)) continue;

            int canRepair = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.level(), head,
                    (int) (amount * head.getXpRepairRatio()));
            int repair = Math.min(canRepair, head.getDamageValue());
            if (repair <= 0) continue;
            head.setDamageValue(head.getDamageValue() - repair);
            setParts(bore, head, parts.get(SLOT_BATTERY));
            amount -= repair * amount / canRepair;
        }
        return amount;
    }

    private static Direction[] planeAxes(Vec3 look) {
        Direction facing = Direction.getApproximateNearest(look);
        return switch (facing.getAxis()) {
            case Z -> new Direction[]{Direction.EAST, Direction.UP};
            case X -> new Direction[]{Direction.NORTH, Direction.UP};
            case Y -> new Direction[]{Direction.EAST, Direction.NORTH};
        };
    }

    // ── Animation: the spinning head replaces the arm swing ───────────────────

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }

    /** Charge and head wear change the stack every block — don't dip the tool for that. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !newStack.is(this);
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !newStack.is(this);
    }

    // ── Right-click: open the parts screen ────────────────────────────────────

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new BoreMenu(id, inv, hand),
                            Component.translatable("container.omnitech.bore")),
                    buf -> buf.writeEnum(hand));
        }
        return InteractionResult.SUCCESS;
    }

    // ── Charge bar ────────────────────────────────────────────────────────────

    @Override
    public boolean isBarVisible(ItemStack stack) { return getCapacity(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = getCapacity(stack);
        return max > 0 ? Math.round(13f * getEnergy(stack) / max) : 0;
    }

    @Override public int getBarColor(ItemStack stack) { return 0x44AAFF; }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        List<ItemStack> parts = getParts(stack);
        ItemStack head = parts.get(SLOT_HEAD);
        ItemStack bat = parts.get(SLOT_BATTERY);

        if (headItem(head) instanceof BoreHeadItem h) {
            tooltip.accept(head.getHoverName().copy().append(
                            "  " + (head.getMaxDamage() - head.getDamageValue()) + "/" + head.getMaxDamage())
                    .withStyle(ChatFormatting.GOLD));
            h.appendStats(tooltip);
        } else {
            tooltip.accept(Component.translatable("tooltip.omnitech.bore.no_head")
                    .withStyle(ChatFormatting.RED));
        }

        if (bat.getItem() instanceof BatteryItem b) {
            int eu = b.getEnergy(bat);
            tooltip.accept(bat.getHoverName().copy().append("  " + ElectricUnits.formatEnergy(eu)
                            + " / " + ElectricUnits.formatEnergy(b.capacityKj)
                            + " (" + Math.round(eu * 100f / b.capacityKj) + "%)")
                    .withStyle(ChatFormatting.AQUA));
            b.appendPackInfo(tooltip);
        } else {
            tooltip.accept(Component.translatable("tooltip.omnitech.bore.no_battery")
                    .withStyle(ChatFormatting.RED));
        }

        tooltip.accept(Component.translatable("tooltip.omnitech.bore.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
