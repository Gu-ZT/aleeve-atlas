package dev.dubhe.map.structure;

import dev.dubhe.map.util.NbtValues;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 每维度独立保存结构知识和个人偏好，来源升级不会覆盖收藏。
public final class StructureStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructureStore.class);
    private final Path file;
    private final String dimension;
    private final Map<String, StructureRecord> records = new LinkedHashMap<>();
    private final Set<String> favorites = new HashSet<>();
    private final Set<StructureKind> followed = EnumSet.noneOf(StructureKind.class);
    private boolean dirty;
    private long revision;

    public StructureStore(Path file, String dimension) {
        this.file = file;
        this.dimension = dimension;
        this.load();
    }

    public boolean put(StructureRecord record) {
        if (!this.dimension.equals(record.dimension())) {
            throw new IllegalArgumentException("Wrong structure dimension");
        }
        StructureRecord previous = this.records.get(record.key());
        if (previous != null && previous.source().ordinal() > record.source().ordinal()) {
            return false;
        }
        if (record.equals(previous)) {
            return false;
        }
        this.records.put(record.key(), record);
        this.changed();
        return true;
    }

    public List<StructureRecord> all() {
        return List.copyOf(this.records.values());
    }

    public @Nullable StructureRecord get(String key) {
        return this.records.get(key);
    }

    public List<StructureRecord> search(@Nullable StructureKind kind, boolean favoritesOnly, double x, double z) {
        return this.records.values().stream().filter(r -> kind == null || r.kind() == kind)
                .filter(r -> !favoritesOnly || this.favorite(r.key()))
                .sorted(Comparator.comparingDouble((StructureRecord r) -> r.positionDistanceSquared(x, z))
                        .thenComparing(StructureRecord::key)).toList();
    }

    public boolean favorite(String key) {
        return this.favorites.contains(key);
    }

    public void toggleFavorite(String key) {
        if (this.records.containsKey(key)) {
            if (!this.favorites.remove(key)) {
                this.favorites.add(key);
            }
            this.changed();
        }
    }

    public boolean followed(StructureKind kind) {
        return this.followed.contains(kind);
    }

    public void toggleFollowed(StructureKind kind) {
        if (!this.followed.remove(kind)) {
            this.followed.add(kind);
        }
        this.changed();
    }

    public long revision() {
        return this.revision;
    }

    private void changed() {
        this.dirty = true;
        this.revision++;
    }

    private void load() {
        if (!Files.isRegularFile(this.file)) {
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(this.file, NbtAccounter.create(64 * 1024 * 1024));
            if (NbtValues.getInt(root, "version", -1) != 1 || !this.dimension.equals(NbtValues.getString(root, "dimension", ""))) {
                LOGGER.warn("跳过格式或维度不匹配的结构文件 {}", this.file);
                return;
            }
            ListTag list = root.getList("records", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                try {
                    CompoundTag tag = list.getCompound(i);
                    StructureRecord record = StructureRecord.load(tag);
                    this.put(record);
                    if (NbtValues.getBoolean(tag, "favorite", false)) {
                        this.favorites.add(record.key());
                    }
                } catch (RuntimeException e) {
                    LOGGER.warn("跳过结构文件 {} 中的无效记录 {}", this.file, i, e);
                }
            }
            CompoundTag follows = root.getCompound("followed");
            for (String key : follows.getAllKeys()) {
                StructureKind kind = StructureKind.fromId(ResourceLocation.parse(key));
                if (kind != null && NbtValues.getBoolean(follows, key, false)) {
                    this.followed.add(kind);
                }
            }
            this.dirty = false;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("读取结构文件失败 {}", this.file, e);
        }
    }

    public void flush() {
        if (!this.dirty) {
            return;
        }
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        root.putString("dimension", this.dimension);
        ListTag list = new ListTag();
        for (StructureRecord record : this.records.values()) {
            CompoundTag tag = record.save();
            tag.putBoolean("favorite", this.favorite(record.key()));
            list.add(tag);
        }
        root.put("records", list);
        CompoundTag follows = new CompoundTag();
        for (StructureKind kind : this.followed) {
            follows.putBoolean(kind.structureId().toString(), true);
        }
        root.put("followed", follows);
        Path temporary = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        try {
            Files.createDirectories(this.file.getParent());
            NbtIo.writeCompressed(root, temporary);
            try {
                Files.move(temporary, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
            this.dirty = false;
        } catch (IOException e) {
            LOGGER.warn("保存结构文件失败 {}", this.file, e);
        }
    }
}
