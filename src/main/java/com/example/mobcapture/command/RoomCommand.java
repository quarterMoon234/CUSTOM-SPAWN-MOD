package com.example.mobcapture.command;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import com.example.mobcapture.blockentity.RoomControllerBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
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

                        // ✅ /room name            -> 비활성화
                        // ✅ /room name <text...>  -> 이름 설정
                        .then(Commands.literal("name")
                                .executes(ctx -> name(ctx.getSource(), "")) // 인자 없이 OFF
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

    /* ===================== link ===================== */

    private static int link(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();

            HitResult hit = player.pick(6.0D, 0.0F, false);
            if (!(hit instanceof BlockHitResult bhr)) {
                source.sendFailure(Component.literal("스폰포인트(DungeonSpawner)를 바라보고 /room link 를 사용하세요."));
                return 0;
            }

            BlockPos targetPos = bhr.getBlockPos();
            BlockEntity targetBE = level.getBlockEntity(targetPos);
            if (!(targetBE instanceof DungeonSpawnerBlockEntity)) {
                source.sendFailure(Component.literal("바라보는 블록이 스폰포인트(DungeonSpawner)가 아닙니다."));
                return 0;
            }

            RoomControllerBlockEntity nearest = findNearestController(level, player.blockPosition(), 32);
            if (nearest == null) {
                source.sendFailure(Component.literal("근처에 RoomController가 없습니다. (32블록 이내)"));
                return 0;
            }

            nearest.linkSpawnPoint(targetPos, nearest.getRoomId(), level);

            source.sendSuccess(
                    () -> Component.literal("링크 완료: spawnPoint=" + targetPos + " → roomId=" + nearest.getRoomId()),
                    true
            );
            return 1;

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

            // ✅ 껐으면(빈값) 현재 방 안 플레이어에게 잔상 제거 1회
            if (!rc.isTitleEnabled()) {
                rc.clearTitlesForPlayersInRoom(source.getLevel());
                source.sendSuccess(() -> Component.literal("Room title OFF (비활성화)"), true);
            } else {
                source.sendSuccess(() -> Component.literal("Room name 설정: " + rc.getRoomName()), true);
            }
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
                    "Title color 설정: " + rc.getTitleColorName() +
                            " (예: gold, red, aqua, yellow, dark_red, light_purple, white)"
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
