package com.example.mobcapture.events;

import com.example.mobcapture.MobCaptureMod;
import com.example.mobcapture.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MobCaptureMod.MODID)
public class ForgeEvents {

    private static final String TAG_ENTITY_TYPE = "entity_type";

    @SubscribeEvent
    public static void onEntityRightClick(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        InteractionHand hand = event.getHand();

        // 1️⃣ 서버에서만 실행 (중복/크래시 방지)
        if (player.level().isClientSide) return;

        // 2️⃣ OP만 허용
        if (!(player instanceof ServerPlayer sp) || !sp.hasPermissions(2)) {
            return; // 일반 유저는 아무 반응 없음
        }

        // 3️⃣ 캡처알 들고 있는지 확인
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !held.is(ModItems.CAPTURE_EGG.get())) return;

        // 4️⃣ 플레이어는 캡처 금지
        if (target instanceof Player) return;

        // 5️⃣ 이미 몬스터가 들어있는 알이면 중단
        if (held.hasTag() && held.getTag().contains(TAG_ENTITY_TYPE)) {
            sp.displayClientMessage(
                    Component.literal("이 캡처알에는 이미 몬스터가 들어있습니다."),
                    true
            );
            return;
        }

        // 6️⃣ 엔티티 타입 ID 가져오기 (안전)
        ResourceLocation entityId =
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (entityId == null) return;

        // 7️⃣ 알에 저장
        held.getOrCreateTag().putString(TAG_ENTITY_TYPE, entityId.toString());

        // 8️⃣ 몬스터 제거 (서버 안전 방식)
        if (target.isAlive()) {
            target.discard();
        }

        // 9️⃣ 안내 메시지 (OP만)
        sp.displayClientMessage(
                Component.literal("캡처 성공: " + entityId),
                true
        );

        // 🔟 이벤트 소비 (중복 클릭 방지)
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
