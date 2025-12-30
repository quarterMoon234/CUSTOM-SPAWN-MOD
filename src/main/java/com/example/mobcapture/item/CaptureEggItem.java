package com.example.mobcapture.item;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;

public class CaptureEggItem extends Item {

    // ✅ 캡처알에 저장해둔 엔티티 타입 키 (당신 프로젝트에서 실제로 쓰는 키와 반드시 같아야 함)
    // 지금까지 코드 흐름상 "entity_type" 을 쓰고 있었으니 그대로 둡니다.
    private static final String TAG_ENTITY_TYPE = "entity_type";

    public CaptureEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        // 서버에서만
        if (level.isClientSide) return InteractionResult.SUCCESS;

        // OP만
        if (!(context.getPlayer() instanceof ServerPlayer sp) || !sp.hasPermissions(2)) {
            return InteractionResult.PASS;
        }

        // 쉬프트 + 스포너에만 적용
        if (!sp.isShiftKeyDown()) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();

        // ✅ 블록 이름이 아니라 BlockEntity 타입으로 판별 (ModBlocks 필드명 의존 제거)
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DungeonSpawnerBlockEntity spawner)) {
            return InteractionResult.PASS;
        }

        // ✅ 캡처알에서 몬스터 타입 가져오기 (BE 메서드 의존 제거)
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
     * ✅ 캡처알 ItemStack NBT에서 entity_type 읽기
     */
    private static String getCapturedTypeId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        CompoundTag tag = stack.getTag();
        if (tag == null) return null;

        if (!tag.contains(TAG_ENTITY_TYPE)) return null;

        String v = tag.getString(TAG_ENTITY_TYPE);
        if (v == null) return null;

        v = v.trim();
        return v.isBlank() ? null : v;
    }
}
