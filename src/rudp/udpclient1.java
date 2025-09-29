package rudp;

import java.io.*;
import java.net.*;

public class udpclient1 {
    private static final int BUFFER_SIZE = UdpPacket.DATA_SIZE + 20;
    private static final int TIMEOUT = 2000; // ms
    private static final float loss_rate=(float) 0.1;

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Cach dung: java rudp.udpclient1 <server_ip> <port> <filename>");
            return;
        }

        String serverIp = args[0];
        int port = Integer.parseInt(args[1]);
        String fileName = args[2];

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT);
            InetAddress serverAddress = InetAddress.getByName(serverIp);

            // 1. Gửi yêu cầu file
            UdpPacket req = new UdpPacket(0, 0, false, false, fileName.getBytes());
            byte[] reqData = req.toBytes();
            socket.send(new DatagramPacket(reqData, reqData.length, serverAddress, port));
            System.out.println("Da gui yeu cau tep: " + fileName);

            try (FileOutputStream fos = new FileOutputStream("d:\\receive\\" + fileName)) {
                int expectedSeq = 0;
                boolean completed = false;

                while (true) {
                    try {
                        byte[] buf = new byte[BUFFER_SIZE];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);

                        UdpPacket udp = UdpPacket.fromBytes(packet.getData(), packet.getLength());

                        // FIN
                        if (udp.isFinFlag()) {
                            System.out.println("Receive FIN from server.");

                            UdpPacket finAck = new UdpPacket(0, udp.getSequenceNumber() + 1, true, false, new byte[0]);
                            byte[] finAckData = finAck.toBytes();
                            socket.send(new DatagramPacket(finAckData, finAckData.length, serverAddress, port));
                            System.out.println("Send ACK to FIN.");completed = true;
                            if(udp.getAckNumber()==0)System.out.println("Da tai xong file");
                            else {
                                System.out.println("Stop transfering file");
                                completed=false;
                            }
                            
                            break;
                        }
                        
                        
                        // Đúng thứ tự
                        if (udp.getSequenceNumber() == expectedSeq) {
                            System.out.println("Receive segment #" + expectedSeq + " (" + udp.getData().length + " bytes)");
                            fos.write(udp.getData());

                            // Gửi ACK
                            UdpPacket ack = new UdpPacket(0, expectedSeq + 1, true, false, new byte[0]);
                            byte[] ackData = ack.toBytes();
                            
                            if (Math.random() <= loss_rate) {
                                System.out.println(">>Lost: ack # " + (expectedSeq+1));
                                expectedSeq++;
                                continue;
                            }
                            
                            socket.send(new DatagramPacket(ackData, ackData.length, serverAddress, port));
                            System.out.println("Send ACK = " + (expectedSeq + 1));

                            expectedSeq++;
                        } else {
                            System.out.println("Nhan sai thu tu (mong doi " + expectedSeq + ", nhan " + udp.getSequenceNumber() + ")");
                            // Gửi lại ACK cho gói đã nhận đúng cuối cùng
                            UdpPacket ack = new UdpPacket(0, expectedSeq, true, false, new byte[0]);
                            byte[] ackData = ack.toBytes();
                            socket.send(new DatagramPacket(ackData, ackData.length, serverAddress, port));
                            System.out.println("Send NACK = "+expectedSeq);
                        }

                    } catch (SocketTimeoutException e) {
                        //System.out.println("Timeout! Khong nhan duoc du lieu tu server.");
                        //break;
                    }
                }

                if (!completed) {
                    System.out.println(">>File '" + fileName + "' chua tai xong.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
