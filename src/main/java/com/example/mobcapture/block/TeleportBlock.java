package com.example.mobcapture.block;

import com.example.mobcapture.blockentity.TeleportBlockEntity;
import com.example.mobcapture.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class TeleportBlock extends Block implements EntityBlock {

    private static final int COOLDOWN_TICKS = 10; // 0.5초 (20틱=1초)

    public TeleportBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeleportBlockEntity(pos, state);
    }

    // ✅ 핵심: BE tick 연결 (이게 없으면 파티클 절대 안 나옵니다)
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        if (type == ModBlockEntities.TELEPORT_BLOCK_BE.get()) {
            return (lvl, pos, st, be) -> TeleportBlockEntity.tickServer(lvl, pos, st, (TeleportBlockEntity) be);
        }
        return null;
    }

    // ✅ 플레이어가 "밟았을 때"만 텔포 (몹이 밟아도 반응 X)
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);

        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel server)) return;
        if (!(entity instanceof ServerPlayer sp)) return;

        BlockEntity be = server.getBlockEntity(pos);
        if (!(be instanceof TeleportBlockEntity tp)) return;

        tp.tryTeleport(server, sp, COOLDOWN_TICKS);
    }
}
