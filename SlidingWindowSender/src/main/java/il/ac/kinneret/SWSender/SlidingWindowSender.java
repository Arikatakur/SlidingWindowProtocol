package il.ac.kinneret.SWSender;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.CRC32;

/**
 * The SlidingWindowSender class implements the sliding window protocol for reliable
 * data transfer over UDP. It reads a file, splits it into packets, and sends them
 * to a specified destination using a sliding window mechanism.
 * This class handles packet loss and retransmission.
 *
 * @version 3.0
 */
public class SlidingWindowSender {
    private static final int rtt_multiplier = 2;
    private static String destinationIP;
    private static int destinationPort;
    private static String filePath;
    private static int packetSize;
    private static int sws;
    private static int rtt;
    private static List<Integer> dropL = new ArrayList<>();

    /**
     * The main method is the entry point of the program. It parses the arguments and initiates the file sending process.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        if (!parseArguments(args)) {
            warningMessage();
            return;
        }

        try {
            sendFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Parses the command line arguments to set the required parameters.
     *
     * @param args Command line arguments
     * @return true if all required arguments are provided, false otherwise
     */
    private static boolean parseArguments(String[] args) {
        int Find_All_The_Condition = 0;
        for (String arg : args) {
            if (arg.startsWith("-dest=")) {
                destinationIP = arg.substring(6);
                Find_All_The_Condition++;
            } else if (arg.startsWith("-port=")) {
                if (isNumeric(arg.substring(6))) {
                    destinationPort = Integer.parseInt(arg.substring(6));
                    Find_All_The_Condition++;
                } else {
                    System.out.println("Error parsing port: For input string: \"" + arg.substring(6) + "\"");
                }

            } else if (arg.startsWith("-f=")) {
                filePath = arg.substring(3);
                Find_All_The_Condition++;
            } else if (arg.startsWith("-packetsize=")) {
                packetSize = Integer.parseInt(arg.substring(12));
                Find_All_The_Condition++;
            } else if (arg.startsWith("-sws=")) {
                sws = Integer.parseInt(arg.substring(5));
                if (sws > 0)
                    Find_All_The_Condition++;
                else
                    System.out.println("Error: SWS must be positive.");

            } else if (arg.startsWith("-rtt=")) {
                rtt = Integer.parseInt(arg.substring(5));
                Find_All_The_Condition++;
            } else if (arg.startsWith("-droplist=")) {
                dropL = ParseDropList(arg.substring(10));
            }
        }

        return Find_All_The_Condition == 6;
    }

    /**
     * Parses the drop list argument to generate a list of integers representing packet indices to drop.
     *
     * @param Substring Comma-separated list of packet indices to drop
     * @return List of integers representing packet indices to drop
     */
    private static List<Integer> ParseDropList(String Substring) {
        ArrayList<Integer> Integer_Substring = new ArrayList<>();
        for (String s : Substring.split(",")) {
            Integer_Substring.add(Integer.parseInt(s));
        }
        return Integer_Substring;
    }

    /**
     * Checks if a given string is numeric.
     *
     * @param str The string to check
     * @return true if the string is numeric, false otherwise
     */
    public static boolean isNumeric(String str)  {
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sends the file using the sliding window protocol.
     *
     * @throws IOException if an I/O error occurs
     */
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
        Set<Integer> initialDrops = new HashSet<>(dropL);

        while (base < totalPackets) {
            while (nextSeqNum < base + sws && nextSeqNum < totalPackets) {
                boolean isInitialDrop = initialDrops.contains(nextSeqNum);
                byte[] packet = createPacket(fileData, nextSeqNum, packetSize, isInitialDrop);
                if (!dropL.contains(nextSeqNum) || isInitialDrop) {
                    DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, address, destinationPort);
                    socket.send(datagramPacket);
                    System.out.println("Sent packet " + nextSeqNum);
                }
                initialDrops.remove(nextSeqNum);
                packetTimers.put(nextSeqNum, System.currentTimeMillis());
                sentPackets.put(nextSeqNum, packet);
                nextSeqNum++;
            }

            try {
                socket.setSoTimeout(rtt);
                DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4);
                socket.receive(ackPacket);
                int ackNumber = byteArrayToInt(ackPacket.getData());
                System.out.println("Received ACK on " + ackNumber);

                if (ackNumber >= base) {
                    for (int i = base; i <= ackNumber; i++) {
                        ackReceived[i] = true;
                    }
                    base = ackNumber + 1;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timed out waiting for ACK");

                for (int i = base; i < nextSeqNum; i++) {
                    if (!ackReceived[i] && System.currentTimeMillis() - packetTimers.get(i) > rtt_multiplier * rtt) {
                        // boolean isInitialDrop = initialDrops.contains(i);
                        byte[] packet = createPacket(fileData, i, packetSize, false);
                        DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, address, destinationPort);
                        socket.send(datagramPacket);
                        System.out.println("Resent packet " + i);
                        packetTimers.put(i, System.currentTimeMillis());
                    }
                }
            }
        }
        SlidingWindowPacket Final_Packet = new SlidingWindowPacket(-1, -1, new byte[]{-1});
        System.out.println("Sent final packet for " + filePath);
        SendPacket(socket, address, Final_Packet);
        System.out.println("Received ACK " + totalPackets);
        socket.close();
    }

    /**
     * Creates a new packet with the specified data and sequence number.
     *
     * @param fileData The file data to be sent
     * @param sequenceNumber The sequence number of the packet
     * @param packetSize The size of the packet
     * @param initialDrop Whether the packet should be initially dropped
     * @return The byte array representing the packet
     */
    private static byte[] createPacket(byte[] fileData, int sequenceNumber, int packetSize, boolean initialDrop) {
        int start = sequenceNumber * packetSize;
        int end = Math.min(start + packetSize, fileData.length);
        byte[] data = Arrays.copyOfRange(fileData, start, end);

        ByteBuffer buffer = ByteBuffer.allocate(8 + data.length);
        buffer.putInt(sequenceNumber);
        if (initialDrop) {
            buffer.putInt(0);
        } else {
            buffer.putInt(calculateCRC32(data));
        }
        buffer.put(data);
        return buffer.array();
    }

    /**
     * Sends a packet using the provided DatagramSocket.
     *
     * @param socket The DatagramSocket to use for sending the packet
     * @param address The destination address
     * @param packet The packet to be sent
     * @throws IOException if an I/O error occurs
     */
    private static void SendPacket(DatagramSocket socket, InetAddress address, SlidingWindowPacket packet) throws IOException {
        byte[] Data = packet.toByteArray();
        DatagramPacket udpPacket = new DatagramPacket(Data, Data.length, address, destinationPort);
        socket.send(udpPacket);
    }

    /**
     * Calculates the CRC32 checksum for the given data.
     *
     * @param data The data to calculate the checksum for
     * @return The CRC32 checksum value
     */
    private static int calculateCRC32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return (int) crc.getValue();
    }


    /**
     * Converts a byte array to an integer.
     *
     * @param bytes The byte array to convert
     * @return The resulting integer
     */
    private static int byteArrayToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getInt();
    }

    /**
     * Prints a warning message about the correct syntax for running the program.
     */
    private static void warningMessage() {
        System.out.println("Syntax: SlidingWindowClient-5785 -dest=ip -port=p -f=filename -packetsize=bytes " +
                "-sws=size -rtt=ms [-droplist=1,2]\n" +
                "-droplist is optional.  Refers to packets that will be sent incorrectly the first " +
                "time.  The list should be comma separated without spaces.  Example: 1,3,4");
    }
}