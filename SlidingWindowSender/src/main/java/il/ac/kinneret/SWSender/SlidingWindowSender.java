package il.ac.kinneret.SWSender;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author Saleem Yousef
 * @version 1.9
 */
public class SlidingWindowSender {
    private static final int TIMEOUT_MULTIPLIER = 2;
    private static String destinationIP;
    private static int destinationPort;
    private static String filePath;
    private static int packetSize;
    private static int sws; 
    private static int rtt; 
    private static List<Integer> dropList = new ArrayList<>();

    public static void main(String[] args) {
        if (!parseArguments(args)) {
            printUsage();
            return;
        }
        try {
            sendFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean parseArguments(String[] args) {
        int flag = 0;
        for (String arg : args) {
            if (arg.startsWith("-dest=")) {
                destinationIP = arg.substring(6);
                flag++;
            } else if (arg.startsWith("-port=")) {
                destinationPort = Integer.parseInt(arg.substring(6));
                flag++;
            } else if (arg.startsWith("-f=")) {
                filePath = arg.substring(3);
                flag++;
            } else if (arg.startsWith("-packetsize=")) {
                packetSize = Integer.parseInt(arg.substring(12));
                flag++;
            } else if (arg.startsWith("-sws=")) {
                sws = Integer.parseInt(arg.substring(5));
                if (sws > 0) {
                    flag++;
                } else {
                    System.out.println("Error: SWS must be positive.");
                }
            } else if (arg.startsWith("-rtt=")) {
                rtt = Integer.parseInt(arg.substring(5));
                flag++;
            } else if (arg.startsWith("-droplist=")) {
                dropList = parseDropList(arg.substring(10));
            }
        }

        return flag == 6;
    }

    private static List<Integer> parseDropList(String dropListStr) {
        List<Integer> parsedDropList = new ArrayList<>();
        for (String s : dropListStr.split(",")) {
            parsedDropList.add(Integer.parseInt(s));
        }
        return parsedDropList;
    }

    private static void sendFile() throws IOException {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(destinationIP);
        byte[] fileData = Files.readAllBytes(Paths.get(filePath));
        int totalPackets = (int) Math.ceil((double) fileData.length / packetSize);

        int base = 0;
        int nextSeqNum = 0;
        boolean[] ackReceived = new boolean[totalPackets];
        Map<Integer, Long> packetTimers = new HashMap<>();
        Map<Integer, byte[]> sentPackets = new HashMap<>();

        while (base < totalPackets) {
            while (nextSeqNum < base + sws && nextSeqNum < totalPackets) {
                if (!dropList.contains(nextSeqNum)) {
                    byte[] packet = createPacket(fileData, nextSeqNum, packetSize);
                    DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, address, destinationPort);
                    socket.send(datagramPacket);
                    System.out.println("Sent packet " + nextSeqNum);

                    packetTimers.put(nextSeqNum, System.currentTimeMillis());
                    sentPackets.put(nextSeqNum, packet);
                } else {
                    System.out.println("Dropped packet " + nextSeqNum);
                    dropList.remove((Integer) nextSeqNum);
                }
                nextSeqNum++;
            }

            // Wait for ACKs
            try {
                socket.setSoTimeout(rtt);
                DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4);
                socket.receive(ackPacket);
                int ackNumber = byteArrayToInt(ackPacket.getData());
                System.out.println("Received ACK: " + ackNumber);

                if (ackNumber >= base) {
                    for (int i = base; i <= ackNumber; i++) {
                        ackReceived[i] = true;
                    }
                    base = ackNumber + 1; // Slide the window forward
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout! Retransmitting...");
                for (int i = base; i < nextSeqNum; i++) {
                    if (!ackReceived[i] && System.currentTimeMillis() - packetTimers.get(i) > TIMEOUT_MULTIPLIER * rtt) {
                        byte[] packet = sentPackets.get(i);
                        DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, address, destinationPort);
                        socket.send(datagramPacket);
                        System.out.println("Resent packet " + i);

                        packetTimers.put(i, System.currentTimeMillis());
                    }
                }
            }
        }

        // Send final packet
        byte[] finalPacket = createFinalPacket();
        DatagramPacket finalDatagramPacket = new DatagramPacket(finalPacket, finalPacket.length, address, destinationPort);
        socket.send(finalDatagramPacket);
        System.out.println("Sent final packet for " + filePath);
        System.out.println("Received ACK: " + base);
        socket.close();
    }

    private static byte[] createPacket(byte[] fileData, int sequenceNumber, int packetSize) {
        int start = sequenceNumber * packetSize;
        int end = Math.min(start + packetSize, fileData.length);
        byte[] data = Arrays.copyOfRange(fileData, start, end);

        CRC32 crc = new CRC32();
        crc.update(data);

        ByteBuffer buffer = ByteBuffer.allocate(8 + data.length);
        buffer.putInt(sequenceNumber);
        buffer.putInt((int) crc.getValue());
        buffer.put(data);
        return buffer.array();
    }

    private static byte[] createFinalPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(8 + 1);
        buffer.putInt(-1);
        buffer.putInt(-1);
        buffer.put((byte) -1);
        return buffer.array();
    }

    private static int byteArrayToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    private static void printUsage() {
        System.out.println("Syntax: SlidingWindowSender -dest=ip -port=p -f=filename -packetsize=bytes -sws=size -rtt=ms [-droplist=1,2]");
    }
}
