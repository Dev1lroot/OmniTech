/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.jei;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.recipes.AlloyFurnaceRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.AlloyFurnaceRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.BoilingRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.BoilingRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ChemicalReactorRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ChemicalReactorRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ElectrolysisRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ElectrolysisRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.FermentationRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FermentationRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.FilterPressRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FilterPressRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.FluidCollectorRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FluidCollectorRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.FoundryRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FoundryRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.GlassBlowingRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.GlassBlowingRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.FractionalDistillationRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FractionalDistillationRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ManualCentrifugeRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ManualCentrifugeRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ManualMaceratorRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ManualMaceratorRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.PressRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.PressRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.SmelterRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.SolidFormManager;
import com.dev1lroot.mcmods.omnitech.recipes.SmelterRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.SolvationRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.SolvationRecipeManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

@JeiPlugin
public class OmniTechJeiPlugin implements IModPlugin {

    // ── Recipe types ──────────────────────────────────────────────────────────

    public static final IRecipeType<AlloyFurnaceRecipe> ALLOY_FURNACE =
            IRecipeType.create(OmniTech.MODID, "alloy_furnace", AlloyFurnaceRecipe.class);

    public static final IRecipeType<ManualMaceratorRecipe> MANUAL_MACERATOR =
            IRecipeType.create(OmniTech.MODID, "manual_macerator", ManualMaceratorRecipe.class);

    public static final IRecipeType<ManualCentrifugeRecipe> MANUAL_CENTRIFUGE =
            IRecipeType.create(OmniTech.MODID, "manual_centrifuge", ManualCentrifugeRecipe.class);

    public static final IRecipeType<SmelterRecipe> SMELTING =
            IRecipeType.create(OmniTech.MODID, "smelting", SmelterRecipe.class);

    public static final IRecipeType<FoundryRecipe> FOUNDRY =
            IRecipeType.create(OmniTech.MODID, "foundry", FoundryRecipe.class);

    public static final IRecipeType<ChemicalReactorRecipe> CHEMICAL_REACTOR =
            IRecipeType.create(OmniTech.MODID, "chemical_reactor", ChemicalReactorRecipe.class);

    public static final IRecipeType<ElectrolysisRecipe> ELECTROLYSIS =
            IRecipeType.create(OmniTech.MODID, "electrolysis", ElectrolysisRecipe.class);

    public static final IRecipeType<FractionalDistillationRecipe> FRACTIONAL_DISTILLATION =
            IRecipeType.create(OmniTech.MODID, "fractional_distillation", FractionalDistillationRecipe.class);

    public static final IRecipeType<SolvationRecipe> SOLVATION =
            IRecipeType.create(OmniTech.MODID, "solvation", SolvationRecipe.class);

    public static final IRecipeType<FluidCollectorRecipe> FLUID_COLLECTOR =
            IRecipeType.create(OmniTech.MODID, "fluid_collector", FluidCollectorRecipe.class);

    public static final IRecipeType<FluidFillerRecipe> FLUID_FILLER =
            IRecipeType.create(OmniTech.MODID, "fluid_filler", FluidFillerRecipe.class);

    public static final IRecipeType<FilterPressRecipe> FILTER_PRESS =
            IRecipeType.create(OmniTech.MODID, "filter_press", FilterPressRecipe.class);

    public static final IRecipeType<GlassBlowingRecipe> GLASS_BLOWING =
            IRecipeType.create(OmniTech.MODID, "glass_blowing", GlassBlowingRecipe.class);

    public static final IRecipeType<PressRecipe> PRESS =
            IRecipeType.create(OmniTech.MODID, "press", PressRecipe.class);

    public static final IRecipeType<BoilingRecipe> BOILING =
            IRecipeType.create(OmniTech.MODID, "boiling", BoilingRecipe.class);

    public static final IRecipeType<SolidFormManager.SolidForm> SOLID_FORM =
            IRecipeType.create(OmniTech.MODID, "solid_form", SolidFormManager.SolidForm.class);

    public static final IRecipeType<FermentationRecipe> FERMENTATION =
            IRecipeType.create(OmniTech.MODID, "fermentation", FermentationRecipe.class);

    public static final IRecipeType<FermentationRecipeManager.Dissolution> FERMENTER_DISSOLVE =
            IRecipeType.create(OmniTech.MODID, "fermenter_dissolve", FermentationRecipeManager.Dissolution.class);

    public static final IRecipeType<FermentationRecipeManager.Microbe> FERMENTER_MICROBES =
            IRecipeType.create(OmniTech.MODID, "fermenter_microbes", FermentationRecipeManager.Microbe.class);

    /**
     * Not backed by any recipe JSON — filling a bucket is a generic capability
     * interaction (see {@link OmniTechFluids.FluidObject#bucket}), not a data-driven
     * recipe. This is a display-only pairing so JEI can show "right-click an empty
     * bucket on a Fluid Tank / Fluid Filler holding this fluid" for every fluid that
     * actually has a bucket.
     */
    private record FluidFillerRecipe(net.minecraft.world.level.material.Fluid fluid,
                                      net.minecraft.world.item.Item filledBucket) {}

    private static List<FluidFillerRecipe> buildFluidFillerRecipes() {
        List<FluidFillerRecipe> recipes = new java.util.ArrayList<>();
        for (OmniTechFluids.FluidObject fluidObject : OmniTechFluids.all().values()) {
            if (fluidObject.bucket == null) continue;
            recipes.add(new FluidFillerRecipe(fluidObject.source.get(), fluidObject.bucket.get()));
        }
        return recipes;
    }

    // ── Plugin identity ───────────────────────────────────────────────────────

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(OmniTech.MODID, "jei_plugin");
    }

    // ── Category registration ─────────────────────────────────────────────────

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        IGuiHelper gui = helpers.getGuiHelper();

        registration.addRecipeCategories(
                new AlloyFurnaceCategory(gui),
                new ManualMaceratorCategory(gui),
                new ManualCentrifugeCategory(gui),
                new SmeltingCategory(gui),
                new FoundryCategory(gui),
                new ChemicalReactorCategory(gui),
                new ElectrolysisCategory(gui),
                new FractionalDistillationCategory(gui),
                new SolvationCategory(gui),
                new FluidCollectorCategory(gui),
                new FluidFillerCategory(gui),
                new FilterPressCategory(gui),
                new SolidFormCategory(gui),
                new BoilingCategory(gui),
                new PressCategory(gui),
                new GlassBlowingCategory(gui),
                new FermentationCategory(gui),
                new FermenterDissolveCategory(gui),
                new FermenterMicrobeCategory(gui)
        );
    }

    // ── Recipe registration ───────────────────────────────────────────────────

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(ALLOY_FURNACE,            AlloyFurnaceRecipeManager.getAllRecipes());
        registration.addRecipes(MANUAL_MACERATOR,         ManualMaceratorRecipeManager.getAllRecipes());
        registration.addRecipes(MANUAL_CENTRIFUGE,        ManualCentrifugeRecipeManager.getAllRecipes());
        registration.addRecipes(SMELTING,                 SmelterRecipeManager.getAllRecipes());
        registration.addRecipes(FOUNDRY,                  FoundryRecipeManager.getAllRecipes());
        registration.addRecipes(CHEMICAL_REACTOR,         ChemicalReactorRecipeManager.getAllRecipes());
        registration.addRecipes(ELECTROLYSIS,             ElectrolysisRecipeManager.getAllRecipes());
        registration.addRecipes(FRACTIONAL_DISTILLATION,  FractionalDistillationRecipeManager.getAllRecipes());
        registration.addRecipes(SOLVATION,                SolvationRecipeManager.getAllRecipes());
        registration.addRecipes(FLUID_COLLECTOR,          FluidCollectorRecipeManager.getAllRecipes());
        registration.addRecipes(FLUID_FILLER,             buildFluidFillerRecipes());
        registration.addRecipes(FILTER_PRESS,             FilterPressRecipeManager.getAllRecipes());
        registration.addRecipes(SOLID_FORM,               SolidFormManager.getAll());
        registration.addRecipes(BOILING,                  BoilingRecipeManager.getAllRecipes());
        registration.addRecipes(PRESS,                    PressRecipeManager.getAllRecipes());
        registration.addRecipes(GLASS_BLOWING,            GlassBlowingRecipeManager.getAllRecipes());
        registration.addRecipes(FERMENTATION,             FermentationRecipeManager.getAllRecipes());
        registration.addRecipes(FERMENTER_DISSOLVE,       FermentationRecipeManager.getDissolutions());
        registration.addRecipes(FERMENTER_MICROBES,       FermentationRecipeManager.getMicrobes());

        // Straining and separating mixtures isn't a fixed recipe, so explain it on the items themselves
        registration.addItemStackInfo(new ItemStack(OmniTechBlocks.FILTER_PRESS.get()),
                Component.translatable("jei.omnitech.info.filter_press"));
        registration.addItemStackInfo(new ItemStack(OmniTechBlocks.MANUAL_CENTRIFUGE.get()),
                Component.translatable("jei.omnitech.info.manual_centrifuge"));
        registration.addItemStackInfo(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(OmniTech.MODID, "mixture_dust"))),
                Component.translatable("jei.omnitech.info.mixture_dust"));
        registration.addItemStackInfo(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(OmniTech.MODID, "yeast"))),
                Component.translatable("jei.omnitech.info.yeast"));
        registration.addItemStackInfo(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(OmniTech.MODID, "mold_culture"))),
                Component.translatable("jei.omnitech.info.mold_culture"));
        registration.addItemStackInfo(new ItemStack(OmniTechBlocks.PRESS.get()),
                Component.translatable("jei.omnitech.info.press"));
        for (String id : List.of("lactic_acid_bacteria", "cheese_curd", "ricotta", "grapes", "mother_of_vinegar", "drinking_bottle", "lemon", "citric_acid")) {
            registration.addItemStackInfo(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(OmniTech.MODID, id))),
                    Component.translatable("jei.omnitech.info." + id));
        }
    }

    // ── Catalyst registration ─────────────────────────────────────────────────

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(ALLOY_FURNACE,           OmniTechBlocks.ALLOY_FURNACE.get());
        registration.addCraftingStation(MANUAL_MACERATOR,        OmniTechBlocks.MANUAL_MACERATOR.get());
        registration.addCraftingStation(MANUAL_CENTRIFUGE,       OmniTechBlocks.MANUAL_CENTRIFUGE.get());
        registration.addCraftingStation(SMELTING,                OmniTechBlocks.SMELTER.get());
        registration.addCraftingStation(FOUNDRY,                 OmniTechBlocks.FOUNDRY.get());
        registration.addCraftingStation(CHEMICAL_REACTOR,        OmniTechBlocks.CHEMICAL_REACTOR.get());
        registration.addCraftingStation(ELECTROLYSIS,            OmniTechBlocks.ELECTROLYSIS_MACHINE.get());
        registration.addCraftingStation(FRACTIONAL_DISTILLATION, OmniTechBlocks.FRACTIONAL_DISTILLER.get());
        registration.addCraftingStation(SOLVATION,               OmniTechBlocks.SOLVATION_MACHINE.get());
        registration.addCraftingStation(FLUID_COLLECTOR,         OmniTechBlocks.FLUID_COLLECTOR.get());
        registration.addCraftingStation(FLUID_FILLER,            OmniTechBlocks.FLUID_FILLER.get());
        registration.addCraftingStation(FLUID_FILLER,            OmniTechBlocks.FLUID_TANK.get());
        registration.addCraftingStation(FILTER_PRESS,            OmniTechBlocks.FILTER_PRESS.get());
        registration.addCraftingStation(SOLID_FORM,              OmniTechBlocks.MANUAL_CENTRIFUGE.get());
        registration.addCraftingStation(BOILING,                 OmniTechBlocks.BOILER.get());
        registration.addCraftingStation(PRESS,                   OmniTechBlocks.PRESS.get());
        registration.addCraftingStation(GLASS_BLOWING,           OmniTechBlocks.GLASS_BLOWING_STATION.get());
        registration.addCraftingStation(FERMENTATION,            OmniTechBlocks.FERMENTER.get());
        registration.addCraftingStation(FERMENTER_DISSOLVE,      OmniTechBlocks.FERMENTER.get());
        registration.addCraftingStation(FERMENTER_MICROBES,      OmniTechBlocks.FERMENTER.get());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Category implementations
    // ═════════════════════════════════════════════════════════════════════════

    // ── Alloy Furnace: items in → items out + temperature ─────────────────────

    static class AlloyFurnaceCategory implements IRecipeCategory<AlloyFurnaceRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        AlloyFurnaceCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 65);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.ALLOY_FURNACE.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<AlloyFurnaceRecipe> getRecipeType() { return ALLOY_FURNACE; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.alloy_furnace"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, AlloyFurnaceRecipe recipe, IFocusGroup focuses) {
            List<net.minecraft.world.item.Item> inputs = recipe.getIngredients();
            for (int i = 0; i < inputs.size(); i++) {
                int x = (i % 2) * 18;
                int y = (i / 2) * 18;
                builder.addInputSlot(x, y)
                        .add(new ItemStack(inputs.get(i)))
                        .setStandardSlotBackground();
            }
            List<net.minecraft.world.item.Item> outputs = recipe.getOutputs();
            for (int i = 0; i < outputs.size(); i++) {
                int x = 108 + (i % 2) * 18;
                int y = (i / 2) * 18;
                builder.addOutputSlot(x, y)
                        .add(new ItemStack(outputs.get(i)))
                        .setOutputSlotBackground();
            }
        }

        @Override
        public void draw(AlloyFurnaceRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 60, 20);
            graphics.text(font, recipe.getMinTemperature() + "°C min", 0, 50, 0x555555, false);
        }
    }

    // ── Manual Macerator: item in → items out + KF ───────────────────────────

    static class ManualMaceratorCategory implements IRecipeCategory<ManualMaceratorRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        ManualMaceratorCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 60);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.MANUAL_MACERATOR.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<ManualMaceratorRecipe> getRecipeType() { return MANUAL_MACERATOR; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.manual_macerator"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 60; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ManualMaceratorRecipe recipe, IFocusGroup focuses) {
            if (recipe.getInput() != null)
                builder.addInputSlot(0, 10).add(new ItemStack(recipe.getInput())).setStandardSlotBackground();

            List<ManualMaceratorRecipe.Output> outputs = recipe.getOutputs();
            for (int i = 0; i < outputs.size() && i < 6; i++) {
                builder.addOutputSlot(108 + (i % 3) * 18, (i / 3) * 18)
                        .add(new ItemStack(outputs.get(i).item(), outputs.get(i).count()))
                        .setOutputSlotBackground();
            }
        }

        @Override
        public void draw(ManualMaceratorRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 54, 14);
            graphics.text(font, "KF: " + recipe.getRequiredKineticForce(), 0, 46, 0x555555, false);
        }
    }

    // ── Manual Centrifuge: item in → items out + KF ──────────────────────────

    static class ManualCentrifugeCategory implements IRecipeCategory<ManualCentrifugeRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        ManualCentrifugeCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 78);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.MANUAL_CENTRIFUGE.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<ManualCentrifugeRecipe> getRecipeType() { return MANUAL_CENTRIFUGE; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.manual_centrifuge"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 78; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ManualCentrifugeRecipe recipe, IFocusGroup focuses) {
            if (recipe.getInput() != null)
                builder.addInputSlot(0, 20).add(new ItemStack(recipe.getInput())).setStandardSlotBackground();

            List<ManualCentrifugeRecipe.Output> outputs = recipe.getOutputs();
            for (int i = 0; i < outputs.size() && i < 9; i++) {
                builder.addOutputSlot(96 + (i % 3) * 18, (i / 3) * 18)
                        .add(new ItemStack(outputs.get(i).item(), outputs.get(i).count()))
                        .setOutputSlotBackground();
            }
        }

        @Override
        public void draw(ManualCentrifugeRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 54, 26);
            graphics.text(font, "KF: " + recipe.getRequiredKineticForce(), 0, 62, 0x555555, false);
        }
    }

    // ── Smelter: items in → fluid out + temperature ───────────────────────────

    static class SmeltingCategory implements IRecipeCategory<SmelterRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        SmeltingCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 65);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.SMELTER.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<SmelterRecipe> getRecipeType() { return SMELTING; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.smelting"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SmelterRecipe recipe, IFocusGroup focuses) {
            List<net.minecraft.world.item.Item> ingredients = recipe.getIngredients();
            for (int i = 0; i < ingredients.size() && i < 4; i++) {
                builder.addInputSlot((i % 2) * 18, (i / 2) * 18)
                        .add(new ItemStack(ingredients.get(i)))
                        .setStandardSlotBackground();
            }
            FluidStack out = recipe.getOutput();
            if (!out.isEmpty()) {
                builder.addOutputSlot(116, 8)
                        .add(out.getFluid(), out.getAmount())
                        .setFluidRenderer(out.getAmount(), false, 16, 36);
            }
        }

        @Override
        public void draw(SmelterRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 56, 14);
            graphics.text(font, recipe.getRequiredMinimalTemperature() + "°C min", 0, 52, 0x555555, false);
        }
    }

    // ── Foundry: fluid in + template item → item out + temperature ────────────

    static class FoundryCategory implements IRecipeCategory<FoundryRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        FoundryCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 65);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.FOUNDRY.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<FoundryRecipe> getRecipeType() { return FOUNDRY; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.foundry"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FoundryRecipe recipe, IFocusGroup focuses) {
            net.minecraft.world.level.material.Fluid inputFluid = recipe.getInputFluid();
            if (inputFluid != null) {
                builder.addInputSlot(0, 2)
                        .add(inputFluid, recipe.getInputAmount())
                        .setFluidRenderer(recipe.getInputAmount(), false, 16, 36);
            }
            if (recipe.getTemplateItem() != null)
                builder.addInputSlot(22, 12).add(new ItemStack(recipe.getTemplateItem())).setStandardSlotBackground();

            if (recipe.getOutputItem() != null)
                builder.addOutputSlot(116, 12).add(new ItemStack(recipe.getOutputItem())).setOutputSlotBackground();
        }

        @Override
        public void draw(FoundryRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 56, 14);
            graphics.text(font, recipe.getRequiredMinimalTemperature() + "°C min", 0, 52, 0x555555, false);
        }
    }

    // ── Chemical Reactor: 2 fluids + optional catalyst → fluid + temp + time ──

    static class ChemicalReactorCategory implements IRecipeCategory<ChemicalReactorRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        ChemicalReactorCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 80);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.CHEMICAL_REACTOR.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<ChemicalReactorRecipe> getRecipeType() { return CHEMICAL_REACTOR; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.chemical_reactor"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 80; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ChemicalReactorRecipe recipe, IFocusGroup focuses) {
            FluidStack in1 = recipe.getInput1Fluid();
            if (!in1.isEmpty())
                builder.addInputSlot(0, 0).add(in1.getFluid(), in1.getAmount()).setFluidRenderer(in1.getAmount(), false, 16, 36);

            FluidStack in2 = recipe.getInput2Fluid();
            if (!in2.isEmpty())
                builder.addInputSlot(20, 0).add(in2.getFluid(), in2.getAmount()).setFluidRenderer(in2.getAmount(), false, 16, 36);

            if (recipe.requiresCatalyst() && recipe.getCatalystItem() != null)
                builder.addInputSlot(40, 12).add(new ItemStack(recipe.getCatalystItem())).setStandardSlotBackground();

            FluidStack out = recipe.getOutput();
            if (!out.isEmpty())
                builder.addOutputSlot(116, 0).add(out.getFluid(), out.getAmount()).setFluidRenderer(out.getAmount(), false, 16, 36);
        }

        @Override
        public void draw(ChemicalReactorRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 70, 12);
            graphics.text(font, recipe.getRequiredTemperature() + "°C", 0, 46, 0x555555, false);
            graphics.text(font, recipe.getProductionTime() + "t", 0, 56, 0x555555, false);
        }
    }

    // ── Electrolysis: fluid + anode + cathode → 3 fluids + energy ────────────

    static class ElectrolysisCategory implements IRecipeCategory<ElectrolysisRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        ElectrolysisCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 90);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.ELECTROLYSIS_MACHINE.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<ElectrolysisRecipe> getRecipeType() { return ELECTROLYSIS; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.electrolysis"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 90; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ElectrolysisRecipe recipe, IFocusGroup focuses) {
            FluidStack in = recipe.getInputFluid();
            if (!in.isEmpty())
                builder.addInputSlot(0, 0).add(in.getFluid(), in.getAmount()).setFluidRenderer(in.getAmount(), false, 16, 36);

            net.minecraft.world.item.Item anode = recipe.getAnodeItem();
            if (anode != null) builder.addInputSlot(20, 10).add(new ItemStack(anode)).setStandardSlotBackground();

            net.minecraft.world.item.Item cathode = recipe.getCathodeItem();
            if (cathode != null) builder.addInputSlot(20, 30).add(new ItemStack(cathode)).setStandardSlotBackground();

            FluidStack outAnode = recipe.getOutputAnode();
            if (!outAnode.isEmpty())
                builder.addOutputSlot(108, 0).add(outAnode.getFluid(), outAnode.getAmount()).setFluidRenderer(outAnode.getAmount(), false, 16, 36);

            FluidStack outCathode = recipe.getOutputCathode();
            if (!outCathode.isEmpty())
                builder.addOutputSlot(126, 0).add(outCathode.getFluid(), outCathode.getAmount()).setFluidRenderer(outCathode.getAmount(), false, 16, 36);

            FluidStack outSolution = recipe.getOutputSolution();
            if (!outSolution.isEmpty())
                builder.addOutputSlot(144, 0).add(outSolution.getFluid(), outSolution.getAmount()).setFluidRenderer(outSolution.getAmount(), false, 16, 36);
        }

        @Override
        public void draw(ElectrolysisRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 60, 12);
            graphics.text(font, recipe.getEnergyRequired() + " EU", 0, 48, 0x555555, false);
        }
    }

    // ── Fractional Distillation: fluid in → multiple fluids out + temp ────────

    static class FractionalDistillationCategory implements IRecipeCategory<FractionalDistillationRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        FractionalDistillationCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 80);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.FRACTIONAL_DISTILLER.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<FractionalDistillationRecipe> getRecipeType() { return FRACTIONAL_DISTILLATION; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.fractional_distillation"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 80; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FractionalDistillationRecipe recipe, IFocusGroup focuses) {
            net.minecraft.world.level.material.Fluid inputFluid = recipe.getInputFluid();
            if (inputFluid != null)
                builder.addInputSlot(0, 2).add(inputFluid, recipe.getInputAmount()).setFluidRenderer(recipe.getInputAmount(), false, 16, 36);

            int count = recipe.getOutputCount();
            for (int i = 0; i < count && i < 5; i++) {
                FluidStack out = recipe.getOutputStack(i);
                if (!out.isEmpty())
                    builder.addOutputSlot(100 + i * 18, 2).add(out.getFluid(), out.getAmount()).setFluidRenderer(out.getAmount(), false, 16, 36);
            }
        }

        @Override
        public void draw(FractionalDistillationRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 56, 12);
            graphics.text(font, recipe.getRequiredTemperature() + "°C", 0, 46, 0x555555, false);
            graphics.text(font, recipe.getProductionTime() + "t", 0, 56, 0x555555, false);
        }
    }

    // ── Solvation: fluid + item in → fluid out + kinetic force ───────────────

    static class SolvationCategory implements IRecipeCategory<SolvationRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        SolvationCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 65);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.SOLVATION_MACHINE.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<SolvationRecipe> getRecipeType() { return SOLVATION; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.solvation"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SolvationRecipe recipe, IFocusGroup focuses) {
            FluidStack in = recipe.getInputFluid();
            if (!in.isEmpty())
                builder.addInputSlot(0, 2).add(in.getFluid(), in.getAmount()).setFluidRenderer(in.getAmount(), false, 16, 36);

            net.minecraft.world.item.Item inputItem = recipe.getInputItem();
            if (inputItem != null)
                builder.addInputSlot(20, 12).add(new ItemStack(inputItem)).setStandardSlotBackground();

            FluidStack out = recipe.getOutputFluid();
            if (!out.isEmpty())
                builder.addOutputSlot(116, 2).add(out.getFluid(), out.getAmount()).setFluidRenderer(out.getAmount(), false, 16, 36);
        }

        @Override
        public void draw(SolvationRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 56, 12);
            graphics.text(font, "KF: " + recipe.getRequiredKineticForce(), 0, 46, 0x555555, false);
        }
    }

    // ── Fluid Collector: block → fluid ────────────────────────────────────────

    static class FluidCollectorCategory implements IRecipeCategory<FluidCollectorRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        FluidCollectorCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(120, 50);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.FLUID_COLLECTOR.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public IRecipeType<FluidCollectorRecipe> getRecipeType() { return FLUID_COLLECTOR; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.fluid_collector"); }
        @Override public int getWidth()  { return 120; }
        @Override public int getHeight() { return 50; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FluidCollectorRecipe recipe, IFocusGroup focuses) {
            net.minecraft.world.level.block.Block block = recipe.getInputBlock();
            if (block != null)
                builder.addInputSlot(0, 10).add(new ItemStack(block.asItem())).setStandardSlotBackground();

            FluidStack out = recipe.getOutputFluid();
            if (!out.isEmpty())
                builder.addOutputSlot(82, 2).add(out.getFluid(), out.getAmount()).setFluidRenderer(out.getAmount(), false, 16, 36);
        }

        @Override
        public void draw(FluidCollectorRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 44, 12);
        }
    }

    // ── Fluid Filler: fluid (in a tank/filler) + empty bucket → filled bucket ──
    //
    // Display-only — see FluidFillerRecipe. Right-clicking an empty vanilla bucket on
    // a Fluid Tank or Fluid Filler holding the shown fluid fills it, the same generic
    // capability interaction water/lava buckets already use against vanilla tanks.

    static class FluidFillerCategory implements IRecipeCategory<FluidFillerRecipe> {
        private final IDrawable icon;
        private final IDrawable arrow;

        FluidFillerCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.FLUID_FILLER.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<FluidFillerRecipe> getRecipeType() { return FLUID_FILLER; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.fluid_filler"); }
        @Override public int getWidth()  { return 120; }
        @Override public int getHeight() { return 50; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FluidFillerRecipe recipe, IFocusGroup focuses) {
            builder.addInputSlot(0, 2)
                    .add(recipe.fluid(), net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME)
                    .setFluidRenderer(net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME, false, 16, 36);

            builder.addInputSlot(22, 12)
                    .add(new ItemStack(net.minecraft.world.item.Items.BUCKET))
                    .setStandardSlotBackground();

            builder.addOutputSlot(82, 2)
                    .add(new ItemStack(recipe.filledBucket()))
                    .setOutputSlotBackground();
        }

        @Override
        public void draw(FluidFillerRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 44, 12);
        }
    }

    // ── Shared fluid-slot helper for the fermenter / filter press categories ──

    /** A fluid slot filled to the brim with {@code amount} mB, with optional grey tooltip lines. */
    private static void fluidSlot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y,
                                  Fluid fluid, int amount, Component... notes) {
        var slot = builder.addSlot(role, x, y).add(fluid, amount).setFluidRenderer(amount, false, 16, 36);
        if (notes.length > 0) {
            slot.addRichTooltipCallback((view, tooltip) -> {
                for (Component note : notes) tooltip.add(note.copy().withStyle(ChatFormatting.GRAY));
            });
        }
    }

    // ── Filter Press: fluid (or a mixture carrying it undissolved) → item + filtrate ──

    static class FilterPressCategory implements IRecipeCategory<FilterPressRecipe> {
        private final IDrawable icon;
        private final IDrawable arrow;

        FilterPressCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.FILTER_PRESS.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<FilterPressRecipe> getRecipeType() { return FILTER_PRESS; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.filter_press"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FilterPressRecipe recipe, IFocusGroup focuses) {
            FluidStack in = recipe.getInputFluid();
            if (!in.isEmpty())
                fluidSlot(builder, RecipeIngredientRole.INPUT, 0, 2, in.getFluid(), in.getAmount());

            ItemStack outItem = recipe.getOutputItemStack();
            if (!outItem.isEmpty())
                builder.addOutputSlot(82, 12).add(outItem).setOutputSlotBackground();

            FluidStack out = recipe.getOutputFluid();
            if (!out.isEmpty())
                fluidSlot(builder, RecipeIngredientRole.OUTPUT, 106, 2, out.getFluid(), out.getAmount());
        }

        @Override
        public void draw(FilterPressRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 44, 12);
            graphics.text(font, "KF: " + recipe.getRequiredKineticForce(), 0, 46, 0x555555, false);
        }
    }

    // ── Manual Centrifuge: an ingredient separated out of Mixture Dust → its solid form ──

    static class SolidFormCategory implements IRecipeCategory<SolidFormManager.SolidForm> {
        private final IDrawable icon;
        private final IDrawable arrow;

        SolidFormCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.MANUAL_CENTRIFUGE.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<SolidFormManager.SolidForm> getRecipeType() { return SOLID_FORM; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.solid_form"); }
        @Override public int getWidth()  { return 120; }
        @Override public int getHeight() { return 50; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SolidFormManager.SolidForm recipe, IFocusGroup focuses) {
            Fluid fluid = recipe.fluid();
            if (fluid != null)
                fluidSlot(builder, RecipeIngredientRole.INPUT, 0, 2, fluid, recipe.amount(),
                        Component.translatable("jei.omnitech.solid_form.source"));
            ItemStack out = recipe.stack();
            if (!out.isEmpty())
                builder.addOutputSlot(82, 12).add(out).setOutputSlotBackground();
        }

        @Override
        public void draw(SolidFormManager.SolidForm recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 44, 12);
        }
    }

    // ── Glass Blowing Station: one item melted and blown into glassware at a minimum heat ──

    static class GlassBlowingCategory implements IRecipeCategory<GlassBlowingRecipe> {
        private final IDrawable icon;
        private final IDrawable arrow;

        GlassBlowingCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.GLASS_BLOWING_STATION.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<GlassBlowingRecipe> getRecipeType() { return GLASS_BLOWING; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.glass_blowing"); }
        @Override public int getWidth()  { return 120; }
        @Override public int getHeight() { return 50; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, GlassBlowingRecipe recipe, IFocusGroup focuses) {
            if (recipe.getInput() != null)
                builder.addInputSlot(0, 12).add(new ItemStack(recipe.getInput())).setStandardSlotBackground();
            ItemStack result = recipe.getResult();
            if (!result.isEmpty())
                builder.addOutputSlot(82, 12).add(result).setOutputSlotBackground();
        }

        @Override
        public void draw(GlassBlowingRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 44, 12);
            graphics.text(font, recipe.getRequiredMinimalTemperature() + "°C", 0, 36, 0x555555, false);
        }
    }

    // ── Press: one item → juice + leftover pulp ──

    static class PressCategory implements IRecipeCategory<PressRecipe> {
        private final IDrawable icon;
        private final IDrawable arrow;

        PressCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.PRESS.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<PressRecipe> getRecipeType() { return PRESS; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.press"); }
        @Override public int getWidth()  { return 140; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, PressRecipe recipe, IFocusGroup focuses) {
            if (recipe.getInput() != null)
                builder.addInputSlot(0, 12).add(new ItemStack(recipe.getInput())).setStandardSlotBackground();

            ItemStack pulp = recipe.getOutputItem();
            if (!pulp.isEmpty()) {
                var slot = builder.addOutputSlot(82, 12).add(pulp).setOutputSlotBackground();
                if (recipe.getOutputItemChance() < 1f) {
                    int percent = Math.round(recipe.getOutputItemChance() * 100f);
                    slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable("jei.omnitech.boiler.chance", percent).withStyle(ChatFormatting.GRAY)));
                }
            }

            FluidStack juice = recipe.getOutputFluid();
            if (!juice.isEmpty())
                fluidSlot(builder, RecipeIngredientRole.OUTPUT, 106, 2, juice.getFluid(), juice.getAmount());
        }

        @Override
        public void draw(PressRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 44, 12);
            graphics.text(font, "KF: " + recipe.getRequiredKineticForce(), 0, 46, 0x555555, false);
        }
    }

    // ── Boiler: fluid boiled off → residue item and/or a different vapour ──

    static class BoilingCategory implements IRecipeCategory<BoilingRecipe> {
        private final IDrawable icon;
        private final IDrawable arrow;

        BoilingCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.BOILER.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<BoilingRecipe> getRecipeType() { return BOILING; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.boiler"); }
        @Override public int getWidth()  { return 140; }
        @Override public int getHeight() { return 50; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BoilingRecipe recipe, IFocusGroup focuses) {
            Fluid in = recipe.getInputFluid();
            if (in != null)
                fluidSlot(builder, RecipeIngredientRole.INPUT, 0, 2, in, recipe.getInputAmount());

            if (recipe.hasResult()) {
                ItemStack result = recipe.resultStack();
                var slot = builder.addOutputSlot(82, 12).add(result).setOutputSlotBackground();
                if (recipe.getResultChance() < 1f) {
                    int percent = Math.round(recipe.getResultChance() * 100f);
                    slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable("jei.omnitech.boiler.chance", percent).withStyle(ChatFormatting.GRAY)));
                }
            }
            if (recipe.convertsFluid() && recipe.getOutputFluid() != null)
                fluidSlot(builder, RecipeIngredientRole.OUTPUT, 106, 2, recipe.getOutputFluid(), recipe.getOutputAmount(),
                        Component.translatable("jei.omnitech.boiler.vapour"));
        }

        @Override
        public void draw(BoilingRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 44, 12);
        }
    }

    // ── Fermenter: timed reaction inside the mixture (inputs + catalysts → outputs) ──

    static class FermentationCategory implements IRecipeCategory<FermentationRecipe> {
        private final IDrawable icon;
        private final IDrawable arrow;

        FermentationCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.FERMENTER.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<FermentationRecipe> getRecipeType() { return FERMENTATION; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.fermentation"); }
        @Override public int getWidth()  { return 176; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FermentationRecipe recipe, IFocusGroup focuses) {
            int x = 0;
            for (FermentationRecipe.FluidAmount in : recipe.getInputs()) {
                if (in.fluid() == null) continue;
                fluidSlot(builder, RecipeIngredientRole.INPUT, x, 2, in.fluid(), in.amount(),
                        Component.translatable("jei.omnitech.fermentation.consumed"));
                x += 18;
            }
            for (FermentationRecipe.FluidAmount cat : recipe.getCatalysts()) {
                if (cat.fluid() == null) continue;
                fluidSlot(builder, RecipeIngredientRole.INPUT, x, 2, cat.fluid(), cat.amount(),
                        Component.translatable("jei.omnitech.fermentation.catalyst"));
                x += 18;
            }

            x = 106;
            for (FermentationRecipe.FluidAmount out : recipe.getOutputs()) {
                if (out.fluid() == null) continue;
                if (out.dissolved()) fluidSlot(builder, RecipeIngredientRole.OUTPUT, x, 2, out.fluid(), out.amount());
                else fluidSlot(builder, RecipeIngredientRole.OUTPUT, x, 2, out.fluid(), out.amount(),
                        Component.translatable("jei.omnitech.fermentation.undissolved"));
                x += 18;
            }
            ItemStack item = recipe.getOutputItemStack();
            if (!item.isEmpty())
                builder.addOutputSlot(x, 12).add(item).setOutputSlotBackground();
        }

        @Override
        public void draw(FermentationRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 76, 12);
            graphics.text(font, Component.translatable("jei.omnitech.fermentation.interval", recipe.getInterval()),
                    0, 46, 0x555555, false);
        }
    }

    // ── Fermenter: item dropped in the input slot dissolves into the mixture ──

    static class FermenterDissolveCategory implements IRecipeCategory<FermentationRecipeManager.Dissolution> {
        private final IDrawable icon;
        private final IDrawable arrow;

        FermenterDissolveCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.FERMENTER.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<FermentationRecipeManager.Dissolution> getRecipeType() { return FERMENTER_DISSOLVE; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.fermenter_dissolve"); }
        @Override public int getWidth()  { return 120; }
        @Override public int getHeight() { return 50; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FermentationRecipeManager.Dissolution recipe, IFocusGroup focuses) {
            BuiltInRegistries.ITEM.getOptional(recipe.itemId()).ifPresent(item ->
                    builder.addInputSlot(0, 12).add(new ItemStack(item)).setStandardSlotBackground());

            FermentationRecipe.FluidAmount out = recipe.fluid();
            if (out.fluid() == null) return;
            if (out.dissolved()) fluidSlot(builder, RecipeIngredientRole.OUTPUT, 82, 2, out.fluid(), out.amount());
            else fluidSlot(builder, RecipeIngredientRole.OUTPUT, 82, 2, out.fluid(), out.amount(),
                    Component.translatable("jei.omnitech.fermentation.undissolved"));
        }

        @Override
        public void draw(FermentationRecipeManager.Dissolution recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 44, 12);
        }
    }

    // ── Fermenter: microbes that appear on their own once their food is present ──

    static class FermenterMicrobeCategory implements IRecipeCategory<FermentationRecipeManager.Microbe> {
        private final IDrawable icon;
        private final IDrawable arrow;

        FermenterMicrobeCategory(IGuiHelper gui) {
            this.icon  = gui.createDrawableItemLike(OmniTechBlocks.FERMENTER.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override public IRecipeType<FermentationRecipeManager.Microbe> getRecipeType() { return FERMENTER_MICROBES; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.fermenter_microbes"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FermentationRecipeManager.Microbe recipe, IFocusGroup focuses) {
            int x = 0;
            for (var id : recipe.requires()) {
                Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
                if (fluid == null) continue;
                fluidSlot(builder, RecipeIngredientRole.INPUT, x, 2, fluid, 1000,
                        Component.translatable("jei.omnitech.fermenter_microbes.requires"));
                x += 18;
            }
            FermentationRecipe.FluidAmount microbe = recipe.fluid();
            if (microbe.fluid() != null)
                fluidSlot(builder, RecipeIngredientRole.OUTPUT, 106, 2, microbe.fluid(), microbe.amount());
        }

        @Override
        public void draw(FermentationRecipeManager.Microbe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 76, 12);
            // Weighted only against the other microbes whose food is also present, so no fixed %
            graphics.text(font, Component.translatable("jei.omnitech.fermenter_microbes.weight", recipe.weight()), 0, 46, 0x555555, false);
        }
    }
}
