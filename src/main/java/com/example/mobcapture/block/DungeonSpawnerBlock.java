package com.example.mobcapture.block;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DungeonSpawnerBlock extends Block implements EntityBlock {

    public DungeonSpawnerBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DungeonSpawnerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand,
                                 net.minecraft.world.phys.BlockHitResult hit) {

        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!sp.hasPermissions(2)) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DungeonSpawnerBlockEntity spawner)) return InteractionResult.PASS;

        sp.sendSystemMessage(Component.literal(spawner.getInfoText()));
        return InteractionResult.SUCCESS;
    }
}

