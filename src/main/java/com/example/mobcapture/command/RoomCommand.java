package com.example.mobcapture.command;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import com.example.mobcapture.blockentity.RoomControllerBlockEntity;
import com.example.mobcapture.blockentity.TeleportBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class RoomCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("room")
                        .requires(s -> s.hasPermission(2))

                        .then(Commands.literal("link")
                                .executes(ctx -> link(ctx.getSource())))

                        .then(Commands.literal("radius")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> radius(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "value")))))

                        // /room name <text...>
                        // ✅ 공백 포함 받기: greedyString
                        .then(Commands.literal("name")
                                .then(Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> name(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text")))))

                        .then(Commands.literal("color")
                                .then(Commands.argument("value", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> color(ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "value")))))

                        .then(Commands.literal("info")
                                .executes(ctx -> info(ctx.getSource())))
        );
    }

    /* ===================== link (범용) ===================== */

    private static int link(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();

            // 1) 플레이어가 바라보는 블록
            HitResult hit = player.pick(6.0D, 0.0F, false);
            if (!(hit instanceof BlockHitResult bhr)) {
                source.sendFailure(Component.literal("블록을 바라보고 /room link 를 사용하세요."));
                return 0;
            }

            BlockPos targetPos = bhr.getBlockPos();
            BlockEntity targetBE = level.getBlockEntity(targetPos);
            if (targetBE == null) {
                source.sendFailure(Component.literal("바라보는 위치에 BlockEntity가 없습니다."));
                return 0;
            }

            // 2) 근처 컨트롤러 찾기(32)
            RoomControllerBlockEntity nearest = findNearestController(level, player.blockPosition(), 32);
            if (nearest == null) {
                source.sendFailure(Component.literal("근처에 RoomController가 없습니다. (32블록 이내)"));
                return 0;
            }

            // 3) 타입별 링크 처리
            if (targetBE instanceof DungeonSpawnerBlockEntity) {
                nearest.linkSpawnPoint(targetPos, nearest.getRoomId(), level);
                source.sendSuccess(() -> Component.literal("링크 완료(SpawnPoint): " + targetPos + " → roomId=" + nearest.getRoomId()), true);
                return 1;
            }

            if (targetBE instanceof TeleportBlockEntity tp) {
                // ✅ 텔포블록에 컨트롤러 좌표 저장
                tp.setLinkedController(nearest.getBlockPos());

                // 저장/클라 갱신 확실히
                level.sendBlockUpdated(targetPos, level.getBlockState(targetPos), level.getBlockState(targetPos), 3);

                source.sendSuccess(() -> Component.literal("링크 완료(TeleportBlock): " + targetPos + " → controllerPos=" + nearest.getBlockPos()), true);
                return 1;
            }

            source.sendFailure(Component.literal("바라보는 블록이 SpawnPoint(DungeonSpawner) 또는 TeleportBlock이 아닙니다."));
            return 0;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("링크 중 오류 발생 (콘솔 확인)"));
            t.printStackTrace();
            return 0;
        }
    }

    /* ===================== radius ===================== */

    private static int radius(CommandSourceStack source, int value) {
        try {
            RoomControllerBlockEntity rc = getLookedRoomController(source);
            if (rc == null) return 0;

            rc.setRoomRadius(value);
            source.sendSuccess(() -> Component.literal("RoomController radius 설정: " + value), true);
            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("radius 설정 중 오류 발생"));
            t.printStackTrace();
            return 0;
        }
    }

    /* ===================== name ===================== */

    private static int name(CommandSourceStack source, String text) {
        try {
            RoomControllerBlockEntity rc = getLookedRoomController(source);
            if (rc == null) return 0;

            rc.setRoomName(text);
            source.sendSuccess(() -> Component.literal("Room name 설정: " + (rc.getRoomName().isBlank() ? "(OFF)" : rc.getRoomName())), true);
            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("name 설정 중 오류 발생"));
            t.printStackTrace();
            return 0;
        }
    }

    /* ===================== color ===================== */

    private static int color(CommandSourceStack source, String value) {
        try {
            RoomControllerBlockEntity rc = getLookedRoomController(source);
            if (rc == null) return 0;

            rc.setTitleColorName(value);
            source.sendSuccess(() -> Component.literal(
                    "Title color 설정: " + rc.getTitleColorName()
                            + " (예: gold, red, aqua, yellow, dark_red, light_purple, white)"
            ), true);

            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("color 설정 중 오류 발생"));
            t.printStackTrace();
            return 0;
        }
    }

    /* ===================== info ===================== */

    private static int info(CommandSourceStack source) {
        try {
            RoomControllerBlockEntity rc = getLookedRoomController(source);
            if (rc == null) return 0;

            source.sendSuccess(() -> Component.literal(
                    "RoomController: id=" + rc.getRoomId()
                            + " | radius=" + rc.getRoomRadius()
                            + " | name=" + (rc.getRoomName().isBlank() ? "(OFF)" : rc.getRoomName())
                            + " | color=" + rc.getTitleColorName()
            ), false);

            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("info 조회 중 오류 발생"));
            t.printStackTrace();
            return 0;
        }
    }

    /* ===================== helpers ===================== */

    private static RoomControllerBlockEntity getLookedRoomController(CommandSourceStack source) throws Exception {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult bhr)) {
            source.sendFailure(Component.literal("RoomController를 바라보고 명령어를 사용하세요."));
            return null;
        }

        BlockEntity be = level.getBlockEntity(bhr.getBlockPos());
        if (!(be instanceof RoomControllerBlockEntity rc)) {
            source.sendFailure(Component.literal("바라보는 블록이 RoomController가 아닙니다."));
            return null;
        }
        return rc;
    }

    private static RoomControllerBlockEntity findNearestController(ServerLevel level, BlockPos center, int r) {
        RoomControllerBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.offset(x, y, z);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof RoomControllerBlockEntity rc) {
                        double d = p.distSqr(center);
                        if (d < bestDist) {
                            bestDist = d;
                            best = rc;
                        }
                    }
                }
            }
        }
        return best;
    }
}
