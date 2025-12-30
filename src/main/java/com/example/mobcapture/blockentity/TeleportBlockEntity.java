package com.example.mobcapture.blockentity;

import com.example.mobcapture.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TeleportBlockEntity extends BlockEntity {

    private static final String TAG_TARGET = "target";
    private static final String TAG_REQUIRE_CLEAR = "require_clear";
    private static final String TAG_LINKED_CONTROLLER = "linked_controller";

    private BlockPos target;
    private boolean requireClear = false;
    private BlockPos linkedController;

    // ✅ "이전 클리어 상태" 기억 (NBT 저장 불필요: 연출용 런타임 상태)
    private boolean lastClearedState = false;

    public TeleportBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEPORT_BLOCK_BE.get(), pos, state);
    }

    /* ===================== GET / SET ===================== */

    public void setTarget(BlockPos pos) {
        this.target = (pos == null) ? null : pos.immutable();
        setChanged();
        sync();
    }

    public BlockPos getTarget() {
        return target;
    }

    public void setRequireClear(boolean v) {
        this.requireClear = v;
        setChanged();
        sync();
    }

    public boolean isRequireClear() {
        return requireClear;
    }

    // ✅ /room link에서 텔포블록도 같이 연결할 때 쓰는 메서드
    public void setLinkedController(BlockPos pos) {
        this.linkedController = (pos == null) ? null : pos.immutable();
        setChanged();
        sync();
    }

    public BlockPos getLinkedController() {
        return linkedController;
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /* ===================== TICK ===================== */

    public static void tickServer(Level level, BlockPos pos, BlockState state, TeleportBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;

        // ✅ 10틱(0.5초)에 한 번만
        if (server.getGameTime() % 10 != 0) return;

        // ✅ 타겟 좌표가 없으면 연출 의미 없음
        if (be.target == null) return;

        // (1) 클리어 조건이 없는 포탈 => 항상 초록 포탈 이펙트
        if (!be.requireClear) {
            spawnReadyPortalParticles(server, pos);
            // requireClear=false는 상태 전환 연출 대상이 아니므로 lastClearedState는 굳이 건드릴 필요 없음
            return;
        }

        // (2) 클리어 조건이 있는 포탈
        if (be.linkedController == null) {
            // 조건부 포탈인데 컨트롤러가 없으면 판단 불가 => 아무 이펙트도 안 냄
            // (원하면 경고 파티클 넣을 수 있음)
            return;
        }

        BlockEntity controllerBE = server.getBlockEntity(be.linkedController);
        if (!(controllerBE instanceof RoomControllerBlockEntity rc)) return;

        boolean cleared;
        try {
            // ✅ isCleared는 ServerLevel을 받는 것으로 통일
            cleared = rc.isCleared(server);
        } catch (Throwable ignored) {
            return;
        }

        // ✅ 핵심: 상태 변화 감지 (미클리어 -> 클리어 전환 순간 1회)
        if (!cleared) {
            // 아직 클리어 안됨 => 흰 철창 유지
            spawnGateParticles(server, pos);
        } else {
            // 클리어됨 => 초록 포탈
            if (!be.lastClearedState) {
                // ⭐ 방금 막 클리어된 "전환 순간" 1회 연출
                spawnGateBreakParticles(server, pos);
            }
            spawnReadyPortalParticles(server, pos);
        }

        // ✅ 반드시 마지막에 상태 갱신
        be.lastClearedState = cleared;
    }

    /* ===================== PARTICLE ===================== */

    /**
     * ✅ 흰색 철창 기둥 파티클 (높이 5칸)
     */
    private static void spawnGateParticles(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;

        double height = 5.0;
        double step = 0.25;

        for (double y = 0.05; y <= height; y += step) {
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    x,
                    pos.getY() + y,
                    z,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );
        }
    }

    /**
     * ✅ 철창이 "깨지면서 흩뿌려지는" 연출 (클리어 되는 순간 1회)
     * - END_ROD를 여러 개 뿌리고, delta를 줘서 퍼지게 만듦
     * - 필요하면 FIREWORK도 살짝 섞어도 됨
     */
    /**
     * ✅ 철창이 공중으로 흩어지며 깨지는 연출 (높이 5칸)
     */
    private static void spawnGateBreakParticles(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;

        double height = 5.0;
        double step = 0.35;

        var r = level.getRandom();

        for (double y = 0.2; y <= height; y += step) {
            for (int i = 0; i < 6; i++) {

                double vx = (r.nextDouble() - 0.5) * 0.25;
                double vy = 0.15 + r.nextDouble() * 0.25;
                double vz = (r.nextDouble() - 0.5) * 0.25;

                level.sendParticles(
                        ParticleTypes.END_ROD,
                        cx,
                        pos.getY() + y,
                        cz,
                        1,
                        vx, vy, vz,
                        0.01
                );
            }
        }
    }

    /**
     * ✅ “사용 가능” 상태 초록 포탈 이펙트 (범위 5칸, 넓게 퍼짐)
     */
    /**
     * ✅ “사용 가능” 상태 초록 포탈 이펙트 (얇은 기둥형, 범위 5칸)
     */
    private static void spawnReadyPortalParticles(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;

        double height = 5.0;

        net.minecraft.util.RandomSource r = level.getRandom();

        int count = 32; // 🔽 개수도 살짝 줄여서 더 깔끔하게
        for (int i = 0; i < count; i++) {

            // 세로 높이 (5칸)
            double y = pos.getY() + 0.15 + r.nextDouble() * height;

            // 🔥 핵심 수정 포인트
            // 기존: 1.2 ~ 3.0 (너무 넓음)
            // 변경: 0.35 ~ 0.75 (얇은 포탈 기둥)
            double radius = 0.35 + r.nextDouble() * 0.4;

            double angle = r.nextDouble() * Math.PI * 2.0;

            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;

            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    x, y, z,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );
        }
    }

    /* ===================== SAVE / LOAD ===================== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        if (target != null) tag.putLong(TAG_TARGET, target.asLong());
        tag.putBoolean(TAG_REQUIRE_CLEAR, requireClear);
        if (linkedController != null) tag.putLong(TAG_LINKED_CONTROLLER, linkedController.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        target = tag.contains(TAG_TARGET) ? BlockPos.of(tag.getLong(TAG_TARGET)) : null;
        requireClear = tag.getBoolean(TAG_REQUIRE_CLEAR);
        linkedController = tag.contains(TAG_LINKED_CONTROLLER)
                ? BlockPos.of(tag.getLong(TAG_LINKED_CONTROLLER))
                : null;

        // ✅ 재접속 시 "전환 순간" 연출이 갑자기 터지는 걸 막기 위해
        // 기본값을 false로 두되, 다음 tick에서 현재 상태로 정상 갱신되도록 둠.
        lastClearedState = false;
    }

    /* ===================== TELEPORT ===================== */

    public void tryTeleport(ServerLevel level, ServerPlayer player, int cooldownTicks) {

        if (target == null) {
            player.sendSystemMessage(Component.literal("§c텔레포트 좌표가 설정되지 않았습니다."));
            return;
        }

        // 쿨타임
        CompoundTag data = player.getPersistentData();
        long now = level.getGameTime();
        long last = data.getLong("mobcapture_tp_last");

        if (now - last < cooldownTicks) return;
        data.putLong("mobcapture_tp_last", now);

        // 클리어 조건
        if (requireClear) {
            if (linkedController == null) {
                player.sendSystemMessage(Component.literal("§c연결된 컨트롤러가 없습니다."));
                return;
            }

            BlockEntity be = level.getBlockEntity(linkedController);
            if (!(be instanceof RoomControllerBlockEntity rc)) {
                player.sendSystemMessage(Component.literal("§c연결된 컨트롤러를 찾을 수 없습니다."));
                return;
            }

            // ✅ isCleared는 ServerLevel을 받는 것으로 통일
            if (!rc.isCleared(level)) {
                player.sendSystemMessage(Component.literal("§f아직 방을 클리어하지 않았습니다."));
                return;
            }
        }

        player.teleportTo(
                level,
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5,
                player.getYRot(),
                player.getXRot()
        );
    }
}
