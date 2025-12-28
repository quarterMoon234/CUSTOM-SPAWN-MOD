package com.example.mobcapture.command;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class SpawnPointCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnpoint")
                        .requires(s -> s.hasPermission(2))

                        // /spawnpoint hp <value>
                        .then(Commands.literal("hp")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 1000000.0))
                                        .executes(ctx -> setHp(
                                                ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "value")
                                        ))
                                )
                        )

                        // /spawnpoint name <text...>
                        .then(Commands.literal("name")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> setName(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "text")
                                        ))
                                )
                        )
        );
    }

    private static int setHp(CommandSourceStack source, double hp) {
        try {
            DungeonSpawnerBlockEntity spawner = getLookedSpawner(source);
            if (spawner == null) return 0;

            spawner.setCustomMaxHp(hp);

            source.sendSuccess(() -> Component.literal("스폰포인트 HP 설정 완료: " + hp), true);
            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("spawnpoint hp 설정 중 오류가 발생했습니다. (콘솔 확인)"));
            t.printStackTrace();
            return 0;
        }
    }

    private static int setName(CommandSourceStack source, String raw) {
        try {
            DungeonSpawnerBlockEntity spawner = getLookedSpawner(source);
            if (spawner == null) return 0;

            String name = sanitizeName(raw);

            spawner.setCustomMobName(name);

            String msg = (spawner.getCustomMobName() == null)
                    ? "스폰포인트 이름 설정: (default)  ← 이름 초기화됨"
                    : "스폰포인트 이름 설정: " + spawner.getCustomMobName()
                      + "  (색코드: & -> § 자동 변환됨)";

            // 메시지 자체도 색이 보이게 literal 사용
            source.sendSuccess(() -> Component.literal(msg), true);
            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("spawnpoint name 설정 중 오류가 발생했습니다. (콘솔 확인)"));
            t.printStackTrace();
            return 0;
        }
    }

    private static String sanitizeName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        // 줄바꿈 제거(채팅/UI 깨짐 방지)
        s = s.replace("\n", " ").replace("\r", " ");

        // 사용 편의: &를 §로 변환 (예: &d -> §d)
        s = s.replace('&', '§');

        // 너무 길면 제한
        if (s.length() > 64) s = s.substring(0, 64);

        // 공백만이면 빈 문자열로
        if (s.isBlank()) return "";

        return s;
    }

    private static DungeonSpawnerBlockEntity getLookedSpawner(CommandSourceStack source) throws Exception {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult bhr)) {
            source.sendFailure(Component.literal("스폰포인트(DungeonSpawner)를 바라보고 명령어를 사용하세요."));
            return null;
        }

        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DungeonSpawnerBlockEntity spawner)) {
            source.sendFailure(Component.literal("바라보는 블록이 스폰포인트(DungeonSpawner)가 아닙니다."));
            return null;
        }
        return spawner;
    }
}
