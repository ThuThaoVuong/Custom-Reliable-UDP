package rudp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class UdpPacket implements Serializable {

    public static final int DATA_SIZE = 1450;

    private int sequenceNumber;
    private int ackNumber;
    private boolean ackFlag;
    private boolean finFlag; // Cờ báo hiệu gói tin cuối cùng
    private byte[] data;

    public UdpPacket(int sequenceNumber, int ackNumber, boolean ackFlag, boolean finFlag, byte[] data) {
        this.sequenceNumber = sequenceNumber;
        this.ackNumber = ackNumber;
        this.ackFlag = ackFlag;
        this.finFlag = finFlag;
        this.data = data;
    }
    public UdpPacket(int sequenceNumber, boolean finFlag, byte[] data) {
        this.sequenceNumber = sequenceNumber;
        this.ackNumber = 0;
        this.ackFlag = false;
        this.finFlag = finFlag;
        this.data = data;
    }

    // Chuyển đổi đối tượng UdpPacket thành mảng byte để gửi đi
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(byteStream);

        dataStream.writeInt(sequenceNumber);
        dataStream.writeInt(ackNumber);
        dataStream.writeBoolean(ackFlag);
        dataStream.writeBoolean(finFlag);
        if (data != null) {
            dataStream.write(data);
        }

        return byteStream.toByteArray();
    }

    // Chuyển đổi mảng byte nhận được thành đối tượng UdpPacket
    public static UdpPacket fromBytes(byte[] bytes, int length) throws IOException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes, 0, length);
        DataInputStream dataStream = new DataInputStream(byteStream);

        int seqNum = dataStream.readInt();
        int ackNum = dataStream.readInt();
        boolean isAck = dataStream.readBoolean();
        boolean isFin = dataStream.readBoolean();
        
        byte[] data = new byte[dataStream.available()];
        dataStream.readFully(data);

        return new UdpPacket(seqNum, ackNum, isAck, isFin, data);
    }
    
    // Getters
    public int getSequenceNumber() { return sequenceNumber; }
    public int getAckNumber() { return ackNumber; }
    public boolean isAckFlag() { return ackFlag; }
    public boolean isFinFlag() { return finFlag; }
    public byte[] getData() { return data; }
}
