package com.example.mobcapture.blockentity;

import com.example.mobcapture.blockentity.DungeonSpawnerBlockEntity;
import com.example.mobcapture.registry.ModBlockEntities;
import com.example.mobcapture.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class RoomControllerBlockEntity extends BlockEntity {

    private static final String TAG_ROOM_ID = "room_id";
    private static final String TAG_ROOM_RADIUS = "room_radius";
    private static final String TAG_SPAWNPOINTS = "spawnpoints";
    private static final String TAG_SPAWNED = "spawned";
    private static final String TAG_ALIVE_UUIDS = "alive_uuids";
    private static final String TAG_ROOM_NAME = "room_name";
    private static final String TAG_TITLE_COLOR = "title_color";

    private UUID roomId = UUID.randomUUID();
    private int roomRadius = 10;
    private boolean spawned = false;

    // ✅ 기본은 “타이틀 비활성”
    private String roomName = "";
    private String titleColorName = "gold";

    private final Set<Long> spawnPointPositions = new HashSet<>();
    private final Set<UUID> aliveMobs = new HashSet<>();
    private final Set<UUID> playersInside = new HashSet<>();

    /**
     * ✅ 플레이어별 “마지막으로 띄운 지역명” 기억
     * - 같은 지역명으로 연속 진입하면 타이틀 안 띄움
     * - 서버 메모리(저장 X). 서버 재시작 시 초기화되는게 정상.
     */
    private static final Map<UUID, String> LAST_SHOWN_TITLE = new java.util.concurrent.ConcurrentHashMap<>();

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROOM_CONTROLLER_BE.get(), pos, state);
    }

    /* ===================== GET ===================== */

    public UUID getRoomId() {
        return roomId;
    }

    public int getRoomRadius() {
        return roomRadius;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getTitleColorName() {
        return titleColorName;
    }

    /* ===================== SET ===================== */

    public void setRoomRadius(int radius) {
        this.roomRadius = Math.max(1, Math.min(128, radius));
        setChanged();
    }

    /**
     * ✅ 빈 값이면 “타이틀 비활성화”
     * - Unknown Room 같은 강제값 넣지 않음
     */
    public void setRoomName(String name) {
        if (name == null) name = "";
        name = name.trim();

        if (name.isBlank()) {
            this.roomName = "";
            setChanged();
            return;
        }

        if (name.length() > 48) name = name.substring(0, 48);
        this.roomName = name;
        setChanged();
    }

    public void setTitleColorName(String colorName) {
        String normalized = normalizeColorName(colorName);
        if (normalized == null) normalized = "gold";
        this.titleColorName = normalized;
        setChanged();
    }

    /* ===================== LINK ===================== */

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
        List<ServerPlayer> currentPlayers = server.getEntitiesOfClass(ServerPlayer.class, box);

        // (A) 입장 감지(플레이어별)
        Set<UUID> nowInside = new HashSet<>();
        for (ServerPlayer p : currentPlayers) {
            UUID id = p.getUUID();
            nowInside.add(id);

            if (!be.playersInside.contains(id)) {
                // ✅ 이번 틱에 새로 들어온 플레이어
                be.playPortalEnterSound(p);
                be.trySendRoomTitle(p); // ✅ 요구사항 반영된 타이틀 처리
            }
        }
        be.playersInside.clear();
        be.playersInside.addAll(nowInside);

        // (B) 스폰/클리어
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

    /* ===================== SOUND ===================== */

    private void playPortalEnterSound(ServerPlayer p) {
        p.playNotifySound(
                ModSounds.PORTAL_ENTER.get(),
                SoundSource.AMBIENT,
                1.0f,
                1.0f
        );
    }

    /* ===================== TITLE ===================== */

    /**
     * ✅ 핵심 요구사항 구현:
     * - roomName이 비활성이면(빈값) → 아예 타이틀을 “보내지 않음”
     * - 마지막으로 보낸 타이틀과 동일하면 → “보내지 않음”
     * - 다르면 → 타이틀 전송 + LAST_SHOWN_TITLE 갱신
     */
    private void trySendRoomTitle(ServerPlayer p) {

        // 1) 타이틀 기능 자체 비활성이면: 아무것도 하지 않음(전송 X)
        if (roomName == null || roomName.trim().isBlank()) {
            return;
        }

        String nowTitle = roomName.trim();
        UUID pid = p.getUUID();

        // 2) 같은 지역이면 타이틀 전송 안함
        String prevTitle = LAST_SHOWN_TITLE.get(pid);
        if (prevTitle != null && prevTitle.equals(nowTitle)) {
            return;
        }

        // 3) 다른 지역이면 타이틀 전송
        ChatFormatting color = toChatFormatting(titleColorName);
        if (color == null) color = ChatFormatting.GOLD;

        Component title = Component.literal(nowTitle).withStyle(color, ChatFormatting.BOLD);

        int fadeIn = 10;
        int stay = 40;
        int fadeOut = 15;

        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));

        // 4) 마지막 타이틀 갱신
        LAST_SHOWN_TITLE.put(pid, nowTitle);
    }

    private static ChatFormatting toChatFormatting(String colorName) {
        String s = normalizeColorName(colorName);
        if (s == null) return null;

        return switch (s) {
            case "black" -> ChatFormatting.BLACK;
            case "dark_blue" -> ChatFormatting.DARK_BLUE;
            case "dark_green" -> ChatFormatting.DARK_GREEN;
            case "dark_aqua" -> ChatFormatting.DARK_AQUA;
            case "dark_red" -> ChatFormatting.DARK_RED;
            case "dark_purple" -> ChatFormatting.DARK_PURPLE;
            case "gold" -> ChatFormatting.GOLD;
            case "gray" -> ChatFormatting.GRAY;
            case "dark_gray" -> ChatFormatting.DARK_GRAY;
            case "blue" -> ChatFormatting.BLUE;
            case "green" -> ChatFormatting.GREEN;
            case "aqua" -> ChatFormatting.AQUA;
            case "red" -> ChatFormatting.RED;
            case "light_purple" -> ChatFormatting.LIGHT_PURPLE;
            case "yellow" -> ChatFormatting.YELLOW;
            case "white" -> ChatFormatting.WHITE;
            default -> null;
        };
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

    /**
     * ✅ 텔포 블록이 “클리어 됐나?” 물어볼 때 사용
     */
    public boolean isCleared(ServerLevel level) {
        if (!spawned) return false;

        aliveMobs.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });

        return aliveMobs.isEmpty();
    }

    /* ===================== SAVE / LOAD ===================== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putUUID(TAG_ROOM_ID, roomId);
        tag.putInt(TAG_ROOM_RADIUS, roomRadius);
        tag.putBoolean(TAG_SPAWNED, spawned);

        // ✅ roomName은 "" 그대로 저장 (비활성 유지)
        tag.putString(TAG_ROOM_NAME, roomName == null ? "" : roomName);
        tag.putString(TAG_TITLE_COLOR, titleColorName == null ? "gold" : titleColorName);

        ListTag spList = new ListTag();
        for (Long p : spawnPointPositions) spList.add(LongTag.valueOf(p));
        tag.put(TAG_SPAWNPOINTS, spList);

        ListTag alive = new ListTag();
        for (UUID u : aliveMobs) alive.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
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

        // ✅ 비어있으면 그대로 "" 유지 (타이틀 비활성 유지)
        if (roomName == null) roomName = "";

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

        playersInside.clear();
    }

    /* ===================== COLOR UTILS ===================== */

    private static String normalizeColorName(String in) {
        if (in == null) return null;
        String s = in.trim().toLowerCase();

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

}
