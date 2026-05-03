package com.dev1lroot.mcmods.omnitech.blocks.logic.display;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.DisplayMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class DisplayBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SIZE = 16;

    private int portId = 0;
    public final int[] pixels = new int[SIZE * SIZE]; // 0x00RRGGBB per pixel

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.DISPLAY.get(), pos, state);
    }

    public int getPortId() { return portId; }

    public void setPortId(int id) {
        portId = Math.clamp(id, 0, 65535);
        setChanged();
        sync();
    }

    /** Called by LogicMachine for the SET instruction. */
    public void setPixel(int x, int y, int color) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) return;
        int c = color & 0xFFFFFF;
        if (pixels[y * SIZE + x] == c) return;
        pixels[y * SIZE + x] = c;
        setChanged();
        sync();
    }

    /** Called by LogicMachine for the RST instruction. */
    public void resetPixels() {
        boolean changed = false;
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] != 0) { pixels[i] = 0; changed = true; }
        }
        if (changed) { setChanged(); sync(); }
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── MenuProvider ─────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.display");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId,
            Inventory inv, Player player) {
        return new DisplayMenu(containerId, inv, worldPosition, portId);
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("PortId", portId);
        out.putIntArray("Pixels", pixels);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        portId = in.getIntOr("PortId", 0);
        in.getIntArray("Pixels").ifPresent(arr ->
                System.arraycopy(arr, 0, pixels, 0, Math.min(arr.length, pixels.length)));
    }
}
