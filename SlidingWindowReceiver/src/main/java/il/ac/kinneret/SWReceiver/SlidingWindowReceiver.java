package il.ac.kinneret.SWReceiver;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Implements the sliding window receiver using UDP.
 * Receives packets, validates CRC, and writes to an output file.
 */
public class SlidingWindowReceiver {
    private static String ip;
    private static int port;
    private static String outfile;
    private static int rws; // Receive Window Size

    private static int temp;

    public static void main(String[] args) {
        if (!parseArguments(args)) {
            System.out.println("Syntax: SlidingWindowReceiver -ip=ip -port=p -outfile=f -rws=r ");
            return;
        }

        try {
            System.out.println("Listening...");
            receiveFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean parseArguments(String[] args) {
        int conditionsMet = 0;
        for (String arg : args) {
            if (arg.startsWith("-ip=")) {
                ip = arg.substring(4);
                conditionsMet++;
                if (!ip.equals("127.0.0.1")) {
                    System.out.println("Error parsing listening address: " + ip + ": Name or service not known");
                    conditionsMet--;
                }
            }
            if (arg.startsWith("-port=")) {
                port = Integer.parseInt(arg.substring(6));
                conditionsMet++;
            }
            if (arg.startsWith("-outfile=")) {
                outfile = arg.substring(9);
                conditionsMet++;
            }
            if (arg.startsWith("-rws=")) {
                rws = Integer.parseInt(arg.substring(5));
                conditionsMet++;
            }
        }

        return conditionsMet == 4;
    }

    private static void receiveFile() throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        byte[] buffer = new byte[1332];
        Map<Integer, byte[]> receivedPackets = new HashMap<>();
        int expectedSeqNum = 0;

        try (FileOutputStream fos = new FileOutputStream(outfile)) {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] packetData = Arrays.copyOf(packet.getData(), packet.getLength());
                int seqNum = PacketUtils.byteArrayToInt(Arrays.copyOfRange(packetData, 0, 4));
                long receivedCRC = PacketUtils.byteArrayToLong(Arrays.copyOfRange(packetData, 4, 8));
                byte[] data = Arrays.copyOfRange(packetData, 8, packet.getLength());
                if(seqNum != -1){
                    System.out.printf("Received Packet %d valid CRC\n", seqNum);
                    temp = seqNum + 1;
                }
                else { 
                    PacketUtils.sendAck(socket, packet.getAddress(), packet.getPort(), expectedSeqNum);
                    System.out.println("File " + outfile +" completed. Received " + temp + " packets");
                    break;
                }

                long calculatedCRC = PacketUtils.calculateCRC(data);

                if (calculatedCRC == receivedCRC) {
                    if (seqNum == expectedSeqNum) {
                        fos.write(data);
                        expectedSeqNum++;
                        while (receivedPackets.containsKey(expectedSeqNum)) {
                            fos.write(receivedPackets.remove(expectedSeqNum));
                            expectedSeqNum++;
                        }
                    } else if (seqNum > expectedSeqNum && seqNum < expectedSeqNum + rws) {
                        receivedPackets.put(seqNum, data);
                    } else {
                        System.out.printf("Recieved Packet %d valid CRC outside receive window.", seqNum);
                    }
                } else {
                    System.out.printf("Recieved Packet %d invalid CRC.", seqNum);
                }

                PacketUtils.sendAck(socket, packet.getAddress(), packet.getPort(), expectedSeqNum - 1);
            }
        }
    }
}
