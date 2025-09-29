package rudp;

import java.io.*;
import java.net.*;
import java.util.*;

public class udpclient3 {

    private static final int BUFFER_SIZE = UdpPacket.DATA_SIZE + 20;
    private static final double ACK_LOSS_PROB = -1;
    

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Cach dung: java rudp.udpclient3 <server_ip> <port> <filename>");
            return;
        }

        String serverIp = args[0];
        int port = Integer.parseInt(args[1]);
        String fileName = args[2];

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverAddress = InetAddress.getByName(serverIp);

            // 1. Gửi request
            UdpPacket requestUdp = new UdpPacket(0, 0, false, false, fileName.getBytes());
            byte[] requestData = requestUdp.toBytes();
            DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, serverAddress, port);
            socket.send(requestPacket);
            System.out.println("Da gui yeu cau cho tep: " + fileName);

            // 2. Nhận dữ liệu
            try (FileOutputStream fos = new FileOutputStream("d:\\receive\\" + fileName)) {
                boolean finished = false;
                // buffer để lưu gói
                Map<Integer, byte[]> buffer = new HashMap<>();
                while (!finished) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    socket.receive(dp);

                    UdpPacket pkt = UdpPacket.fromBytes(dp.getData(), dp.getLength());

                    if (pkt.isFinFlag()) {
                        System.out.println("Receive FIN");
                        finished = true;
                        UdpPacket finAck = new UdpPacket(0, pkt.getSequenceNumber() + 1, true, false, new byte[0]);
                        socket.send(new DatagramPacket(finAck.toBytes(), finAck.toBytes().length, serverAddress, port));
                        System.out.println("Send ACK to FIN.");
                        if(pkt.getAckNumber()==0){
                            int i = 0;
                            while (buffer.containsKey(i)) {
                                fos.write(buffer.get(i));
                                i++;
                            }
                            System.out.println("Da tai xong file");
                            fos.close();
                        }
                        else System.out.println("Stop transfering file");
                        break;
                    }

                    int seq = pkt.getSequenceNumber();
                    byte[] data = pkt.getData();
               
                    if (!buffer.containsKey(seq)) {
                        buffer.put(seq, data);
                        System.out.println("Receive segment #" + seq);
                    }
                    
                    
                    // 3. Gửi ACK (có thể bị "mất")
                    if (Math.random() <= ACK_LOSS_PROB) {
                        System.out.println(">>Lost ACK # " + (seq + 1));
                        continue; // bỏ qua, không gửi ack
                    }

                    UdpPacket ack = new UdpPacket(0, seq + 1, true, false, new byte[0]);
                    byte[] ackData = ack.toBytes();
                    DatagramPacket ackPkt = new DatagramPacket(ackData, ackData.length, serverAddress, port);
                    socket.send(ackPkt);
                    System.out.println("Send ACK = " + (seq + 1));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

