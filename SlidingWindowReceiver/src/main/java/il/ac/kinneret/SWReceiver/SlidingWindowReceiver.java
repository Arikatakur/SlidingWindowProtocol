package il.ac.kinneret.SWReceiver;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Implements the sliding window receiver using UDP.
 * Receives packets, validates CRC, and writes to an output file.
 * 
 * @version 3.0
 */
public class SlidingWindowReceiver {
    private static String ip;
    private static int port;
    private static String outfile;
    private static int rws; // Receive Window Size

    private static int temp;
    /**
     * Main entry point of the program.
     * Parses the command-line arguments and starts receiving files.
     * 
     * @param args Command-line arguments.
     * @throws IOException If an I/O error occurs.
     */
    public static void main(String[] args) throws IOException{
        if (!parseArguments(args)) {
            System.out.println("Syntax: SlidingWindowReceiver-5785 -ip=ip -port=p -outfile=f -rws=r");
            return;
        }

        try {
            System.out.println("Listening...");
            receiveFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses the command-line arguments and sets the required parameters.
     * Ensures the arguments are valid and assigns them to the corresponding variables.
     * 
     * @param args Command-line arguments.
     * @return true if arguments are valid, false otherwise.
     */

    private static boolean parseArguments(String[] args) {
        int flag = 0;
        for (String arg : args) {
            if (arg.startsWith("-ip=")) {
                ip = arg.substring(4);
                flag++;
                if (!ip.equals("127.0.0.1")) {
                    System.out.println("Error parsing listening address: " + ip + ": Name or service not known");
                    flag--;
                }
            }
            if (arg.startsWith("-port=")) {
                port = Integer.parseInt(arg.substring(6));
                flag++;
            }
            if (arg.startsWith("-outfile=")) {
                outfile = arg.substring(9);
                flag++;
            }
            if (arg.startsWith("-rws=")) {
                rws = Integer.parseInt(arg.substring(5));
                flag++;
                if (rws < 0) {
                    System.out.println("Error: RWS must be positive");
                    flag--;
                }
            }
        }

        return flag == 4;
    }

    /**
     * Listens on the specified port and receives packets using UDP.
     * Validates CRC, writes data to the output file, and handles sliding window logic.
     * 
     * @throws IOException If an I/O error occurs while receiving packets or writing to file.
     */

    private static void receiveFile() throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        byte[] buffer = new byte[port];
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
                    //System.out.printf("Received packet %d valid CRC\n", seqNum);
                    temp = seqNum + 1;
                }
                else { 
                    PacketUtils.sendAck(socket, packet.getAddress(), packet.getPort(), expectedSeqNum);
                    System.out.println("File " + outfile +" completed.  Received " + temp + " packets");
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
                        System.out.printf("Received packet %d valid CRC\n", seqNum);
                    } else if (seqNum > expectedSeqNum && seqNum < expectedSeqNum + rws) {
                        if (!receivedPackets.containsKey(seqNum)) {
                            receivedPackets.put(seqNum, data);
                            System.out.printf("Received packet %d valid CRC\n", seqNum);
                        }
                    } else {
                        System.out.printf("Received packet %d valid CRC outside receive window.\n", seqNum);
                    }
                } else {
                    System.out.printf("Received packet %d invalid CRC\n", seqNum);
                }
                
                PacketUtils.sendAck(socket, packet.getAddress(), packet.getPort(), expectedSeqNum - 1);
            }
        }
    }
}