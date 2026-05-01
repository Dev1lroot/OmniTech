package com.dev1lroot.mcmods.omnitech.blocks.radio.radio_scanner;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioManager;
import com.dev1lroot.mcmods.omnitech.gui.RadioScannerMenu;
import com.dev1lroot.mcmods.omnitech.network.RadioScannerRowPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Block entity for {@link RadioScannerBlock}.
 *
 * <p>Every 5 server ticks it sends a row of signal data for the currently
 * active band to every player with this scanner's GUI open.
 *
 * <p>ContainerData slot 0 = active band ordinal (synced to client).
 * Players switch bands via {@code clickMenuButton(bandOrdinal)}.
 */
public class RadioScannerBlockEntity extends BaseContainerBlockEntity {

    private static final int SEND_INTERVAL = 5;

    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    private FrequencyBand activeBand  = FrequencyBand.VHF;
    private int           tickCounter = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return index == 0 ? activeBand.ordinal() : 0;
        }
        @Override public void set(int index, int value) {
            if (index == 0) {
                FrequencyBand[] vals = FrequencyBand.values();
                if (value >= 0 && value < vals.length) activeBand = vals[value];
            }
        }
        @Override public int getCount() { return 1; }
    };

    public RadioScannerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.RADIO_SCANNER.get(), pos, state);
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("container.omnitech.radio_scanner");
    }
    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new RadioScannerMenu(containerId, inv, this, dataAccess);
    }

    public void setActiveBand(int ordinal) {
        FrequencyBand[] vals = FrequencyBand.values();
        if (ordinal >= 0 && ordinal < vals.length) {
            activeBand = vals[ordinal];
            setChanged();
        }
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            RadioScannerBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        be.tickCounter++;
        if (be.tickCounter < SEND_INTERVAL) return;
        be.tickCounter = 0;

        float[] row = RadioManager.getRow(level.dimension(), be.activeBand);
        RadioScannerRowPacket packet = new RadioScannerRowPacket(be.activeBand.ordinal(), row);

        for (ServerPlayer player : sl.players()) {
            if (player.containerMenu instanceof RadioScannerMenu menu
                    && menu.getBlockPos().equals(pos)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }
}
