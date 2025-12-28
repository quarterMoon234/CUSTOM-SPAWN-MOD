package com.example.mobcapture.blockentity;

import com.example.mobcapture.registry.ModBlockEntities;
import com.example.mobcapture.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RoomControllerBlockEntity extends BlockEntity {

    /* ===================== NBT TAG ===================== */

    private static final String TAG_ROOM_ID = "room_id";
    private static final String TAG_ROOM_RADIUS = "room_radius";
    private static final String TAG_SPAWNPOINTS = "spawnpoints";
    private static final String TAG_SPAWNED = "spawned";
    private static final String TAG_ALIVE_UUIDS = "alive_uuids";
    private static final String TAG_ROOM_NAME = "room_name";
    private static final String TAG_TITLE_COLOR = "title_color"; // ex) "gold", "red", "aqua"

    /* ===================== CONFIG ===================== */

    private UUID roomId = UUID.randomUUID();
    private int roomRadius = 10;
    private boolean spawned = false;

    // 방 이름(타이틀)
    private String roomName = "Unknown Room";

    // 타이틀 색(이름 기반). 기본 gold
    private String titleColorName = "gold";

    /* ===================== RUNTIME STATE ===================== */

    // 연결된 스폰포인트 좌표들 (BlockPos.asLong)
    private final Set<Long> spawnPointPositions = new HashSet<>();

    // 현재 방에서 스폰된 몹 UUID들(퇴장 시 완전 제거용)
    private final Set<UUID> aliveMobs = new HashSet<>();

    // 방 안에 '현재' 들어와 있는 플레이어 UUID (입장 감지용, 저장할 필요 없음)
    private final Set<UUID> playersInside = new HashSet<>();

    /* ===================== CONSTRUCTOR ===================== */

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROOM_CONTROLLER_BE.get(), pos, state);
    }

    /* ===================== GET / SET ===================== */

    public UUID getRoomId() {
        return roomId;
    }

    public int getRoomRadius() {
        return roomRadius;
    }

    public void setRoomRadius(int radius) {
        this.roomRadius = Math.max(1, Math.min(128, radius));
        setChanged();
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String name) {
        if (name == null) name = "";
        name = name.trim();
        if (name.isBlank()) name = "Unknown Room";
        if (name.length() > 48) name = name.substring(0, 48);
        this.roomName = name;
        setChanged();
    }

    public String getTitleColorName() {
        return titleColorName;
    }

    public void setTitleColorName(String colorName) {
        String normalized = normalizeColorName(colorName);
        if (normalized == null) normalized = "gold";
        this.titleColorName = normalized;
        setChanged();
    }

    /* ===================== LINK SPAWN POINT ===================== */

    public void linkSpawnPoint(BlockPos spawnPointPos, UUID roomIdToSet, ServerLevel level) {
        spawnPointPositions.add(spawnPointPos.asLong());

        BlockEntity be = level.getBlockEntity(spawnPointPos);
        if (be instanceof DungeonSpawnerBlockEntity sp) {
            sp.setRoomId(roomIdToSet);
        }

        setChanged();
    }

    /* ===================== TICK ===================== */

    public static void tickServer(Level level, BlockPos pos, BlockState state, RoomControllerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;

        AABB box = new AABB(pos).inflate(be.roomRadius);

        // 현재 방 안 플레이어 목록
        List<ServerPlayer> currentPlayers =
                server.getEntitiesOfClass(ServerPlayer.class, box);

        // (A) 입장 감지: 새로 들어온 플레이어에게만 타이틀 + 포탈 사운드
        Set<UUID> nowInside = new HashSet<>();

        for (ServerPlayer p : currentPlayers) {
            UUID id = p.getUUID();
            nowInside.add(id);

            if (!be.playersInside.contains(id)) {
                // 이번 틱에 새로 입장한 플레이어
                be.playPortalEnterSound(p);
                be.sendRoomTitle(p);
            }
        }

        // 퇴장 반영
        be.playersInside.clear();
        be.playersInside.addAll(nowInside);

        // (B) 스폰 로직 (기존 구조 유지)
        boolean hasPlayer = !currentPlayers.isEmpty();

        if (hasPlayer && !be.spawned) {
            be.spawnWave(server);
            be.spawned = true;
            be.setChanged();
            return;
        }

        if (!hasPlayer && be.spawned) {
            be.clearWave(server);
            be.spawned = false;
            be.setChanged();
        }
    }

    /* ===================== ENTRY SOUND ===================== */

    /**
     * 포탈 이동 느낌 사운드(플레이어 개인 재생)
     * - 주변 다른 플레이어에게는 들리지 않음
     */
    private void playPortalEnterSound(ServerPlayer p) {
        // ✅ RPG 포탈 이동 느낌
        p.playNotifySound(
                ModSounds.PORTAL_ENTER.get(),
                SoundSource.AMBIENT,
                1.0f,
                1.0f
        );

        // 더 “차원 이동” 느낌을 원하면 아래로 바꿔도 됨(약간 길고 묵직):
        // p.playNotifySound(SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    /* ===================== TITLE ===================== */

    private void sendRoomTitle(ServerPlayer p) {
        // 색 변환 (이름 -> §코드)
        String colorCode = toSectionColorCode(titleColorName); // ex) "§6"
        if (colorCode == null) colorCode = "§6"; // gold fallback

        // 굵게 + 색상
        Component title = Component.literal(colorCode + "§l" + roomName);

        // (fadeIn, stay, fadeOut) : 틱 단위 (20틱=1초)
        int fadeIn = 10;   // 0.5초
        int stay = 40;     // 2초
        int fadeOut = 15;  // 0.75초

        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /* ===================== SPAWN / CLEAR ===================== */

    private void spawnWave(ServerLevel level) {
        aliveMobs.clear();

        for (Long packed : spawnPointPositions) {
            BlockPos p = BlockPos.of(packed);
            BlockEntity be = level.getBlockEntity(p);

            if (!(be instanceof DungeonSpawnerBlockEntity sp)) continue;
            if (sp.getRoomId() == null || !sp.getRoomId().equals(roomId)) continue;

            try {
                List<UUID> spawned = sp.spawnOnceCollect(level);
                aliveMobs.addAll(spawned);
            } catch (Throwable ignored) {
            }
        }
    }

    private void clearWave(ServerLevel level) {
        for (UUID id : aliveMobs) {
            try {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            } catch (Throwable ignored) {
            }
        }
        aliveMobs.clear();
    }

    /* ===================== SAVE / LOAD ===================== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putUUID(TAG_ROOM_ID, roomId);
        tag.putInt(TAG_ROOM_RADIUS, roomRadius);
        tag.putBoolean(TAG_SPAWNED, spawned);

        tag.putString(TAG_ROOM_NAME, roomName);
        tag.putString(TAG_TITLE_COLOR, titleColorName);

        // spawnpoints 저장
        ListTag spList = new ListTag();
        for (Long p : spawnPointPositions) {
            spList.add(LongTag.valueOf(p));
        }
        tag.put(TAG_SPAWNPOINTS, spList);

        // alive uuid 저장
        ListTag alive = new ListTag();
        for (UUID u : aliveMobs) {
            alive.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
        }
        tag.put(TAG_ALIVE_UUIDS, alive);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.hasUUID(TAG_ROOM_ID)) roomId = tag.getUUID(TAG_ROOM_ID);
        roomRadius = tag.getInt(TAG_ROOM_RADIUS);
        spawned = tag.getBoolean(TAG_SPAWNED);

        roomName = tag.getString(TAG_ROOM_NAME);
        titleColorName = tag.getString(TAG_TITLE_COLOR);

        if (roomRadius <= 0) roomRadius = 10;
        if (roomName == null || roomName.isBlank()) roomName = "Unknown Room";

        String norm = normalizeColorName(titleColorName);
        titleColorName = (norm == null) ? "gold" : norm;

        spawnPointPositions.clear();
        if (tag.contains(TAG_SPAWNPOINTS)) {
            ListTag list = tag.getList(TAG_SPAWNPOINTS, net.minecraft.nbt.Tag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) {
                spawnPointPositions.add(((LongTag) list.get(i)).getAsLong());
            }
        }

        aliveMobs.clear();
        if (tag.contains(TAG_ALIVE_UUIDS)) {
            ListTag alive = tag.getList(TAG_ALIVE_UUIDS, net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < alive.size(); i++) {
                try {
                    aliveMobs.add(UUID.fromString(alive.getString(i)));
                } catch (Exception ignored) {
                }
            }
        }

        // 런타임 캐시
        playersInside.clear();
    }

    /* ===================== COLOR UTILS ===================== */

    // 허용 색 이름 정규화. 허용: black,dark_blue,dark_green,dark_aqua,dark_red,dark_purple,gold,gray,dark_gray,blue,green,aqua,red,light_purple,yellow,white
    private static String normalizeColorName(String in) {
        if (in == null) return null;
        String s = in.trim().toLowerCase();

        // 자주 쓰는 별칭 지원
        if (s.equals("pink")) s = "light_purple";
        if (s.equals("purple")) s = "dark_purple";
        if (s.equals("orange")) s = "gold";
        if (s.equals("lightgray")) s = "gray";
        if (s.equals("darkgray")) s = "dark_gray";
        if (s.equals("cyan")) s = "aqua";

        return switch (s) {
            case "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
                    "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
                    "light_purple", "yellow", "white" -> s;
            default -> null;
        };
    }

    // 색 이름 -> §코드
    private static String toSectionColorCode(String colorName) {
        String s = normalizeColorName(colorName);
        if (s == null) return null;

        return switch (s) {
            case "black" -> "§0";
            case "dark_blue" -> "§1";
            case "dark_green" -> "§2";
            case "dark_aqua" -> "§3";
            case "dark_red" -> "§4";
            case "dark_purple" -> "§5";
            case "gold" -> "§6";
            case "gray" -> "§7";
            case "dark_gray" -> "§8";
            case "blue" -> "§9";
            case "green" -> "§a";
            case "aqua" -> "§b";
            case "red" -> "§c";
            case "light_purple" -> "§d";
            case "yellow" -> "§e";
            case "white" -> "§f";
            default -> null;
        };
    }
}
