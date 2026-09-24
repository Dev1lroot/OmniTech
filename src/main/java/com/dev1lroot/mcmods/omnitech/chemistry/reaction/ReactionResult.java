/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.List;

/** What {@link ReactionEngine#react} decides happens between two compounds. */
public sealed interface ReactionResult {

    enum PhysicalState { GAS, PRECIPITATE, AQUEOUS, LIQUID, SOLID }

    record Species(Formula formula, int coefficient, PhysicalState state) {
        public String label() { return coefficient == 1 ? formula.canonical() : coefficient + formula.canonical(); }
    }

    record Reaction(String reactionType, List<Species> reactants, List<Species> products, String description)
            implements ReactionResult {

        public String equation() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < reactants.size(); i++) {
                if (i > 0) sb.append(" + ");
                sb.append(reactants.get(i).label());
            }
            sb.append(" -> ");
            for (int i = 0; i < products.size(); i++) {
                if (i > 0) sb.append(" + ");
                sb.append(products.get(i).label());
                if (products.get(i).state() == PhysicalState.GAS) sb.append("(g)");
                if (products.get(i).state() == PhysicalState.PRECIPITATE) sb.append("(s)↓");
            }
            return sb.toString();
        }
    }

    record NoReaction(String reason) implements ReactionResult {}
}
