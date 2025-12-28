package com.example.mobcapture.item;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import com.example.mobcapture.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CaptureEggItem extends Item {

    public CaptureEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        // 서버에서만
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // OP만
        if (!(context.getPlayer() instanceof ServerPlayer sp) || !sp.hasPermissions(2)) {
            return InteractionResult.PASS;
        }

        // 쉬프트 + 던전 스포너 블록인지 확인
        if (!sp.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        // 던전 스포너 블록인지 확인
        if (!level.getBlockState(context.getClickedPos()).is(ModBlocks.DUNGEON_SPAWNER.get())) {
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (!(be instanceof DungeonSpawnerBlockEntity spawner)) {
            return InteractionResult.PASS;
        }

        // 캡처알에서 몬스터 타입 가져오기
        String typeId = DungeonSpawnerBlockEntity.getCapturedTypeId(context.getItemInHand());

        if (typeId == null) {
            sp.sendSystemMessage(Component.literal("캡처알이 비어있습니다."));
            return InteractionResult.SUCCESS;
        }

        // 스포너에 등록
        spawner.setEntityTypeId(typeId);
        sp.sendSystemMessage(Component.literal("스포너 등록: " + typeId));

        return InteractionResult.SUCCESS;
    }
}
