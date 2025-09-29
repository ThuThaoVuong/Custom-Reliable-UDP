package rudp;

import java.io.*;
import java.net.*;
import java.util.Arrays;

public class udpserver1 {
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT = 2000; // ms
    private static final int MAX_RETRY = 3;
    private static final int loss_rate=10;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Cach dung: java rudp.udpServer1 <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        try (DatagramSocket socket = new DatagramSocket(port)) {
            
            System.out.println("Server STOP AND WAIT dang lang nghe tren cong " + port + "...");

            while (true) {
                // 1. Nhận yêu cầu từ client
                byte[] buf = new byte[BUFFER_SIZE];
                DatagramPacket reqPacket = new DatagramPacket(buf, buf.length);
                try{
                    socket.receive(reqPacket);
                } catch(Exception e){
                    
                }

                UdpPacket req = UdpPacket.fromBytes(reqPacket.getData(), reqPacket.getLength());
                String fileName = new String(req.getData()).trim();
                if(fileName.isEmpty()) continue;
                InetAddress clientAddress = reqPacket.getAddress();
                int clientPort = reqPacket.getPort();

                System.out.println("Nhan yeu cau cho tep '" + fileName + "' tu " + clientAddress + ":" + clientPort);

                File file = new File("d:\\sender\\"+fileName);
                if (!file.exists()) {
                    System.out.println("File khong ton tai: " + fileName);
                    continue;
                }

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] fileBuffer = new byte[UdpPacket.DATA_SIZE];
                    int bytesRead;
                    int seq = 0;
                    int over=0;
                    while ((bytesRead = fis.read(fileBuffer)) != -1) {
                        byte[] dataToSend = Arrays.copyOf(fileBuffer, bytesRead);
                        UdpPacket packet = new UdpPacket(seq, 0, false, false, dataToSend);
                        byte[] sendData = packet.toBytes();

                        boolean acked = false;
                        int retry = 0;

                        while (!acked && retry < MAX_RETRY) {
                            if((int)(Math.random()*100)<=loss_rate){
                                System.out.println(">>Lost segment # "+seq);
                            }
                            else{
                                socket.send(new DatagramPacket(sendData, sendData.length, clientAddress, clientPort));
                                System.out.println("Sent segment # " + seq);
                            }
                            try {
                                socket.setSoTimeout(TIMEOUT);
                                byte[] ackBuf = new byte[BUFFER_SIZE];
                                DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                                socket.receive(ackPacket);

                                UdpPacket ackUdp = UdpPacket.fromBytes(ackBuf, ackPacket.getLength());
                                if (ackUdp.isAckFlag() && ackUdp.getAckNumber() == seq + 1) {
                                    System.out.println("Receive ACK = " + ackUdp.getAckNumber()+" for sequence = "+seq);
                                    acked = true;
                                    retry=0;
                                }
                            } catch (SocketTimeoutException e) {
                                retry++;
                                System.out.println("Timeout! Retransmit lan " + retry+"! Gui lai voi segment # "+seq);
                            }
                        }

                        if (!acked) {
                            System.out.println(">>Khong nhan duoc ACK sau " + MAX_RETRY + " lan. Stop transfering file.");
                            over=1;
                            break;
                        }

                        seq++;
                    }

                    // Gửi FIN
                    
                    UdpPacket fin = new UdpPacket(seq, 0, false, true, new byte[0]);
                    if(over==1) fin=new UdpPacket(seq,-1,false,true,new byte[0]);
                    byte[] finData = fin.toBytes();
                    socket.send(new DatagramPacket(finData, finData.length, clientAddress, clientPort));
                    System.out.println("Gui FIN, ket thuc truyen tep.\n====================================");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
