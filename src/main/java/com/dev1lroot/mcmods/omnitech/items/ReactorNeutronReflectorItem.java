/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCellType;

public class ReactorNeutronReflectorItem extends ReactorRodItem {

    public ReactorNeutronReflectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public ReactorCellType getCellType() { return ReactorCellType.OTHER; }
}
