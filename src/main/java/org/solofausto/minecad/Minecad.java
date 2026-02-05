package org.solofausto.minecad;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.solofausto.minecad.blueprint.BlueprintCommands;
import org.solofausto.minecad.item.BlueprintToolItem;
import org.solofausto.minecad.item.LineToolItem;
import org.solofausto.minecad.item.SketchToolItem;

@SuppressWarnings("null")
public class Minecad implements ModInitializer {
        public static final String MOD_ID = "minecad";

        public static final Identifier BLUEPRINT_TOOL_ID = Identifier.of(MOD_ID, "blueprint_tool");
        public static final Item BLUEPRINT_TOOL = Registry.register(
                        Registries.ITEM,
                        BLUEPRINT_TOOL_ID,
                        new BlueprintToolItem(
                                        new Item.Settings()
                                                        .maxCount(1)
                                                        .registryKey(RegistryKey.of(RegistryKeys.ITEM,
                                                                        BLUEPRINT_TOOL_ID))));

        public static final Identifier SKETCH_TOOL_ID = Identifier.of(MOD_ID, "sketch_tool");
        public static final Item SKETCH_TOOL = Registry.register(
                        Registries.ITEM,
                        SKETCH_TOOL_ID,
                        new SketchToolItem(
                                        new Item.Settings()
                                                        .maxCount(1)
                                                        .registryKey(RegistryKey.of(RegistryKeys.ITEM,
                                                                        SKETCH_TOOL_ID))));

        public static final Identifier LINE_TOOL_ID = Identifier.of(MOD_ID, "line_tool");
        public static final Item LINE_TOOL = Registry.register(
                        Registries.ITEM,
                        LINE_TOOL_ID,
                        new LineToolItem(
                                        new Item.Settings()
                                                        .maxCount(1)
                                                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, LINE_TOOL_ID))));

        @Override
        public void onInitialize() {
                BlueprintCommands.register();
        }
}
