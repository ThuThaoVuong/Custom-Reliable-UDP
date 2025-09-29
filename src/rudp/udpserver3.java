package rudp;

import java.io.*;
import java.net.*;
import java.util.*;

public class udpserver3 {
    private static final int BUFFER_SIZE = UdpPacket.DATA_SIZE + 10;
    private static final int WINDOW_SIZE = 4;     // cửa sổ 4 gói
    private static final int TIMEOUT = 2000;      // 2 giây timeout
    private static final double loss_rate=0.1;
    private static int base = 0;                  // gói nhỏ nhất chưa ACK
    private static int nextSeqNum = 0;            // gói tiếp theo để gửi
    private static Map<Integer, Timer> timers = new HashMap<>();
    private static Map<Integer, Integer> retransmitCount = new HashMap<>();
    //private static int MAX_RETRIES=5;
    //private static int over=0;
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Cach dung: java rudp.udpserver3 <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        try (DatagramSocket socket = new DatagramSocket(port)) {
            //socket.setSoTimeout(0);
            System.out.println("Server SR ARQ dang lang nghe tren cong " + port + "...");

            while(true){
                byte[] buf = new byte[BUFFER_SIZE];
                DatagramPacket reqPacket = new DatagramPacket(buf, buf.length);

                // Nhận yêu cầu file
                try{
                    socket.receive(reqPacket);
                }catch(Exception e){

                }

                InetAddress clientAddr = reqPacket.getAddress();
                int clientPort = reqPacket.getPort();

                UdpPacket request = UdpPacket.fromBytes(reqPacket.getData(), reqPacket.getLength());
                String filename = new String(request.getData()).trim();
                if(filename.isEmpty()) continue;
                System.out.println("Nhan yeu cau file '" + filename + "' tu " + clientAddr + ":" + clientPort);

                File file = new File("d:\\sender\\"+filename);
                if (!file.exists()) {
                    System.out.println("File khong ton tai.");
                    return;
                }

                byte[] fileBytes = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(fileBytes);
                }

                int totalSegments = (int) Math.ceil((double) fileBytes.length / UdpPacket.DATA_SIZE);
                System.out.println("Tong so segment can gui: " + totalSegments);

                // Gửi dữ liệu
                while (base < totalSegments) {
                    // gửi các gói trong cửa sổ
                    while (nextSeqNum < base + WINDOW_SIZE && nextSeqNum < totalSegments) {
                        int start = nextSeqNum * UdpPacket.DATA_SIZE;
                        int end = Math.min(start + UdpPacket.DATA_SIZE, fileBytes.length);
                        byte[] chunk = Arrays.copyOfRange(fileBytes, start, end);

                        sendSegment(socket, clientAddr, clientPort, nextSeqNum, chunk);
                        //if(over==1) break;
                        nextSeqNum++;
                    }
                    //if(over==1) break;
                    // Nhận ACK
                    try {
                        byte[] ackBuf = new byte[BUFFER_SIZE];
                        DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                        socket.receive(ackPacket);

                        UdpPacket ack = UdpPacket.fromBytes(ackPacket.getData(), ackPacket.getLength());
                        int ackNum = ack.getAckNumber();

                        System.out.println("Receive ACK # " + ackNum);

                        if (ack.isAckFlag()) {
                            // Hủy timer của gói được ACK
                            int seq = ackNum - 1;
                            if (timers.containsKey(seq)) {
                                timers.get(seq).cancel();
                                timers.remove(seq);
                            }

                            // cập nhật base nếu gói đầu cửa sổ đã được ACK
                            while (!timers.containsKey(base) && base < nextSeqNum) {
                                base++;
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        // timers riêng sẽ xử lý retransmit
                    }
                    
                }

                // Gửi FIN khi hoàn tất
                UdpPacket finPkt = new UdpPacket(nextSeqNum, 0, false, true, new byte[0]);
                //if(over==1) finPkt=new UdpPacket(nextSeqNum,-1,false,true,new byte[0]);
                //over=0;
                byte[] finData = finPkt.toBytes();
                DatagramPacket finDp = new DatagramPacket(finData, finData.length, clientAddr, clientPort);
                socket.send(finDp);
                System.out.println("Gui FIN, ket thuc truyen file.\n=========================");
            } 
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Hàm gửi segment + tạo timer riêng
    private static void sendSegment(DatagramSocket socket, InetAddress clientAddr, int clientPort,
                                    int seqNum, byte[] data) throws IOException {
        int count = retransmitCount.getOrDefault(seqNum, 0);
        /*if (count >= MAX_RETRIES) {
            System.out.println("!!! Segment #" + seqNum + " vuot qua so lan gui lai. Stop transfering file.\n==================");
            over=1;
            return; // hoặc throw Exception, hoặc break vòng lặp chính
        }*/
        
        UdpPacket pkt = new UdpPacket(seqNum, seqNum + 1, false, false, data);
        byte[] sendData = pkt.toBytes();
        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, clientAddr, clientPort);
        if(Math.random()<=loss_rate){
            System.out.println(">> Lost segment # "+seqNum);
        }
        else{
            socket.send(dp);
            System.out.println("Send segment # " + seqNum);
        }

        // tạo timer cho gói này
        Timer timer = new Timer();
        timers.put(seqNum, timer);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    retransmitCount.put(seqNum, retransmitCount.getOrDefault(seqNum, 0) + 1);
                    System.out.println("Timeout -> Gui lai segment #" + seqNum + " (no " + retransmitCount.get(seqNum) + ")");
                    
                    sendSegment(socket, clientAddr, clientPort, seqNum, data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, TIMEOUT);
    }
}
