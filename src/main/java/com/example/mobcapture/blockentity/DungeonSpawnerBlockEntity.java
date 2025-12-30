package com.example.mobcapture.blockentity;

import com.example.mobcapture.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class DungeonSpawnerBlockEntity extends BlockEntity {

    private static final String TAG_ENTITY_TYPE = "entity_type";
    private static final String TAG_CUSTOM_HP = "custom_hp";
    private static final String TAG_CUSTOM_NAME = "custom_name";
    private static final String TAG_EQUIP = "equipTag";
    private static final String TAG_ROOM_ID = "roomId";

    private String entityTypeId = null;

    // 방(룸) 전용 설정
    private UUID roomId = null;

    // 스폰포인트 커스텀
    private double customMaxHp = -1.0;
    private String customMobName = null;

    // 장비 태그(HandItems/ArmorItems만)
    private CompoundTag equipTag = null;

    // “큰 몹 끼임 방지”를 위한 기본 값
    private static final int BASE_Y_OFFSET = 2;
    private static final int MAX_UP_SCAN = 8;

    public DungeonSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUNGEON_SPAWNER_BE.get(), pos, state);
    }

    /* ===================== getters/setters ===================== */

    public void setRoomId(UUID id) {
        this.roomId = id;
        setChanged();
    }

    public UUID getRoomId() {
        return roomId;
    }

    public void setEntityTypeId(String typeId) {
        this.entityTypeId = (typeId == null || typeId.isBlank()) ? null : typeId.trim();
        setChanged();
    }

    public String getEntityTypeId() {
        return entityTypeId;
    }

    public void setCustomMaxHp(double hp) {
        this.customMaxHp = (hp > 0) ? hp : -1.0;
        setChanged();
    }

    public double getCustomMaxHp() {
        return customMaxHp;
    }

    public void setCustomMobName(String name) {
        if (name == null) name = "";
        name = name.trim();
        if (name.isBlank()) this.customMobName = null;
        else this.customMobName = (name.length() > 64) ? name.substring(0, 64) : name;
        setChanged();
    }

    public String getCustomMobName() {
        return customMobName;
    }

    public void setEquipTag(CompoundTag tag) {
        this.equipTag = (tag == null ? null : tag.copy());
        setChanged();
    }

    public CompoundTag getEquipTag() {
        return equipTag;
    }

    public String getInfoText() {
        return "Spawner: " + (entityTypeId == null ? "(none)" : entityTypeId)
                + " | hp=" + (customMaxHp > 0 ? String.valueOf(customMaxHp) : "(default)")
                + " | name=" + (customMobName != null ? ("\"" + customMobName + "\"") : "(default)")
                + " | equip=" + (equipTag != null ? "(set)" : "(none)")
                + " | roomId=" + (roomId != null ? roomId : "(none)");
    }

    /* ===================== spawn once ===================== */

    /**
     * RoomController가 방 입장 시 1회 호출
     */
    public List<UUID> spawnOnceCollect(ServerLevel level) {
        List<UUID> spawned = new ArrayList<>();

        if (entityTypeId == null || entityTypeId.isBlank()) return spawned;

        Optional<EntityType<?>> opt = EntityType.byString(entityTypeId);
        if (opt.isEmpty()) return spawned;

        EntityType<?> type = opt.get();

        // 스폰 위치: 스포너 바로 위(X/Z 고정), Y는 빈 공간 찾기
        BlockPos spawnPos = findSafeSpawnPos(level, this.worldPosition);

        if (spawnPos == null) return spawned;

        double x = spawnPos.getX() + 0.5;
        double y = spawnPos.getY() + 0.15;
        double z = spawnPos.getZ() + 0.5;

        try {
            // 연출
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.5, z, 45, 0.35, 0.45, 0.35, 0.02);
            level.sendParticles(ParticleTypes.PORTAL,         x, y + 0.35, z, 25, 0.35, 0.35, 0.35, 0.02);
            level.sendParticles(ParticleTypes.SMOKE,          x, y + 0.10, z,  8, 0.20, 0.08, 0.20, 0.003);

            Entity e = type.spawn(level, (CompoundTag) null, null, spawnPos, MobSpawnType.SPAWNER, true, false);
            if (e == null) return spawned;

            spawned.add(e.getUUID());

            // 잔상
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.45, z, 18, 0.25, 0.35, 0.25, 0.02);

            // 이름/체력/장비
            applyCustomNameIfNeeded(e);
            applyEquipIfNeeded(e);
            applyCustomHpIfNeeded(e);

            // 살짝 “등장 연출”
            if (e instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 18, 0, false, false));
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));
            }

        } catch (Throwable ignored) {
            // 크래시 금지
        }

        return spawned;
    }

    private BlockPos findSafeSpawnPos(ServerLevel level, BlockPos base) {
        BlockPos start = base.above(BASE_Y_OFFSET);

        for (int up = 0; up <= MAX_UP_SCAN; up++) {
            BlockPos cand = start.above(up);

            boolean bodyClear = level.getBlockState(cand).getCollisionShape(level, cand).isEmpty();
            boolean headClear = level.getBlockState(cand.above()).getCollisionShape(level, cand.above()).isEmpty();

            if (bodyClear && headClear) {
                return cand;
            }
        }

        return null;
    }

    private void applyCustomNameIfNeeded(Entity entity) {
        if (customMobName == null || customMobName.isBlank()) return;

        try {
            entity.setCustomName(Component.literal(customMobName));
            entity.setCustomNameVisible(true);
        } catch (Throwable ignored) {}
    }

    private void applyCustomHpIfNeeded(Entity entity) {
        if (customMaxHp <= 0) return;
        if (!(entity instanceof LivingEntity living)) return;

        try {
            var inst = living.getAttribute(Attributes.MAX_HEALTH);
            if (inst == null) return;
            inst.setBaseValue(customMaxHp);
            living.setHealth((float) customMaxHp);
        } catch (Throwable ignored) {}
    }

    /**
     * 장비는 “슬롯 적용”만 합니다.
     * - 어떤 몹은 갑옷 렌더가 원래 안 보일 수 있음(예: illager 계열)
     * - 그래도 ArmorItems 슬롯 데이터는 들어갑니다.
     */
    private void applyEquipIfNeeded(Entity entity) {
        if (equipTag == null || equipTag.isEmpty()) return;
        if (!(entity instanceof LivingEntity living)) return;

        try {
            // HandItems: [main, off]
            if (equipTag.contains("HandItems", 9)) {
                ListTag list = equipTag.getList("HandItems", 10);
                if (list.size() > 0) living.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.of(list.getCompound(0)));
                if (list.size() > 1) living.setItemSlot(EquipmentSlot.OFFHAND,  ItemStack.of(list.getCompound(1)));
            }

            // ArmorItems: [boots, legs, chest, head]
            if (equipTag.contains("ArmorItems", 9)) {
                ListTag list = equipTag.getList("ArmorItems", 10);
                if (list.size() > 0) living.setItemSlot(EquipmentSlot.FEET,  ItemStack.of(list.getCompound(0)));
                if (list.size() > 1) living.setItemSlot(EquipmentSlot.LEGS,  ItemStack.of(list.getCompound(1)));
                if (list.size() > 2) living.setItemSlot(EquipmentSlot.CHEST, ItemStack.of(list.getCompound(2)));
                if (list.size() > 3) living.setItemSlot(EquipmentSlot.HEAD,  ItemStack.of(list.getCompound(3)));
            }

        } catch (Throwable ignored) {}
    }

    /* ===================== save/load ===================== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if (entityTypeId != null) tag.putString(TAG_ENTITY_TYPE, entityTypeId);

        if (roomId != null) tag.putUUID(TAG_ROOM_ID, roomId);

        if (customMaxHp > 0) tag.putDouble(TAG_CUSTOM_HP, customMaxHp);

        if (customMobName != null && !customMobName.isBlank()) tag.putString(TAG_CUSTOM_NAME, customMobName);

        if (equipTag != null && !equipTag.isEmpty()) tag.put(TAG_EQUIP, equipTag.copy());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        entityTypeId = tag.contains(TAG_ENTITY_TYPE) ? tag.getString(TAG_ENTITY_TYPE) : null;
        roomId = tag.hasUUID(TAG_ROOM_ID) ? tag.getUUID(TAG_ROOM_ID) : null;

        customMaxHp = tag.contains(TAG_CUSTOM_HP) ? tag.getDouble(TAG_CUSTOM_HP) : -1.0;

        if (tag.contains(TAG_CUSTOM_NAME)) {
            String n = tag.getString(TAG_CUSTOM_NAME);
            customMobName = (n == null || n.isBlank()) ? null : n;
        } else {
            customMobName = null;
        }

        equipTag = tag.contains(TAG_EQUIP) ? tag.getCompound(TAG_EQUIP) : null;

        if (customMaxHp <= 0) customMaxHp = -1.0;
        if (entityTypeId != null && entityTypeId.isBlank()) entityTypeId = null;
        if (customMobName != null && customMobName.isBlank()) customMobName = null;
        if (equipTag != null && equipTag.isEmpty()) equipTag = null;
    }
}
