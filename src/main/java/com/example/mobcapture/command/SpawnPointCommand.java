package com.example.mobcapture.command;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;

public class SpawnPointCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spawnpoint")
                        .requires(s -> s.hasPermission(2))

                        .then(Commands.literal("hp")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 1000000.0))
                                        .executes(ctx -> setHp(
                                                ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "value")
                                        ))
                                )
                        )

                        .then(Commands.literal("name")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> setName(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "text")
                                        ))
                                )
                        )

                        // ✅ 간단 NBT: preset / set / clear
                        .then(Commands.literal("nbt")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .executes(ctx -> nbtHelp(ctx.getSource()))
                                        .then(Commands.argument("rest", StringArgumentType.greedyString())
                                                .executes(ctx -> setNbtSimple(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "mode"),
                                                        StringArgumentType.getString(ctx, "rest")
                                                ))
                                        )
                                )
                                .executes(ctx -> nbtHelp(ctx.getSource()))
                        )

                        // ✅ 고급 SNBT는 별도
                        .then(Commands.literal("snbt")
                                .then(Commands.argument("snbt", StringArgumentType.greedyString())
                                        .executes(ctx -> setSnbt(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "snbt")
                                        ))
                                )
                        )
        );
    }

    private static int nbtHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "사용법:\n" +
                        " /spawnpoint nbt iron|diamond|netherite\n" +
                        " /spawnpoint nbt clear\n" +
                        " /spawnpoint nbt set <main> <off> <boots> <legs> <chest> <head>\n" +
                        "   - 아이템ID 또는 none\n" +
                        " 고급:\n" +
                        " /spawnpoint snbt {HandItems:[...],ArmorItems:[...]}"
        ), false);
        return 1;
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

            source.sendSuccess(() -> Component.literal(msg), true);
            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("spawnpoint name 설정 중 오류가 발생했습니다. (콘솔 확인)"));
            t.printStackTrace();
            return 0;
        }
    }

    /**
     * /spawnpoint nbt <mode> <rest...>
     * - mode: iron/diamond/netherite/clear/set
     */
    private static int setNbtSimple(CommandSourceStack source, String modeRaw, String restRaw) {
        try {
            DungeonSpawnerBlockEntity spawner = getLookedSpawner(source);
            if (spawner == null) return 0;

            String mode = (modeRaw == null) ? "" : modeRaw.trim().toLowerCase();

            if (mode.equals("clear")) {
                spawner.setEquipTag(null);
                source.sendSuccess(() -> Component.literal("스폰포인트 장비 설정 초기화 완료"), true);
                return 1;
            }

            if (mode.equals("iron") || mode.equals("diamond") || mode.equals("netherite")) {
                spawner.setEquipTag(buildPreset(mode));
                source.sendSuccess(() -> Component.literal("스폰포인트 장비 프리셋 적용: " + mode), true);
                return 1;
            }

            if (mode.equals("set")) {
                // rest: 6개 토큰(main off boots legs chest head)
                String[] parts = (restRaw == null ? "" : restRaw.trim()).split("\\s+");
                if (parts.length != 6) {
                    source.sendFailure(Component.literal("형식: /spawnpoint nbt set <main> <off> <boots> <legs> <chest> <head> (총 6개)"));
                    return 0;
                }

                ItemStack main = parseStack(parts[0]);
                ItemStack off  = parseStack(parts[1]);
                ItemStack boots= parseStack(parts[2]);
                ItemStack legs = parseStack(parts[3]);
                ItemStack chest= parseStack(parts[4]);
                ItemStack head = parseStack(parts[5]);

                CompoundTag equip = buildEquipTag(main, off, boots, legs, chest, head);
                if (equip.isEmpty()) {
                    spawner.setEquipTag(null);
                    source.sendFailure(Component.literal("설정된 장비가 없습니다. (모두 none이면 초기화 됩니다)"));
                    return 0;
                }

                spawner.setEquipTag(equip);
                source.sendSuccess(() -> Component.literal("스폰포인트 장비 set 적용 완료! (다음 스폰부터)"), true);
                return 1;
            }

            // 알 수 없는 mode
            return nbtHelp(source);

        } catch (Throwable t) {
            source.sendFailure(Component.literal("spawnpoint nbt 처리 중 오류가 발생했습니다. (콘솔 확인)"));
            t.printStackTrace();
            return 0;
        }
    }

    /**
     * 고급 SNBT (기존 방식)
     */
    private static int setSnbt(CommandSourceStack source, String snbtRaw) {
        try {
            DungeonSpawnerBlockEntity spawner = getLookedSpawner(source);
            if (spawner == null) return 0;

            String snbt = (snbtRaw == null) ? "" : snbtRaw.trim();
            if (snbt.length() > 4000) {
                source.sendFailure(Component.literal("NBT 문자열이 너무 깁니다. (최대 4000자)"));
                return 0;
            }

            if (snbt.isBlank() || snbt.equals("{}")) {
                spawner.setEquipTag(null);
                source.sendSuccess(() -> Component.literal("스폰포인트 장비 SNBT 초기화 완료"), true);
                return 1;
            }

            CompoundTag parsed = TagParser.parseTag(snbt);

            CompoundTag equip = new CompoundTag();
            if (parsed.contains("HandItems")) equip.put("HandItems", parsed.get("HandItems"));
            if (parsed.contains("ArmorItems")) equip.put("ArmorItems", parsed.get("ArmorItems"));

            if (equip.isEmpty()) {
                spawner.setEquipTag(null);
                source.sendFailure(Component.literal("장비 관련 키가 없습니다. 허용 키: HandItems, ArmorItems"));
                return 0;
            }

            spawner.setEquipTag(equip);
            source.sendSuccess(() -> Component.literal("스폰포인트 장비 SNBT 설정 완료! (다음 스폰부터 적용)"), true);
            return 1;

        } catch (Throwable t) {
            source.sendFailure(Component.literal("SNBT 파싱 실패: 문법을 확인하세요."));
            t.printStackTrace();
            return 0;
        }
    }

    private static CompoundTag buildPreset(String mode) {
        // 프리셋은 “보이는 장비” 중심으로. (illager 갑옷 렌더 안 보일 수 있음)
        return switch (mode) {
            case "iron" -> buildEquipTag(
                    parseStack("minecraft:iron_sword"),
                    parseStack("minecraft:shield"),
                    parseStack("minecraft:iron_boots"),
                    parseStack("minecraft:iron_leggings"),
                    parseStack("minecraft:iron_chestplate"),
                    parseStack("minecraft:iron_helmet")
            );
            case "diamond" -> buildEquipTag(
                    parseStack("minecraft:diamond_sword"),
                    parseStack("minecraft:shield"),
                    parseStack("minecraft:diamond_boots"),
                    parseStack("minecraft:diamond_leggings"),
                    parseStack("minecraft:diamond_chestplate"),
                    parseStack("minecraft:diamond_helmet")
            );
            case "netherite" -> buildEquipTag(
                    parseStack("minecraft:netherite_sword"),
                    parseStack("minecraft:shield"),
                    parseStack("minecraft:netherite_boots"),
                    parseStack("minecraft:netherite_leggings"),
                    parseStack("minecraft:netherite_chestplate"),
                    parseStack("minecraft:netherite_helmet")
            );
            default -> new CompoundTag();
        };
    }

    private static CompoundTag buildEquipTag(ItemStack main, ItemStack off,
                                             ItemStack boots, ItemStack legs, ItemStack chest, ItemStack head) {
        boolean any = !(main.isEmpty() && off.isEmpty() && boots.isEmpty() && legs.isEmpty() && chest.isEmpty() && head.isEmpty());
        if (!any) return new CompoundTag();

        CompoundTag equip = new CompoundTag();

        // HandItems = 2
        ListTag hand = new ListTag();
        hand.add(toStackTag(main));
        hand.add(toStackTag(off));
        equip.put("HandItems", hand);

        // ArmorItems = 4 (boots, legs, chest, head)
        ListTag armor = new ListTag();
        armor.add(toStackTag(boots));
        armor.add(toStackTag(legs));
        armor.add(toStackTag(chest));
        armor.add(toStackTag(head));
        equip.put("ArmorItems", armor);

        return equip;
    }

    private static CompoundTag toStackTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CompoundTag t = new CompoundTag();
        stack.save(t);
        return t;
    }

    /**
     * "none" 또는 "minecraft:iron_sword" 같은 item id만 지원
     */
    private static ItemStack parseStack(String token) {
        if (token == null) return ItemStack.EMPTY;
        String s = token.trim();
        if (s.isEmpty()) return ItemStack.EMPTY;
        if (s.equalsIgnoreCase("none")) return ItemStack.EMPTY;

        try {
            ResourceLocation rl = new ResourceLocation(s);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null) return ItemStack.EMPTY;
            return new ItemStack(item, 1);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String sanitizeName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replace("\n", " ").replace("\r", " ");
        s = s.replace('&', '§');
        if (s.length() > 64) s = s.substring(0, 64);
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
