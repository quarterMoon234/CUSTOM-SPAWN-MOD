package com.example.mobcapture.item;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import com.example.mobcapture.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CaptureEggItem extends Item {

    // 캡처알에 저장되는 엔티티 타입 키(기존 데이터 호환 유지)
    private static final String TAG_ENTITY_TYPE = "entity_type";

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

        // 쉬프트 + 던전 스포너에만 적용
        if (!sp.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        BlockPos pos = context.getClickedPos();

        if (!level.getBlockState(pos).is(ModBlocks.DUNGEON_SPAWNER.get())) {
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DungeonSpawnerBlockEntity spawner)) {
            return InteractionResult.PASS;
        }

        // ✅ 캡처알에서 몬스터 타입 가져오기 (BlockEntity에 의존하지 않음)
        String typeId = getCapturedTypeId(context.getItemInHand());

        if (typeId == null) {
            sp.sendSystemMessage(Component.literal("캡처알이 비어있습니다."));
            return InteractionResult.SUCCESS;
        }

        // 스포너에 등록
        spawner.setEntityTypeId(typeId);
        sp.sendSystemMessage(Component.literal("스포너 등록: " + typeId));

        return InteractionResult.SUCCESS;
    }

    /**
     * ✅ 캡처알(ItemStack) NBT에서 엔티티 타입 읽기
     * - 기존 태그 키("entity_type") 유지해서 이전 월드/아이템과 호환
     */
    private static String getCapturedTypeId(ItemStack captureEgg) {
        if (captureEgg == null || captureEgg.isEmpty()) return null;
        if (!captureEgg.hasTag()) return null;

        CompoundTag tag = captureEgg.getTag();
        if (tag == null || !tag.contains(TAG_ENTITY_TYPE)) return null;

        String v = tag.getString(TAG_ENTITY_TYPE);
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
