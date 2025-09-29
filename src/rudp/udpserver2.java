package rudp;

import java.io.*;
import java.net.*;
import java.util.*;

public class udpserver2 {
    private static final int BUFFER_SIZE = 1024;
    private static final int WINDOW_SIZE = 4;     // kích thước cửa sổ
    private static final int TIMEOUT = 2000;      // ms
    private static final float loss_rate=(float) 0.1;
    private static final int MAX_RETRY=3;
    
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Cach dung: java rudp.udpserver2 <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("Server Go-Back-N ARQ dang lang nghe tren cong " + port + "...");

        while(true){
            // Nhận yêu cầu file
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket request = new DatagramPacket(buf, buf.length);
            try{
                socket.receive(request);
            }catch(Exception e){

            }

            InetAddress clientAddr = request.getAddress();
            int clientPort = request.getPort();

            String fileName = new String(request.getData(), 0, request.getLength()).trim();
            if(fileName.isEmpty() || fileName.equals("?")){
                continue;
            }
            System.out.println("Nhan yeu cau file '" + fileName + "' tu " + clientAddr + ":" + clientPort);

            File file = new File("d:\\sender\\"+fileName);
            if (!file.exists()) {
                System.out.println("File khong ton tai.");
                socket.close();
                return;
            }

            // Đọc dữ liệu file vào list
            FileInputStream fis = new FileInputStream(file);
            List<byte[]> fileData = new ArrayList<>();
            byte[] buffer = new byte[BUFFER_SIZE - 100]; // trừ header
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fileData.add(Arrays.copyOf(buffer, bytesRead));
            }
            fis.close();

            int totalSegments = fileData.size();
            System.out.println("Tong so segment can gui: " + totalSegments);

            int base = 0;          // segment đầu cửa sổ
            int nextSeq = 0;       // segment kế tiếp để gửi

            // Đếm số lần retransmit cho từng segment
            Map<Integer, Integer> retryCount = new HashMap<>();
            int over=0;
            while (base < totalSegments) {
                // Gửi trong cửa sổ

                while (nextSeq < base + WINDOW_SIZE && nextSeq < totalSegments) {
                    byte[] chunk = fileData.get(nextSeq);
                    UdpPacket pkt = new UdpPacket(nextSeq, 0, false, false, chunk);
                    byte[] sendData = pkt.toBytes();
                    DatagramPacket dp = new DatagramPacket(sendData, sendData.length, clientAddr, clientPort);
                    if(Math.random()<=loss_rate){
                        System.out.println(">>Lost segmet #"+nextSeq);
                    }
                    else{
                        socket.send(dp);
                        System.out.println("Send segment # " + nextSeq);
                    }
                    retryCount.put(nextSeq, 0); // reset lần retransmit

                    nextSeq++;
                }

                try {
                    socket.setSoTimeout(TIMEOUT);
                    byte[] ackBuf = new byte[BUFFER_SIZE];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                    socket.receive(ackPacket);

                    UdpPacket ack = UdpPacket.fromBytes(ackPacket.getData(), ackPacket.getLength());
                    int ackNum = ack.getAckNumber();
                    System.out.println("Receive ACK = " + ackNum);

                    if (ackNum > base) {
                        base = ackNum;
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout => retransmit từ base
                    int retry = retryCount.getOrDefault(base, 0) + 1;
                    if (retry > MAX_RETRY) {
                        System.out.println("Segment #" + base + " retransmit > 3. Stop transfering file.");
                        over=1;
                        break;
                    }
                    retryCount.put(base, retry);

                    System.out.println("Timeout -> Retransmit from segment #" + base + " (no " + retry + ")");
                    for (int i = base; i < nextSeq; i++) {
                        byte[] chunk = fileData.get(i);
                        UdpPacket pkt = new UdpPacket(i, 0, false, false, chunk);
                        byte[] sendData = pkt.toBytes();
                        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, clientAddr, clientPort);
                        socket.send(dp);
                        System.out.println(" -> Retransmit segment #" + i);
                    }
                }
                if(over==1) break;
            }

            // Gửi gói FIN báo kết thúc
            UdpPacket fin=new UdpPacket(nextSeq,0,false,true,new byte[0]);
            if(over==1) fin = new UdpPacket(nextSeq, -1, false, true, new byte[0]);
                
            byte[] finData = fin.toBytes();
            DatagramPacket finPkt = new DatagramPacket(finData, finData.length, clientAddr, clientPort);
            socket.send(finPkt);
            System.out.println("Da gui FIN, ket thuc truyen file.\n==============================");
        }
    }
}
