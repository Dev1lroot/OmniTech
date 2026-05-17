package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCellType;

public class ReactorFuelRodItem extends ReactorRodItem {

    public ReactorFuelRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public ReactorCellType getCellType() { return ReactorCellType.FUEL; }
}
