package dev.dubhe.map.client.cache;

import io.netty.buffer.Unpooled;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;

@Getter
public class RegionCache {
    private final int x;
    private final int z;
    private final ChunkCache[][] chunks = new ChunkCache[64][64];
    private final List<Integer> colors = new ArrayList<>();

    public RegionCache(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static RegionCache deserializeFromGzip(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(baos.toByteArray()));
        try {
            if (byteBuf.readableBytes() < 12) {
                throw new IOException("Corrupted cache: insufficient header data");
            }

            int x = byteBuf.readInt();
            int z = byteBuf.readInt();
            RegionCache cache = new RegionCache(x, z);

            if (byteBuf.readableBytes() < 4) {
                throw new IOException("Corrupted cache: missing chunk count");
            }
            int chunkCount = byteBuf.readVarInt();
            if (chunkCount < 0 || chunkCount > 4096) {
                throw new IOException("Corrupted cache: invalid chunk count: " + chunkCount);
            }

            for (int i = 0; i < chunkCount; i++) {
                if (byteBuf.readableBytes() < 4) {
                    throw new IOException("Corrupted cache: truncated at chunk " + i);
                }
                int arrLen = byteBuf.readVarInt();
                if (arrLen < 0 || arrLen > 10000) {
                    throw new IOException("Corrupted cache: invalid array length: " + arrLen);
                }
                if (byteBuf.readableBytes() < arrLen * 4) {
                    throw new IOException("Corrupted cache: truncated reading chunk data");
                }
                int[] arr = new int[arrLen];
                for (int j = 0; j < arrLen; j++) {
                    arr[j] = byteBuf.readInt();
                }
                cache.addChunk(ChunkCache.deserialize(cache, arr));
            }

            if (byteBuf.readableBytes() < 4) {
                throw new IOException("Corrupted cache: missing color count");
            }
            int colorCount = byteBuf.readInt();
            if (colorCount < 0 || colorCount > 4096) {
                throw new IOException("Corrupted cache: invalid color count: " + colorCount);
            }
            if (byteBuf.readableBytes() < colorCount * 4) {
                throw new IOException("Corrupted cache: truncated reading color palette");
            }
            for (int i = 0; i < colorCount; i++) {
                cache.addColor(byteBuf.readInt());
            }

            return cache;
        } finally {
            byteBuf.release();
        }
    }

    public void addChunk(ChunkCache chunkCache) {
        chunks[chunkCache.getX()][chunkCache.getZ()] = chunkCache;
    }

    public @Nullable ChunkCache getChunk(short x, short z) {
        return chunks[x][z];
    }

    public void addColor(int color) {
        int index = colors.indexOf(color);
        if (index == -1) {
            colors.add(color);
        }
    }

    public byte[] serializeToGzip() throws IOException {
        FriendlyByteBuf byteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            // Encode position
            byteBuf.writeInt(this.x);
            byteBuf.writeInt(this.z);

            // Encode chunks  - pre-count non-null chunks
            int chunkCount = 0;
            for (ChunkCache[] row : this.chunks) {
                for (ChunkCache chunk : row) {
                    if (chunk != null) chunkCount++;
                }
            }
            byteBuf.writeInt(chunkCount);

            // Encode each chunk's serialized IntArrayTag data
            for (ChunkCache[] row : this.chunks) {
                for (ChunkCache chunk : row) {
                    if (chunk == null) continue;
                    int[] arr = chunk.serialize();
                    byteBuf.writeVarInt(arr.length);
                    for (int val : arr) {
                        byteBuf.writeInt(val);
                    }
                }
            }

            // Encode color palette
            byteBuf.writeVarInt(this.colors.size());
            for (Integer color : this.colors) {
                byteBuf.writeInt(color);
            }

            // Compress with GZIP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                byte[] bytes = new byte[byteBuf.readableBytes()];
                byteBuf.getBytes(byteBuf.readerIndex(), bytes);
                gzip.write(bytes);
            }
            return baos.toByteArray();
        } finally {
            byteBuf.release();
        }
    }
}
