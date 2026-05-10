package com.dev1lroot.mcmods.omnitech.blocks.logic.research_table;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.ResearchLoader;
import com.dev1lroot.mcmods.omnitech.items.BlueprintItem;
import com.dev1lroot.mcmods.omnitech.gui.ResearchTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class ResearchTableBlockEntity extends BaseContainerBlockEntity {

    /** Slots 0-8: inputs, Slot 9: output. */
    private NonNullList<ItemStack> items = NonNullList.withSize(10, ItemStack.EMPTY);

    private int progress         = 0;  // 0-100
    private int winsCount        = 0;
    @Nullable private String matchedResearchId = null;
    int tickCounter              = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) { return i == 0 ? progress : 0; }
        @Override public void set(int i, int v) { if (i == 0) progress = v; }
        @Override public int getCount() { return 1; }
    };

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.RESEARCH_TABLE.get(), pos, state);
    }

    // ── Server tick ──────────────────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchTableBlockEntity be) {
        if (level.isClientSide()) return;
        if (++be.tickCounter < 20) return;
        be.tickCounter = 0;
        be.checkResearchChange();
    }

    private void checkResearchChange() {
        Optional<ResearchLoader.ResearchDefinition> res =
                ResearchLoader.findMatch(items.subList(0, 9));
        String newId = res.map(ResearchLoader.ResearchDefinition::id).orElse(null);
        if (!Objects.equals(newId, matchedResearchId)) {
            matchedResearchId = newId;
            winsCount = 0;
            progress  = 0;
            setChanged();
        }
    }

    // ── Research progress ────────────────────────────────────────────────────

    /** Called by {@link com.dev1lroot.mcmods.omnitech.network.MinesweeperResultPacket} on win. */
    public void onMinesweeperWin() {
        if (matchedResearchId == null) {
            checkResearchChange(); // update immediately if stale
            if (matchedResearchId == null) return;
        }
        Optional<ResearchLoader.ResearchDefinition> resOpt = ResearchLoader.find(matchedResearchId);
        if (resOpt.isEmpty()) return;
        ResearchLoader.ResearchDefinition res = resOpt.get();

        winsCount++;
        progress = Math.min(100, (winsCount * 100) / res.winsRequired());

        if (progress >= 100) {
            completeResearch(res);
        }
        setChanged();
    }

    /** Called by {@link com.dev1lroot.mcmods.omnitech.network.MinesweeperResultPacket} on loss. */
    public void onMinesweeperLose() {
        if (matchedResearchId == null) return;
        progress  = 0;
        winsCount = 0;
        setChanged();
    }

    private void completeResearch(ResearchLoader.ResearchDefinition res) {
        // Consume non-blueprint inputs
        ResearchLoader.consumeNonBlueprints(items, res);
        // Place blueprint in output slot
        items.set(9, BlueprintItem.ofResearch(res.id()));
        progress  = 0;
        winsCount = 0;
        matchedResearchId = null;
        setChanged();
    }

    public int getProgress() { return progress; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── BaseContainerBlockEntity ─────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.research_table");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ResearchTableMenu(containerId, inv, this);
    }

    @Override
    public int getContainerSize() { return 10; }

    @Override
    public boolean stillValid(Player player) { return true; }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("Progress",  progress);
        out.putInt("WinsCount", winsCount);
        if (matchedResearchId != null) out.putString("Research", matchedResearchId);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(10, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        progress  = in.getIntOr("Progress",  0);
        winsCount = in.getIntOr("WinsCount", 0);
        matchedResearchId = in.getString("Research").orElse(null);
    }
}
