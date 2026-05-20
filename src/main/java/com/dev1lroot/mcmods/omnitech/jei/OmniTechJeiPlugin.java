/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.jei;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.recipes.*;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

@JeiPlugin
public class OmniTechJeiPlugin implements IModPlugin {

    // ── Recipe types ──────────────────────────────────────────────────────────

    public static final RecipeType<AlloyFurnaceRecipe> ALLOY_FURNACE =
            RecipeType.create(OmniTech.MODID, "alloy_furnace", AlloyFurnaceRecipe.class);

    public static final RecipeType<ManualMaceratorRecipe> MANUAL_MACERATOR =
            RecipeType.create(OmniTech.MODID, "manual_macerator", ManualMaceratorRecipe.class);

    public static final RecipeType<ManualCentrifugeRecipe> MANUAL_CENTRIFUGE =
            RecipeType.create(OmniTech.MODID, "manual_centrifuge", ManualCentrifugeRecipe.class);

    public static final RecipeType<SmelterRecipe> SMELTING =
            RecipeType.create(OmniTech.MODID, "smelting", SmelterRecipe.class);

    public static final RecipeType<BoilerRecipe> BOILER =
            RecipeType.create(OmniTech.MODID, "boiler", BoilerRecipe.class);

    public static final RecipeType<FoundryRecipe> FOUNDRY =
            RecipeType.create(OmniTech.MODID, "foundry", FoundryRecipe.class);

    public static final RecipeType<ChemicalReactorRecipe> CHEMICAL_REACTOR =
            RecipeType.create(OmniTech.MODID, "chemical_reactor", ChemicalReactorRecipe.class);

    public static final RecipeType<ElectrolysisRecipe> ELECTROLYSIS =
            RecipeType.create(OmniTech.MODID, "electrolysis", ElectrolysisRecipe.class);

    public static final RecipeType<FractionalDistillationRecipe> FRACTIONAL_DISTILLATION =
            RecipeType.create(OmniTech.MODID, "fractional_distillation", FractionalDistillationRecipe.class);

    public static final RecipeType<SolvationRecipe> SOLVATION =
            RecipeType.create(OmniTech.MODID, "solvation", SolvationRecipe.class);

    public static final RecipeType<FluidCollectorRecipe> FLUID_COLLECTOR =
            RecipeType.create(OmniTech.MODID, "fluid_collector", FluidCollectorRecipe.class);

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
                new BoilerCategory(gui),
                new FoundryCategory(gui),
                new ChemicalReactorCategory(gui),
                new ElectrolysisCategory(gui),
                new FractionalDistillationCategory(gui),
                new SolvationCategory(gui),
                new FluidCollectorCategory(gui)
        );
    }

    // ── Recipe registration ───────────────────────────────────────────────────

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(ALLOY_FURNACE,            AlloyFurnaceRecipeManager.getAllRecipes());
        registration.addRecipes(MANUAL_MACERATOR,         ManualMaceratorRecipeManager.getAllRecipes());
        registration.addRecipes(MANUAL_CENTRIFUGE,        ManualCentrifugeRecipeManager.getAllRecipes());
        registration.addRecipes(SMELTING,                 SmelterRecipeManager.getAllRecipes());
        registration.addRecipes(BOILER,                   BoilerRecipeManager.getAllRecipes());
        registration.addRecipes(FOUNDRY,                  FoundryRecipeManager.getAllRecipes());
        registration.addRecipes(CHEMICAL_REACTOR,         ChemicalReactorRecipeManager.getAllRecipes());
        registration.addRecipes(ELECTROLYSIS,             ElectrolysisRecipeManager.getAllRecipes());
        registration.addRecipes(FRACTIONAL_DISTILLATION,  FractionalDistillationRecipeManager.getAllRecipes());
        registration.addRecipes(SOLVATION,                SolvationRecipeManager.getAllRecipes());
        registration.addRecipes(FLUID_COLLECTOR,          FluidCollectorRecipeManager.getAllRecipes());
    }

    // ── Catalyst registration ─────────────────────────────────────────────────

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(ALLOY_FURNACE,           OmniTechBlocks.ALLOY_FURNACE.get());
        registration.addRecipeCatalysts(MANUAL_MACERATOR,        OmniTechBlocks.MANUAL_MACERATOR.get());
        registration.addRecipeCatalysts(MANUAL_CENTRIFUGE,       OmniTechBlocks.MANUAL_CENTRIFUGE.get());
        registration.addRecipeCatalysts(SMELTING,                OmniTechBlocks.SMELTER.get());
        registration.addRecipeCatalysts(BOILER,                  OmniTechBlocks.BOILER.get());
        registration.addRecipeCatalysts(FOUNDRY,                 OmniTechBlocks.FOUNDRY.get());
        registration.addRecipeCatalysts(CHEMICAL_REACTOR,        OmniTechBlocks.CHEMICAL_REACTOR.get());
        registration.addRecipeCatalysts(ELECTROLYSIS,            OmniTechBlocks.ELECTROLYSIS_MACHINE.get());
        registration.addRecipeCatalysts(FRACTIONAL_DISTILLATION, OmniTechBlocks.FRACTIONAL_DISTILLER.get());
        registration.addRecipeCatalysts(SOLVATION,               OmniTechBlocks.SOLVATION_MACHINE.get());
        registration.addRecipeCatalysts(FLUID_COLLECTOR,         OmniTechBlocks.FLUID_COLLECTOR.get());
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

        @Override public RecipeType<AlloyFurnaceRecipe> getRecipeType() { return ALLOY_FURNACE; }
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
                        .addItemStack(new ItemStack(inputs.get(i)))
                        .setStandardSlotBackground();
            }
            List<net.minecraft.world.item.Item> outputs = recipe.getOutputs();
            for (int i = 0; i < outputs.size(); i++) {
                int x = 108 + (i % 2) * 18;
                int y = (i / 2) * 18;
                builder.addOutputSlot(x, y)
                        .addItemStack(new ItemStack(outputs.get(i)))
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

        @Override public RecipeType<ManualMaceratorRecipe> getRecipeType() { return MANUAL_MACERATOR; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.manual_macerator"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 60; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ManualMaceratorRecipe recipe, IFocusGroup focuses) {
            if (recipe.getInput() != null)
                builder.addInputSlot(0, 10).addItemStack(new ItemStack(recipe.getInput())).setStandardSlotBackground();

            List<ManualMaceratorRecipe.Output> outputs = recipe.getOutputs();
            for (int i = 0; i < outputs.size() && i < 6; i++) {
                builder.addOutputSlot(108 + (i % 3) * 18, (i / 3) * 18)
                        .addItemStack(new ItemStack(outputs.get(i).item(), outputs.get(i).count()))
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

        @Override public RecipeType<ManualCentrifugeRecipe> getRecipeType() { return MANUAL_CENTRIFUGE; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.manual_centrifuge"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 78; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ManualCentrifugeRecipe recipe, IFocusGroup focuses) {
            if (recipe.getInput() != null)
                builder.addInputSlot(0, 20).addItemStack(new ItemStack(recipe.getInput())).setStandardSlotBackground();

            List<ManualCentrifugeRecipe.Output> outputs = recipe.getOutputs();
            for (int i = 0; i < outputs.size() && i < 9; i++) {
                builder.addOutputSlot(96 + (i % 3) * 18, (i / 3) * 18)
                        .addItemStack(new ItemStack(outputs.get(i).item(), outputs.get(i).count()))
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

        @Override public RecipeType<SmelterRecipe> getRecipeType() { return SMELTING; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.smelting"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SmelterRecipe recipe, IFocusGroup focuses) {
            List<net.minecraft.world.item.Item> ingredients = recipe.getIngredients();
            for (int i = 0; i < ingredients.size() && i < 4; i++) {
                builder.addInputSlot((i % 2) * 18, (i / 2) * 18)
                        .addItemStack(new ItemStack(ingredients.get(i)))
                        .setStandardSlotBackground();
            }
            FluidStack out = recipe.getOutput();
            if (!out.isEmpty()) {
                builder.addOutputSlot(116, 8)
                        .addFluidStack(out.getFluid(), out.getAmount())
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

    // ── Boiler: fluid in → fluid out (+ optional item) + temperature + time ──

    static class BoilerCategory implements IRecipeCategory<BoilerRecipe> {
        private final IDrawable background;
        private final IDrawable icon;
        private final IDrawable arrow;

        BoilerCategory(IGuiHelper gui) {
            this.background = gui.createBlankDrawable(160, 70);
            this.icon       = gui.createDrawableItemLike(OmniTechBlocks.BOILER.get());
            this.arrow      = gui.getRecipeArrow();
        }

        @Override public RecipeType<BoilerRecipe> getRecipeType() { return BOILER; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.boiler"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 70; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BoilerRecipe recipe, IFocusGroup focuses) {
            FluidStack in = recipe.getInputFluidStack();
            if (!in.isEmpty()) {
                builder.addInputSlot(0, 2)
                        .addFluidStack(in.getFluid(), in.getAmount())
                        .setFluidRenderer(in.getAmount(), false, 16, 36);
            }
            FluidStack out = recipe.getOutputFluidStack();
            if (!out.isEmpty()) {
                builder.addOutputSlot(116, 2)
                        .addFluidStack(out.getFluid(), out.getAmount())
                        .setFluidRenderer(out.getAmount(), false, 16, 36);
            }
        }

        @Override
        public void draw(BoilerRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            arrow.draw(graphics, 56, 12);
            graphics.text(font, recipe.getRequiredMinimalTemperature() + "°C", 0, 46, 0x555555, false);
            graphics.text(font, recipe.getProductionTime() + "t", 0, 56, 0x555555, false);
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

        @Override public RecipeType<FoundryRecipe> getRecipeType() { return FOUNDRY; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.foundry"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FoundryRecipe recipe, IFocusGroup focuses) {
            net.minecraft.world.level.material.Fluid inputFluid = recipe.getInputFluid();
            if (inputFluid != null) {
                builder.addInputSlot(0, 2)
                        .addFluidStack(inputFluid, recipe.getInputAmount())
                        .setFluidRenderer(recipe.getInputAmount(), false, 16, 36);
            }
            if (recipe.getTemplateItem() != null)
                builder.addInputSlot(22, 12).addItemStack(new ItemStack(recipe.getTemplateItem())).setStandardSlotBackground();

            if (recipe.getOutputItem() != null)
                builder.addOutputSlot(116, 12).addItemStack(new ItemStack(recipe.getOutputItem())).setOutputSlotBackground();
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

        @Override public RecipeType<ChemicalReactorRecipe> getRecipeType() { return CHEMICAL_REACTOR; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.chemical_reactor"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 80; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ChemicalReactorRecipe recipe, IFocusGroup focuses) {
            FluidStack in1 = recipe.getInput1Fluid();
            if (!in1.isEmpty())
                builder.addInputSlot(0, 0).addFluidStack(in1.getFluid(), in1.getAmount()).setFluidRenderer(in1.getAmount(), false, 16, 36);

            FluidStack in2 = recipe.getInput2Fluid();
            if (!in2.isEmpty())
                builder.addInputSlot(20, 0).addFluidStack(in2.getFluid(), in2.getAmount()).setFluidRenderer(in2.getAmount(), false, 16, 36);

            if (recipe.requiresCatalyst() && recipe.getCatalystItem() != null)
                builder.addInputSlot(40, 12).addItemStack(new ItemStack(recipe.getCatalystItem())).setStandardSlotBackground();

            FluidStack out = recipe.getOutput();
            if (!out.isEmpty())
                builder.addOutputSlot(116, 0).addFluidStack(out.getFluid(), out.getAmount()).setFluidRenderer(out.getAmount(), false, 16, 36);
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

        @Override public RecipeType<ElectrolysisRecipe> getRecipeType() { return ELECTROLYSIS; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.electrolysis"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 90; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ElectrolysisRecipe recipe, IFocusGroup focuses) {
            FluidStack in = recipe.getInputFluid();
            if (!in.isEmpty())
                builder.addInputSlot(0, 0).addFluidStack(in.getFluid(), in.getAmount()).setFluidRenderer(in.getAmount(), false, 16, 36);

            net.minecraft.world.item.Item anode = recipe.getAnodeItem();
            if (anode != null) builder.addInputSlot(20, 10).addItemStack(new ItemStack(anode)).setStandardSlotBackground();

            net.minecraft.world.item.Item cathode = recipe.getCathodeItem();
            if (cathode != null) builder.addInputSlot(20, 30).addItemStack(new ItemStack(cathode)).setStandardSlotBackground();

            FluidStack outAnode = recipe.getOutputAnode();
            if (!outAnode.isEmpty())
                builder.addOutputSlot(108, 0).addFluidStack(outAnode.getFluid(), outAnode.getAmount()).setFluidRenderer(outAnode.getAmount(), false, 16, 36);

            FluidStack outCathode = recipe.getOutputCathode();
            if (!outCathode.isEmpty())
                builder.addOutputSlot(126, 0).addFluidStack(outCathode.getFluid(), outCathode.getAmount()).setFluidRenderer(outCathode.getAmount(), false, 16, 36);

            FluidStack outSolution = recipe.getOutputSolution();
            if (!outSolution.isEmpty())
                builder.addOutputSlot(144, 0).addFluidStack(outSolution.getFluid(), outSolution.getAmount()).setFluidRenderer(outSolution.getAmount(), false, 16, 36);
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

        @Override public RecipeType<FractionalDistillationRecipe> getRecipeType() { return FRACTIONAL_DISTILLATION; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.fractional_distillation"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 80; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FractionalDistillationRecipe recipe, IFocusGroup focuses) {
            net.minecraft.world.level.material.Fluid inputFluid = recipe.getInputFluid();
            if (inputFluid != null)
                builder.addInputSlot(0, 2).addFluidStack(inputFluid, recipe.getInputAmount()).setFluidRenderer(recipe.getInputAmount(), false, 16, 36);

            int count = recipe.getOutputCount();
            for (int i = 0; i < count && i < 5; i++) {
                FluidStack out = recipe.getOutputStack(i);
                if (!out.isEmpty())
                    builder.addOutputSlot(100 + i * 18, 2).addFluidStack(out.getFluid(), out.getAmount()).setFluidRenderer(out.getAmount(), false, 16, 36);
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

        @Override public RecipeType<SolvationRecipe> getRecipeType() { return SOLVATION; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.solvation"); }
        @Override public int getWidth()  { return 160; }
        @Override public int getHeight() { return 65; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SolvationRecipe recipe, IFocusGroup focuses) {
            FluidStack in = recipe.getInputFluid();
            if (!in.isEmpty())
                builder.addInputSlot(0, 2).addFluidStack(in.getFluid(), in.getAmount()).setFluidRenderer(in.getAmount(), false, 16, 36);

            net.minecraft.world.item.Item inputItem = recipe.getInputItem();
            if (inputItem != null)
                builder.addInputSlot(20, 12).addItemStack(new ItemStack(inputItem)).setStandardSlotBackground();

            FluidStack out = recipe.getOutputFluid();
            if (!out.isEmpty())
                builder.addOutputSlot(116, 2).addFluidStack(out.getFluid(), out.getAmount()).setFluidRenderer(out.getAmount(), false, 16, 36);
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

        @Override public RecipeType<FluidCollectorRecipe> getRecipeType() { return FLUID_COLLECTOR; }
        @Override public Component getTitle() { return Component.translatable("jei.omnitech.fluid_collector"); }
        @Override public int getWidth()  { return 120; }
        @Override public int getHeight() { return 50; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, FluidCollectorRecipe recipe, IFocusGroup focuses) {
            net.minecraft.world.level.block.Block block = recipe.getInputBlock();
            if (block != null)
                builder.addInputSlot(0, 10).addItemStack(new ItemStack(block.asItem())).setStandardSlotBackground();

            FluidStack out = recipe.getOutputFluid();
            if (!out.isEmpty())
                builder.addOutputSlot(82, 2).addFluidStack(out.getFluid(), out.getAmount()).setFluidRenderer(out.getAmount(), false, 16, 36);
        }

        @Override
        public void draw(FluidCollectorRecipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 44, 12);
        }
    }
}
