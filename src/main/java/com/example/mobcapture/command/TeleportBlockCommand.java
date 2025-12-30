package com.example.mobcapture.command;

import com.example.mobcapture.blockentity.TeleportBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
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

public class TeleportBlockCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("teleportblock")
                        .requires(s -> s.hasPermission(2))

                        // /teleportblock set <x> <y> <z>
                        .then(Commands.literal("set")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> setTarget(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z")
                                                        ))
                                                )
                                        )
                                )
                        )

                        // /teleportblock requireClear <true|false>
                        .then(Commands.literal("requireClear")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setRequireClear(
                                                ctx.getSource(),
                                                BoolArgumentType.getBool(ctx, "value")
                                        ))
                                )
                        )

                        // /teleportblock info
                        .then(Commands.literal("info")
                                .executes(ctx -> info(ctx.getSource())))
        );
    }

    private static int setTarget(CommandSourceStack source, int x, int y, int z) {
        try {
            TeleportBlockEntity tp = getLookedTeleportBlock(source);
            if (tp == null) return 0;

            BlockPos target = new BlockPos(x, y, z);
            tp.setTarget(target); // ✅ 여기 수정 포인트 (level 인자 제거)

            // ✅ 월드에 변경사항 반영(저장/클라 갱신용)
            markUpdated(source.getLevel(), tp.getBlockPos());

            source.sendSuccess(() -> Component.literal("§a텔레포트 좌표 설정 완료: " + target), true);
            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("§c텔레포트 좌표 설정 중 오류 발생 (콘솔 확인)"));
            t.printStackTrace();
            return 0;
        }
    }

    private static int setRequireClear(CommandSourceStack source, boolean v) {
        try {
            TeleportBlockEntity tp = getLookedTeleportBlock(source);
            if (tp == null) return 0;

            tp.setRequireClear(v);

            // ✅ 월드에 변경사항 반영(저장/클라 갱신용)
            markUpdated(source.getLevel(), tp.getBlockPos());

            source.sendSuccess(() -> Component.literal("§e클리어 필요 여부: " + v), true);
            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("§crequireClear 설정 중 오류 발생 (콘솔 확인)"));
            t.printStackTrace();
            return 0;
        }
    }

    private static int info(CommandSourceStack source) {
        try {
            TeleportBlockEntity tp = getLookedTeleportBlock(source);
            if (tp == null) return 0;

            BlockPos target = tp.getTarget();
            boolean require = tp.isRequireClear();

            source.sendSuccess(() -> Component.literal(
                    "TeleportBlock: target=" + (target == null ? "(none)" : target)
                            + " | requireClear=" + require
            ), false);

            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("§cinfo 조회 중 오류 발생 (콘솔 확인)"));
            t.printStackTrace();
            return 0;
        }
    }

    private static TeleportBlockEntity getLookedTeleportBlock(CommandSourceStack source) throws Exception {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult bhr)) {
            source.sendFailure(Component.literal("§cTeleportBlock을 바라보고 명령어를 사용하세요."));
            return null;
        }

        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof TeleportBlockEntity tp)) {
            source.sendFailure(Component.literal("§c바라보는 블록이 TeleportBlock이 아닙니다."));
            return null;
        }
        return tp;
    }

    private static void markUpdated(ServerLevel level, BlockPos pos) {
        // ✅ setChanged는 BE 내부에서 하지만, 이건 “블록 업데이트”를 발생시켜
        // 저장/클라 동기화가 확실히 되도록 도와줌
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }
}
