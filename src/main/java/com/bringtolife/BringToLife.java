package com.bringtolife;

import com.bringtolife.block.HearthBlock;
import com.bringtolife.block.HearthBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class BringToLife implements ModInitializer {

    public static final String MOD_ID = "bringtolife";

    public static final Block HEARTH = new HearthBlock(AbstractBlock.Settings.create()
            .strength(2.0F)
            .luminance(state -> state.get(HearthBlock.LIT) ? 13 : 0));

    public static final BlockItem HEARTH_ITEM = new BlockItem(HEARTH, new Item.Settings());

    public static BlockEntityType<HearthBlockEntity> HEARTH_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "hearth"), HEARTH);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "hearth"), HEARTH_ITEM);
        HEARTH_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "hearth"),
                BlockEntityType.Builder.create(HearthBlockEntity::new, HEARTH).build(null)
        );
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(HEARTH_ITEM));
    }
}
