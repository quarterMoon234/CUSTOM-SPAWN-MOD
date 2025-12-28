package com.example.mobcapture.blockentity;

import com.example.mobcapture.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DungeonSpawnerBlockEntity extends BlockEntity {

    private static final String TAG_ENTITY_TYPE = "entity_type";
    private static final String TAG_ROOM_ID = "room_id";

    // 몹에게 찍을 태그 키 (PersistentData)
    public static final String PDATA_ROOM = "mobcapture_room";

    private String entityTypeId = null;
    private UUID roomId = null;

    // 스폰포인트 설정
    private int spawnCount = 1;
    private int spawnRange = 0; // 0이면 고정 스폰

    public DungeonSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUNGEON_SPAWNER_BE.get(), pos, state);
    }

    // ===== 캡처알에서 타입 읽기 =====
    public static String getCapturedTypeId(ItemStack captureEgg) {
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
        setChanged();
    }

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
        setChanged();
    }

    public UUID getRoomId() {
        return roomId;
    }

    public void setSpawnCount(int spawnCount) {
        this.spawnCount = Math.max(1, Math.min(64, spawnCount));
        setChanged();
    }

    public void setSpawnRange(int spawnRange) {
        this.spawnRange = Math.max(0, Math.min(32, spawnRange));
        setChanged();
    }

    public String getInfoText() {
        return "SpawnPoint: " + (entityTypeId == null ? "(none)" : entityTypeId)
                + " | count=" + spawnCount
                + " | range=" + spawnRange
                + " | room=" + (roomId == null ? "(unlinked)" : roomId);
    }

    public List<UUID> spawnOnceCollect(ServerLevel serverLevel) {
        List<UUID> spawnedIds = new ArrayList<>();

        if (this.entityTypeId == null || this.entityTypeId.isBlank()) return spawnedIds;

        Optional<EntityType<?>> optType = EntityType.byString(this.entityTypeId);
        if (optType.isEmpty()) return spawnedIds;

        EntityType<?> type = optType.get();

        for (int i = 0; i < this.spawnCount; i++) {
            // ===== 안전 스폰 위치 계산: 큰 몹 대비 =====
            BlockPos base = this.worldPosition.offset(
                    serverLevel.random.nextInt(this.spawnRange * 2 + 1) - this.spawnRange,
                    0,
                    serverLevel.random.nextInt(this.spawnRange * 2 + 1) - this.spawnRange
            );

// 기본은 위로 2칸 (큰 몹 여유)
            BlockPos spawnPos = base.above(2);

// 최대 몇 칸까지 위로 올려서 빈 공간을 찾을지
            final int MAX_UP = 6;

// "발 밑은 단단 + 몸통 공간은 비어있음"을 만족하는 위치 찾기
            for (int up = 0; up <= MAX_UP; up++) {
                BlockPos cand = spawnPos.above(up);

                // 발 아래(바닥)는 막혀 있어야 함(공중 스폰 방지) — 단, 물/용암 위는 막고 싶으면 더 검사 가능
                boolean hasFloor = !serverLevel.getBlockState(cand.below()).isAir();

                // 현재 위치는 비어 있어야 함
                boolean bodyClear = serverLevel.getBlockState(cand).getCollisionShape(serverLevel, cand).isEmpty();

                // 머리 공간도 비어 있어야 함(키 큰 몹 완전 해결은 아니지만 끼임 급감)
                boolean headClear = serverLevel.getBlockState(cand.above()).getCollisionShape(serverLevel, cand.above()).isEmpty();

                if (hasFloor && bodyClear && headClear) {
                    spawnPos = cand;
                    break;
                }
                // 끝까지 못 찾으면 마지막 cand 그대로인데, 아래에서 한 번 더 안전하게 스킵 처리
            }

            // 최종 위치가 여전히 막혀 있으면 이 스폰은 포기
            if (!serverLevel.getBlockState(spawnPos)
                    .getCollisionShape(serverLevel, spawnPos).isEmpty()) {
                continue; // 다음 spawnCount 루프로
            }

            double x = spawnPos.getX() + 0.5;
            double y = spawnPos.getY() + 0.15;
            double z = spawnPos.getZ() + 0.5;

            try {
                /* ================== 1️⃣ 차원 균열 열림 ================== */

                // 보라 계열 핵심 파티클 (차원문 느낌)
                serverLevel.sendParticles(
                        ParticleTypes.REVERSE_PORTAL,
                        x, y + 0.5, z,
                        45,
                        0.35, 0.45, 0.35,
                        0.02
                );

                // 공간이 찢어지는 보조 효과
                serverLevel.sendParticles(
                        ParticleTypes.PORTAL,
                        x, y + 0.35, z,
                        25,
                        0.35, 0.35, 0.35,
                        0.02
                );

                // 아주 약한 연무 (톤 유지용, 싫으면 이 블록 삭제 가능)
                serverLevel.sendParticles(
                        ParticleTypes.SMOKE,
                        x, y + 0.1, z,
                        8,
                        0.20, 0.08, 0.20,
                        0.003
                );

                /* ================== 2️⃣ 실제 스폰 ================== */

                Entity spawned = type.spawn(
                        serverLevel,
                        (CompoundTag) null,
                        null,
                        spawnPos,
                        MobSpawnType.SPAWNER,
                        true,
                        false
                );

                if (spawned != null) {
                    spawnedIds.add(spawned.getUUID());

                    /* ================== 3️⃣ 실체화 잔상 ================== */

                    // 등장 직후 잔상 (같은 보라 계열 유지)
                    serverLevel.sendParticles(
                            ParticleTypes.REVERSE_PORTAL,
                            x, y + 0.45, z,
                            18,
                            0.25, 0.35, 0.25,
                            0.02
                    );

                    if (spawned instanceof LivingEntity living) {
                        // 잠깐 빛남 → “차원을 넘어왔다” 강조
                        living.addEffect(new MobEffectInstance(
                                MobEffects.GLOWING,
                                18,
                                0,
                                false,
                                false
                        ));

                        // 아주 짧은 실체화 딜레이
                        living.addEffect(new MobEffectInstance(
                                MobEffects.MOVEMENT_SLOWDOWN,
                                10,
                                2,
                                false,
                                false
                        ));
                    }
                }

            } catch (Throwable ignored) {
                // 크래시 방지
            }
        }

        return spawnedIds;
    }

    // ===== 저장/로드 =====
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if (entityTypeId != null) tag.putString(TAG_ENTITY_TYPE, entityTypeId);
        if (roomId != null) tag.putUUID(TAG_ROOM_ID, roomId);

        tag.putInt("spawnCount", spawnCount);
        tag.putInt("spawnRange", spawnRange);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.contains(TAG_ENTITY_TYPE)) entityTypeId = tag.getString(TAG_ENTITY_TYPE);
        if (tag.hasUUID(TAG_ROOM_ID)) roomId = tag.getUUID(TAG_ROOM_ID);

        spawnCount = tag.getInt("spawnCount");
        spawnRange = tag.getInt("spawnRange");

        if (spawnCount <= 0) spawnCount = 1;
        if (spawnRange < 0) spawnRange = 0;
    }
}
