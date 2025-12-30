package com.example.mobcapture.block;

import com.example.mobcapture.blockentity.RoomControllerBlockEntity;
import com.example.mobcapture.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RoomControllerBlock extends Block implements EntityBlock {

    public RoomControllerBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoomControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        if (type == ModBlockEntities.ROOM_CONTROLLER_BE.get()) {
            return (lvl, pos, st, be) -> RoomControllerBlockEntity.tickServer(lvl, pos, st, (RoomControllerBlockEntity) be);
        }
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand,
                                 net.minecraft.world.phys.BlockHitResult hit) {

        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!sp.hasPermissions(2)) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RoomControllerBlockEntity rc)) return InteractionResult.PASS;

        sp.sendSystemMessage(Component.literal(
                "RoomController: id=" + rc.getRoomId()
                        + " | radius=" + rc.getRoomRadius()
                        + " | name=" + rc.getRoomName()
                        + " | color=" + rc.getTitleColorName()
        ));
        return InteractionResult.SUCCESS;
    }
}
