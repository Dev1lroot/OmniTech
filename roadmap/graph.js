// Builds the item/fluid/recipe node graph from the mod's own data files, plus
// best-effort vanilla recipes for any vanilla item/fluid the mod graph touches.
//
// Node kinds:
//   item     — an item or block-item (blocks are represented by their item form,
//              since that's how every recipe schema actually references them)
//   fluid    — a registered fluid
//   recipe   — a process node: N inputs -> N outputs, never touched directly by
//              the player, just the thing that connects items/fluids together
//
// Edge roles: "input" | "output" | "catalyst" (consumed as durability, not
// stoichiometrically) | "byproduct" (an output with chance < 1)

import fs from 'node:fs';
import path from 'node:path';

export function buildGraph({ projectPath, modid, vanillaSourcesPath }) {
    const RES = path.join(projectPath, 'src/main/resources');
    const DATA = path.join(RES, `data/${modid}`);
    const ASSETS = path.join(RES, `assets/${modid}`);
    const VANILLA_DATA = path.join(vanillaSourcesPath, 'data/minecraft');
    const VANILLA_ASSETS = path.join(vanillaSourcesPath, 'assets/minecraft');

    const nodes = new Map(); // refKey -> node
    const edges = [];
    let recipeCounter = 0;

    // ── Filesystem helpers ────────────────────────────────────────────────────

    function readJson(p) {
        try {
            return JSON.parse(fs.readFileSync(p, 'utf-8'));
        } catch {
            return null;
        }
    }

    function walk(dir, out = []) {
        if (!fs.existsSync(dir)) return out;
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
            const full = path.join(dir, entry.name);
            if (entry.isDirectory()) walk(full, out);
            else if (entry.name.endsWith('.json')) out.push(full);
        }
        return out;
    }

    function prettify(name) {
        return name
            .split('_')
            .filter(Boolean)
            .map((w) => w[0].toUpperCase() + w.slice(1))
            .join(' ');
    }

    // ── Node registry ─────────────────────────────────────────────────────────

    /** Splits "omnitech:tungsten_ingot" -> {namespace, name}; bare "tungsten_ingot" defaults to modid. */
    function splitRef(ref) {
        if (ref.includes(':')) {
            const [namespace, name] = ref.split(':');
            return { namespace, name };
        }
        return { namespace: modid, name: ref };
    }

    function iconFor(kind, namespace, name) {
        const isMod = namespace === modid;
        const itemTex = isMod
            ? path.join(ASSETS, 'textures/item', `${name}.png`)
            : path.join(VANILLA_ASSETS, 'textures/item', `${name}.png`);
        const blockTex = isMod
            ? path.join(ASSETS, 'textures/block', `${name}.png`)
            : path.join(VANILLA_ASSETS, 'textures/block', `${name}.png`);
        const fluidTex = isMod
            ? path.join(ASSETS, 'textures/block/fluid', `${name}_still.png`)
            : null; // vanilla fluids (water/lava) are handled specially below

        if (kind === 'fluid') {
            if (fluidTex && fs.existsSync(fluidTex)) {
                return `/textures/${isMod ? 'mod' : 'vanilla'}/fluid/${name}_still.png`;
            }
            if (name === 'water' || name === 'lava') {
                return `/textures/vanilla/block/${name}_still.png`;
            }
            return null;
        }
        if (fs.existsSync(itemTex)) return `/textures/${isMod ? 'mod' : 'vanilla'}/item/${name}.png`;
        if (fs.existsSync(blockTex)) return `/textures/${isMod ? 'mod' : 'vanilla'}/block/${name}.png`;
        return null;
    }

    function ensureNode(kind, ref) {
        const { namespace, name } = splitRef(ref);
        const key = `${kind}:${namespace}:${name}`;
        if (!nodes.has(key)) {
            nodes.set(key, {
                id: key,
                kind,
                namespace,
                name,
                label: prettify(name),
                mod: namespace === modid,
                icon: iconFor(kind, namespace, name),
            });
        }
        return key;
    }

    function addRecipeNode(recipeType, label, sourceFile) {
        recipeCounter++;
        const key = `recipe:${recipeType}:${recipeCounter}`;
        nodes.set(key, {
            id: key,
            kind: 'recipe',
            recipeType,
            label: label || recipeType,
            sourceFile,
        });
        return key;
    }

    function link(source, target, role, extra = {}) {
        edges.push({ source, target, role, ...extra });
    }

    /** Wires a recipe node to N item/fluid inputs and M outputs in one call. */
    function wireRecipe(recipeKey, inputs, outputs) {
        for (const inp of inputs) {
            if (!inp) continue;
            link(ensureNode(inp.kind, inp.ref), recipeKey, inp.role || 'input', {
                amount: inp.amount,
            });
        }
        for (const out of outputs) {
            if (!out) continue;
            const role = out.chance !== undefined && out.chance < 1 ? 'byproduct' : 'output';
            link(recipeKey, ensureNode(out.kind, out.ref), role, {
                amount: out.amount,
                count: out.count,
                chance: out.chance,
            });
        }
    }

    // ── 1. Register every declared item / fluid up front (so isolated,
    //       recipe-less items still show up in the graph) ─────────────────────

    for (const file of walk(path.join(DATA, 'item'))) {
        ensureNode('item', `${modid}:${path.basename(file, '.json')}`);
    }
    for (const file of walk(path.join(DATA, 'block'))) {
        ensureNode('item', `${modid}:${path.basename(file, '.json')}`);
    }
    for (const file of walk(path.join(DATA, 'fluid'))) {
        ensureNode('fluid', `${modid}:${path.basename(file, '.json')}`);
    }

    // ── 2. Vanilla-style crafting recipes: data/<modid>/recipe/**/*.json ──────
    //      (mote<->dust, ingot<->block, tools, armor, and any hand-authored ones)

    for (const file of walk(path.join(DATA, 'recipe'))) {
        const j = readJson(file);
        if (!j || !j.type) continue;
        const type = j.type;

        if (type === 'minecraft:crafting_shaped' || type === 'minecraft:crafting_shapeless') {
            const ingredientRefs = new Set();
            if (j.key) {
                for (const v of Object.values(j.key)) collectIngredientRefs(v, ingredientRefs);
            }
            if (Array.isArray(j.ingredients)) {
                for (const v of j.ingredients) collectIngredientRefs(v, ingredientRefs);
            }
            const resultRef = j.result?.id;
            if (!resultRef) continue;

            const recipeKey = addRecipeNode('crafting', prettify(path.basename(file, '.json')), relFile(file));
            wireRecipe(
                recipeKey,
                [...ingredientRefs].map((ref) => ({ kind: 'item', ref })),
                [{ kind: 'item', ref: resultRef, count: j.result.count }]
            );
        } else if (type === 'minecraft:smelting' || type === 'minecraft:blasting'
                || type === 'minecraft:smoking' || type === 'minecraft:campfire_cooking') {
            const ing = j.ingredient;
            const refs = new Set();
            collectIngredientRefs(ing, refs);
            const resultRef = j.result?.id ?? j.result;
            if (!resultRef) continue;
            const recipeKey = addRecipeNode(type.replace('minecraft:', ''), prettify(path.basename(file, '.json')), relFile(file));
            wireRecipe(
                recipeKey,
                [...refs].map((ref) => ({ kind: 'item', ref })),
                [{ kind: 'item', ref: typeof resultRef === 'string' ? resultRef : resultRef.id }]
            );
        }
    }

    // ── 3. Assembler recipes: data/<modid>/assembler/*.json ──────────────────

    for (const file of walk(path.join(DATA, 'assembler'))) {
        const j = readJson(file);
        if (!j) continue;
        const recipeKey = addRecipeNode('assembler', j.blueprint || path.basename(file, '.json'), relFile(file));
        const inputs = (j.components || []).map((c) => ({ kind: 'item', ref: c.item, amount: c.count }));
        const outputs = j.output ? [{ kind: 'item', ref: j.output.item, amount: j.output.count }] : [];
        wireRecipe(recipeKey, inputs, outputs);
    }

    // ── 4. Machine recipes: data/<modid>/machine_recipe/<type>/*.json ────────

    const MACHINE_PARSERS = {
        alloy_furnace(j) {
            return {
                inputs: (j.ingredients || []).map((i) => ({ kind: 'item', ref: i })),
                outputs: (j.output || []).map((o) => ({ kind: 'item', ref: o })),
            };
        },
        manual_macerator(j) {
            return {
                inputs: [{ kind: 'item', ref: j.input }],
                outputs: (j.output || []).map((o) => ({ kind: 'item', ref: o.item, amount: o.count, chance: o.chance })),
            };
        },
        manual_centrifuge(j) {
            return {
                inputs: [{ kind: 'item', ref: j.input }],
                outputs: (j.output || []).map((o) => ({ kind: 'item', ref: o.item, amount: o.count, chance: o.chance })),
            };
        },
        smelting(j) {
            return {
                inputs: (j.input || []).map((i) => ({ kind: 'item', ref: i })),
                outputs: j.output ? [{ kind: 'fluid', ref: j.output.fluid, amount: j.output.amount }] : [],
            };
        },
        foundry(j) {
            const inputs = [];
            if (j.input) inputs.push({ kind: 'fluid', ref: j.input.fluid, amount: j.input.amount });
            if (j.template) inputs.push({ kind: 'item', ref: j.template, role: 'catalyst' });
            return {
                inputs,
                outputs: j.output ? [{ kind: 'item', ref: j.output }] : [],
            };
        },
        chemical_reactor(j) {
            const inputs = (j.inputs || []).map((i) => ({ kind: 'fluid', ref: i.fluid, amount: i.amount }));
            if (j.catalyst?.item) inputs.push({ kind: 'item', ref: j.catalyst.item, role: 'catalyst' });
            return {
                inputs,
                outputs: j.output ? [{ kind: 'fluid', ref: j.output.fluid, amount: j.output.amount }] : [],
            };
        },
        electrolysis(j) {
            const inputs = [];
            if (j.inputFluid) inputs.push({ kind: 'fluid', ref: j.inputFluid.fluid, amount: j.inputFluid.amount });
            if (j.anode?.item) inputs.push({ kind: 'item', ref: j.anode.item, role: 'catalyst' });
            if (j.cathode?.item) inputs.push({ kind: 'item', ref: j.cathode.item, role: 'catalyst' });
            const outputs = [];
            if (j.outputAnode) outputs.push({ kind: 'fluid', ref: j.outputAnode.fluid, amount: j.outputAnode.amount });
            if (j.outputCathode) outputs.push({ kind: 'fluid', ref: j.outputCathode.fluid, amount: j.outputCathode.amount });
            if (j.outputSolution) outputs.push({ kind: 'fluid', ref: j.outputSolution.fluid, amount: j.outputSolution.amount });
            return { inputs, outputs };
        },
        fractional_distillation(j) {
            return {
                inputs: j.input ? [{ kind: 'fluid', ref: j.input.fluid, amount: j.input.amount }] : [],
                outputs: (j.outputs || []).map((o) => ({ kind: 'fluid', ref: o.fluid, amount: o.amount })),
            };
        },
        solvation(j) {
            const inputs = [];
            if (j.inputFluid) inputs.push({ kind: 'fluid', ref: j.inputFluid.fluid, amount: j.inputFluid.amount });
            if (j.inputItem) inputs.push({ kind: 'item', ref: j.inputItem.item, amount: j.inputItem.amount });
            return {
                inputs,
                outputs: j.outputFluid ? [{ kind: 'fluid', ref: j.outputFluid.fluid, amount: j.outputFluid.amount }] : [],
            };
        },
        chemical_infuser(j) {
            const inputs = [];
            if (j.inputFluid) inputs.push({ kind: 'fluid', ref: j.inputFluid.fluid, amount: j.inputFluid.amount });
            if (j.inputItem) inputs.push({ kind: 'item', ref: j.inputItem.item, amount: j.inputItem.amount });
            return {
                inputs,
                outputs: j.outputItem ? [{ kind: 'item', ref: j.outputItem.item, amount: j.outputItem.amount }] : [],
            };
        },
        coking(j) {
            return {
                inputs: j.input_item ? [{ kind: 'item', ref: j.input_item }] : [],
                outputs: [
                    j.output_item ? { kind: 'item', ref: j.output_item, amount: j.output_count } : null,
                    j.creosote_amount ? { kind: 'fluid', ref: `${modid}:creosote`, amount: j.creosote_amount } : null,
                ],
            };
        },
        extractor(j) {
            return {
                inputs: j.inputItem ? [{ kind: 'item', ref: j.inputItem.item, amount: j.inputItem.amount }] : [],
                outputs: [
                    j.outputFluid ? { kind: 'fluid', ref: j.outputFluid.fluid, amount: j.outputFluid.amount } : null,
                    j.residueItem ? { kind: 'item', ref: j.residueItem.item, amount: j.residueItem.amount, chance: 0.99 } : null,
                ],
            };
        },
        fluid_collector(j) {
            return {
                inputs: j.inputBlock ? [{ kind: 'item', ref: j.inputBlock }] : [],
                outputs: j.outputFluid ? [{ kind: 'fluid', ref: j.outputFluid.fluid, amount: j.outputFluid.amount }] : [],
            };
        },
    };

    const machineRecipeRoot = path.join(DATA, 'machine_recipe');
    if (fs.existsSync(machineRecipeRoot)) {
        for (const typeDir of fs.readdirSync(machineRecipeRoot, { withFileTypes: true })) {
            if (!typeDir.isDirectory()) continue;
            const recipeType = typeDir.name;
            const parser = MACHINE_PARSERS[recipeType];
            for (const file of walk(path.join(machineRecipeRoot, typeDir.name))) {
                const j = readJson(file);
                if (!j) continue;
                const label = prettify(path.basename(file, '.json'));
                const recipeKey = addRecipeNode(recipeType, label, relFile(file));
                if (parser) {
                    const { inputs, outputs } = parser(j);
                    wireRecipe(recipeKey, inputs, outputs);
                } else {
                    // Unknown machine type — still show the node, just unconnected,
                    // so new machine types are visible instead of silently dropped.
                }
            }
        }
    }

    // ── 5. Best-effort vanilla recipes for referenced vanilla items ──────────
    //      Vanilla recipe files are conventionally named after their result.

    const VANILLA_PARSERS = {
        'minecraft:crafting_shaped': (j) => ({ pattern: true }),
    };

    function tryVanillaRecipe(name) {
        const file = path.join(VANILLA_DATA, 'recipe', `${name}.json`);
        const j = readJson(file);
        if (!j || !j.type) return;
        const type = j.type;

        if (type === 'minecraft:crafting_shaped' || type === 'minecraft:crafting_shapeless') {
            const ingredientRefs = new Set();
            if (j.key) for (const v of Object.values(j.key)) collectIngredientRefs(v, ingredientRefs);
            if (Array.isArray(j.ingredients)) for (const v of j.ingredients) collectIngredientRefs(v, ingredientRefs);
            const resultRef = j.result?.id;
            if (!resultRef || ingredientRefs.size === 0) return;
            const recipeKey = addRecipeNode('vanilla_crafting', prettify(name), `vanilla:recipe/${name}.json`);
            wireRecipe(
                recipeKey,
                [...ingredientRefs].map((ref) => ({ kind: 'item', ref })),
                [{ kind: 'item', ref: resultRef, count: j.result.count }]
            );
        } else if (['minecraft:smelting', 'minecraft:blasting', 'minecraft:smoking', 'minecraft:campfire_cooking'].includes(type)) {
            const refs = new Set();
            collectIngredientRefs(j.ingredient, refs);
            const resultRef = typeof j.result === 'string' ? j.result : j.result?.id;
            if (!resultRef || refs.size === 0) return;
            const recipeKey = addRecipeNode(type.replace('minecraft:', 'vanilla_'), prettify(name), `vanilla:recipe/${name}.json`);
            wireRecipe(
                recipeKey,
                [...refs].map((ref) => ({ kind: 'item', ref })),
                [{ kind: 'item', ref: resultRef }]
            );
        }
    }

    // Snapshot the referenced-vanilla-item set before expansion so we don't
    // chase an unbounded chain — one hop of vanilla context per mod item.
    const vanillaTargets = [...nodes.values()]
        .filter((n) => n.kind === 'item' && !n.mod)
        .map((n) => n.name);
    for (const name of vanillaTargets) tryVanillaRecipe(name);

    // ── Ingredient-ref helpers ────────────────────────────────────────────────

    function collectIngredientRefs(value, into) {
        if (!value) return;
        if (typeof value === 'string') {
            into.add(value);
        } else if (Array.isArray(value)) {
            for (const v of value) collectIngredientRefs(v, into);
        } else if (typeof value === 'object') {
            if (value.item) into.add(value.item);
            // Tags (value.tag) are intentionally skipped — a tag isn't a single
            // node, and resolving it to every matching item would explode the
            // graph for very common tags like #minecraft:planks.
        }
    }

    function relFile(file) {
        return path.relative(projectPath, file);
    }

    return {
        generatedAt: new Date().toISOString(),
        nodes: [...nodes.values()],
        edges,
        stats: {
            items: [...nodes.values()].filter((n) => n.kind === 'item').length,
            fluids: [...nodes.values()].filter((n) => n.kind === 'fluid').length,
            recipes: [...nodes.values()].filter((n) => n.kind === 'recipe').length,
            edges: edges.length,
        },
    };
}
