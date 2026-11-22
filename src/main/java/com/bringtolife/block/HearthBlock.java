package com.bringtolife.block;

import com.bringtolife.sim.Fuel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HearthBlock extends BlockWithEntity implements BlockEntityProvider {

    public static final BooleanProperty LIT = Properties.LIT;
    public static final IntProperty HEAT = IntProperty.of("heat", 0, 3);

    public HearthBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(LIT, false).with(HEAT, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIT, HEAT);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new HearthBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        return (tickWorld, pos, tickState, blockEntity) ->
                ((HearthBlockEntity) blockEntity).tickServer((ServerWorld) tickWorld, pos, tickState);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        Fuel fuel = fuelFor(stack);
        if (fuel == null) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof HearthBlockEntity hearth)) {
            return ActionResult.PASS;
        }
        hearth.addFuel(fuel);
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_LISTENERS);
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        return ActionResult.SUCCESS;
    }

    public static Fuel fuelFor(ItemStack stack) {
        if (stack.isIn(ItemTags.LOGS)) {
            return Fuel.LOG;
        }
        if (stack.isIn(ItemTags.PLANKS)) {
            return Fuel.PLANK;
        }
        if (stack.isOf(Items.STICK)) {
            return Fuel.STICK;
        }
        return null;
    }
}
