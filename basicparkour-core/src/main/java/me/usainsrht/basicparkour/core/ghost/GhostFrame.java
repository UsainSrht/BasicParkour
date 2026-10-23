package me.usainsrht.basicparkour.core.ghost;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * An immutable snapshot of a player's position at a point in time.
 * Used by {@link GhostRecorderImpl} to record movement paths.
 *
 * @param x         world X coordinate
 * @param y         world Y coordinate
 * @param z         world Z coordinate
 * @param yaw       player yaw in degrees
 * @param pitch     player pitch in degrees
 * @param timestamp epoch millisecond timestamp of this frame
 */
public record GhostFrame(double x, double y, double z, float yaw, float pitch, long timestamp) {

    public static byte[] serializeList(List<GhostFrame> frames) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzos)) {
            dos.writeInt(frames.size());
            for (GhostFrame f : frames) {
                dos.writeDouble(f.x());
                dos.writeDouble(f.y());
                dos.writeDouble(f.z());
                dos.writeFloat(f.yaw());
                dos.writeFloat(f.pitch());
                dos.writeLong(f.timestamp());
            }
        }
        return baos.toByteArray();
    }

    public static List<GhostFrame> deserializeList(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (GZIPInputStream gzis = new GZIPInputStream(bais);
             DataInputStream dis = new DataInputStream(gzis)) {
            int count = dis.readInt();
            List<GhostFrame> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                double x = dis.readDouble();
                double y = dis.readDouble();
                double z = dis.readDouble();
                float yaw = dis.readFloat();
                float pitch = dis.readFloat();
                long ts = dis.readLong();
                list.add(new GhostFrame(x, y, z, yaw, pitch, ts));
            }
            return list;
        }
    }
}
