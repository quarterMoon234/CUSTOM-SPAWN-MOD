package com.example.mobcapture.blockentity;

import com.example.mobcapture.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DungeonSpawnerBlockEntity extends BlockEntity {

    private static final String TAG_ENTITY_TYPE = "entity_type";

    // ✅ 추가: 커스텀 HP / 커스텀 이름 저장 태그
    private static final String TAG_CUSTOM_HP = "custom_hp";
    private static final String TAG_CUSTOM_NAME = "custom_name";

    // 저장되는 값들
    private String entityTypeId = null;

    // 스포너 설정
    private int delayTicks = 100;
    private int spawnCount = 1;
    private int spawnRange = 4;
    private int activeRange = 12;
    private int maxNearby = 6;

    // 내부 카운터
    private int cooldown = 20;

    // ✅ 스폰포인트 전용 커스텀 최대체력 (-1이면 기본체력 유지)
    private double customMaxHp = -1.0;

    // ✅ 스폰포인트 전용 커스텀 이름 (null/blank면 기본 이름 유지)
    private String customMobName = null;

    // (RoomController 연동을 쓰는 경우 유지)
    private UUID roomId = null;

    public DungeonSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUNGEON_SPAWNER_BE.get(), pos, state);
    }

    // ===== 캡처알에서 타입 읽기 =====
    public static String getCapturedTypeId(net.minecraft.world.item.ItemStack captureEgg) {
        if (captureEgg == null || captureEgg.isEmpty()) return null;
        if (!captureEgg.hasTag()) return null;
        CompoundTag tag = captureEgg.getTag();
        if (tag == null || !tag.contains(TAG_ENTITY_TYPE)) return null;
        String v = tag.getString(TAG_ENTITY_TYPE);
        return (v == null || v.isBlank()) ? null : v;
    }

    // ===== 외부에서 설정 =====
    public void setEntityTypeId(String typeId) {
        this.entityTypeId = typeId;
        this.cooldown = 20;
        setChanged();
    }

    // ✅ 커스텀 HP 설정/조회
    public void setCustomMaxHp(double hp) {
        if (hp <= 0) this.customMaxHp = -1.0; // 0 이하 => 기본 체력 사용
        else this.customMaxHp = hp;
        setChanged();
    }

    public double getCustomMaxHp() {
        return customMaxHp;
    }

    // ✅ 커스텀 이름 설정/조회
    public void setCustomMobName(String name) {
        if (name == null) name = "";
        name = name.trim();
        if (name.isBlank()) this.customMobName = null; // 빈 값이면 기본 이름
        else {
            // 너무 길면 UI 깨질 수 있어서 제한
            if (name.length() > 64) name = name.substring(0, 64);
            this.customMobName = name;
        }
        setChanged();
    }

    public String getCustomMobName() {
        return customMobName;
    }

    public String getInfoText() {
        return "Spawner: " + (entityTypeId == null ? "(none)" : entityTypeId)
                + " | hp=" + (customMaxHp > 0 ? String.valueOf(customMaxHp) : "(default)")
                + " | name=" + (customMobName != null ? ("\"" + customMobName + "\"") : "(default)")
                + " | delay=" + delayTicks + "t"
                + " | count=" + spawnCount
                + " | range=" + spawnRange
                + " | active=" + activeRange
                + " | maxNearby=" + maxNearby;
    }

    // ===== RoomController 연동 =====
    public void setRoomId(UUID id) {
        this.roomId = id;
        setChanged();
    }

    public UUID getRoomId() {
        return roomId;
    }

    public List<UUID> spawnOnceCollect(ServerLevel serverLevel) {
        List<UUID> spawnedIds = new ArrayList<>();

        if (this.entityTypeId == null || this.entityTypeId.isBlank()) return spawnedIds;

        Optional<EntityType<?>> optType = EntityType.byString(this.entityTypeId);
        if (optType.isEmpty()) return spawnedIds;

        EntityType<?> type = optType.get();

        for (int i = 0; i < this.spawnCount; i++) {

            // ===== 스포너 "바로 위" 스폰 위치 (X/Z 고정) + 끼임 방지 =====
            BlockPos base = this.worldPosition;    // ✅ X/Z 고정
            BlockPos spawnPos = base.above(2);     // ✅ 기본 +2 (큰 몹 대비)
            final int MAX_UP = 8;                  // ✅ 위로 더 올려서 빈 공간 찾기(보스 대비)

            for (int up = 0; up <= MAX_UP; up++) {
                BlockPos cand = spawnPos.above(up);

                boolean bodyClear = serverLevel.getBlockState(cand)
                        .getCollisionShape(serverLevel, cand).isEmpty();
                boolean headClear = serverLevel.getBlockState(cand.above())
                        .getCollisionShape(serverLevel, cand.above()).isEmpty();

                if (bodyClear && headClear) {
                    spawnPos = cand;
                    break;
                }
            }

            // ✅ 최종 위치가 막혀 있으면 스폰 스킵(끼임/크래시 방지)
            if (!serverLevel.getBlockState(spawnPos)
                    .getCollisionShape(serverLevel, spawnPos).isEmpty()) {
                continue;
            }

            double x = spawnPos.getX() + 0.5;
            double y = spawnPos.getY() + 0.15;
            double z = spawnPos.getZ() + 0.5;

            try {
                // ===== 차원 균열(보라 계열) 연출 (사운드 없음) =====
                serverLevel.sendParticles(
                        ParticleTypes.REVERSE_PORTAL,
                        x, y + 0.5, z,
                        45,
                        0.35, 0.45, 0.35,
                        0.02
                );

                serverLevel.sendParticles(
                        ParticleTypes.PORTAL,
                        x, y + 0.35, z,
                        25,
                        0.35, 0.35, 0.35,
                        0.02
                );

                serverLevel.sendParticles(
                        ParticleTypes.SMOKE,
                        x, y + 0.1, z,
                        8,
                        0.20, 0.08, 0.20,
                        0.003
                );

                // ===== 실제 스폰 =====
                Entity spawned = type.spawn(
                        serverLevel,
                        (CompoundTag) null,
                        null,
                        spawnPos,
                        MobSpawnType.SPAWNER,
                        true,
                        false
                );

                if (spawned == null) continue;

                spawnedIds.add(spawned.getUUID());

                // ===== 실체화 잔상 =====
                serverLevel.sendParticles(
                        ParticleTypes.REVERSE_PORTAL,
                        x, y + 0.45, z,
                        18,
                        0.25, 0.35, 0.25,
                        0.02
                );

                // ✅ 커스텀 이름(색 포함) + 머리 위 표시
                applyCustomNameIfNeeded(spawned);

                if (spawned instanceof LivingEntity living) {
                    // ✅ 커스텀 HP 적용(체력만 변경)
                    applyCustomHpIfNeeded(living);

                    // 연출(선택)
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 18, 0, false, false));
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));
                }

            } catch (Throwable ignored) {
                // 절대 크래시 금지
            }
        }

        return spawnedIds;
    }

    /**
     * ✅ 색 코드(§) 포함 이름 적용 + 머리 위 표시
     */
    private void applyCustomNameIfNeeded(Entity entity) {
        if (customMobName == null || customMobName.isBlank()) return;

        try {
            // § 색/스타일 코드를 그대로 허용 (예: §d, §5§l ...)
            // Component.literal은 포맷 코드를 렌더링합니다.
            entity.setCustomName(Component.literal(customMobName));
            entity.setCustomNameVisible(true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * ✅ 체력만 바꾸기
     */
    private void applyCustomHpIfNeeded(LivingEntity living) {
        if (customMaxHp <= 0) return;

        try {
            var inst = living.getAttribute(Attributes.MAX_HEALTH);
            if (inst == null) return;

            inst.setBaseValue(customMaxHp);
            living.setHealth((float) customMaxHp); // 등장 시 풀피
        } catch (Throwable ignored) {
        }
    }

    // ===== 틱 로직(필요하면 유지) =====
    public static void tickServer(Level level, BlockPos pos, BlockState state, DungeonSpawnerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.entityTypeId == null || be.entityTypeId.isBlank()) return;

        if (serverLevel.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.activeRange, false) == null) {
            return;
        }

        Optional<EntityType<?>> optType = EntityType.byString(be.entityTypeId);
        if (optType.isEmpty()) return;

        EntityType<?> type = optType.get();

        int nearby = serverLevel.getEntitiesOfClass(
                Entity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(be.spawnRange + 4),
                e -> e.getType() == type
        ).size();
        if (nearby >= be.maxNearby) return;

        be.cooldown--;
        if (be.cooldown > 0) return;

        be.cooldown = be.delayTicks;
        be.setChanged();
    }

    // ===== 저장/로드 =====
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if (entityTypeId != null) tag.putString(TAG_ENTITY_TYPE, entityTypeId);

        tag.putInt("delayTicks", delayTicks);
        tag.putInt("spawnCount", spawnCount);
        tag.putInt("spawnRange", spawnRange);
        tag.putInt("activeRange", activeRange);
        tag.putInt("maxNearby", maxNearby);
        tag.putInt("cooldown", cooldown);

        if (roomId != null) tag.putUUID("roomId", roomId);

        // ✅ 커스텀 HP 저장
        if (customMaxHp > 0) tag.putDouble(TAG_CUSTOM_HP, customMaxHp);

        // ✅ 커스텀 이름 저장
        if (customMobName != null && !customMobName.isBlank()) tag.putString(TAG_CUSTOM_NAME, customMobName);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.contains(TAG_ENTITY_TYPE)) entityTypeId = tag.getString(TAG_ENTITY_TYPE);

        delayTicks = tag.getInt("delayTicks");
        spawnCount = tag.getInt("spawnCount");
        spawnRange = tag.getInt("spawnRange");
        activeRange = tag.getInt("activeRange");
        maxNearby = tag.getInt("maxNearby");
        cooldown = tag.getInt("cooldown");

        if (tag.hasUUID("roomId")) roomId = tag.getUUID("roomId");

        // ✅ 커스텀 HP 로드
        if (tag.contains(TAG_CUSTOM_HP)) customMaxHp = tag.getDouble(TAG_CUSTOM_HP);
        else customMaxHp = -1.0;

        // ✅ 커스텀 이름 로드
        if (tag.contains(TAG_CUSTOM_NAME)) {
            String n = tag.getString(TAG_CUSTOM_NAME);
            customMobName = (n == null || n.isBlank()) ? null : n;
        } else {
            customMobName = null;
        }

        // 안전장치
        if (delayTicks <= 0) delayTicks = 100;
        if (spawnCount <= 0) spawnCount = 1;
        if (spawnRange <= 0) spawnRange = 4;
        if (activeRange <= 0) activeRange = 12;
        if (maxNearby <= 0) maxNearby = 6;
        if (cooldown < 0) cooldown = 20;
        if (customMaxHp <= 0) customMaxHp = -1.0;
    }
}
