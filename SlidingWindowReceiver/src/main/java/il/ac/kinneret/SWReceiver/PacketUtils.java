package il.ac.kinneret.SWReceiver;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;


public class PacketUtils {

    /**
     *
     * @param socket The DatagramSocket to use for sending the acknowledgment
     * @param address The destination address
     * @param port The destination port
     * @param seqNum The sequence number to acknowledge
     * @throws IOException if an I/O error occurs
     */
    public static void sendAck(DatagramSocket socket, InetAddress address, int port, int seqNum) throws IOException {
        byte[] ack = intToByteArray(seqNum);
        DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, address, port);
        socket.send(ackPacket);
        System.out.println("Acked: " + seqNum);
    }

    /**
     * Calculates the CRC32 for the given data.
     *
     * @param data The data to calculate the checksum for
     * @return The CRC32 checksum value
     */
    public static long calculateCRC(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    /**
     * Converts a byte array to an integer.
     *
     * @param bytes The byte array to convert
     * @return The resulting integer
     */
    public static int byteArrayToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getInt();
    }

    /**
     * Converts an integer to a byte array.
     *
     * @param value The integer to convert
     * @return The resulting byte array
     */
    public static byte[] intToByteArray(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /**
     * Converts a byte array to a long.
     *
     * @param bytes The byte array to convert
     * @return The resulting long value
     */
    public static long byteArrayToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getInt() & 0xffffffffL;
    }
}
