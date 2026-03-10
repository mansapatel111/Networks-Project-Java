package p2p;

import java.io.*;
import java.nio.ByteBuffer;

// a protocol message: [4-byte length][1-byte type][payload]
public class Message {
    
    private MessageType type;
    private byte[] payload;
    
    // message with no payload
    public Message(MessageType type) {
        this.type = type;
        this.payload = new byte[0];
    }
    
    // message with a payload
    public Message(MessageType type, byte[] payload) {
        this.type = type;
        this.payload = payload != null ? payload : new byte[0];
    }
    
    public static Message createHaveMessage(int pieceIndex) {
        byte[] payload = ByteBuffer.allocate(4).putInt(pieceIndex).array();
        return new Message(MessageType.HAVE, payload);
    }
    
    public static Message createBitfieldMessage(byte[] bitfieldBytes) {
        return new Message(MessageType.BITFIELD, bitfieldBytes);
    }
    
    public static Message createRequestMessage(int pieceIndex) {
        byte[] payload = ByteBuffer.allocate(4).putInt(pieceIndex).array();
        return new Message(MessageType.REQUEST, payload);
    }
    
    public static Message createPieceMessage(int pieceIndex, byte[] pieceData) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + pieceData.length);
        buffer.putInt(pieceIndex);
        buffer.put(pieceData);
        return new Message(MessageType.PIECE, buffer.array());
    }
    
    public MessageType getType() {
        return type;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    // extracts the piece index from HAVE, REQUEST, or PIECE messages
    public int getPieceIndex() {
        if (type == MessageType.HAVE || type == MessageType.REQUEST) {
            return ByteBuffer.wrap(payload).getInt();
        } else if (type == MessageType.PIECE) {
            return ByteBuffer.wrap(payload, 0, 4).getInt();
        }
        throw new IllegalStateException("Message type does not contain piece index: " + type);
    }
    
    // pull out the actual piece data from a PIECE message (skips the 4-byte index prefix)
    public byte[] getPieceData() {
        if (type != MessageType.PIECE) {
            throw new IllegalStateException("Not a PIECE message");
        }
        byte[] data = new byte[payload.length - 4];
        System.arraycopy(payload, 4, data, 0, data.length);
        return data;
    }
    
    // serialize to bytes: [4-byte length][1-byte type][payload]
    public byte[] toBytes() {
        int messageLength = 1 + payload.length; // type + payload
        ByteBuffer buffer = ByteBuffer.allocate(4 + messageLength);
        
        buffer.putInt(messageLength);
        buffer.put(type.getValue());
        buffer.put(payload);
        
        return buffer.array();
    }
    
    // write this message to the output stream
    public void send(DataOutputStream out) throws IOException {
        byte[] messageBytes = toBytes();
        out.write(messageBytes);
        out.flush();
    }
    
    // read the next message from the input stream
    public static Message receive(DataInputStream in) throws IOException {
        // Read message length (4 bytes)
        int messageLength = in.readInt();
        
        if (messageLength <= 0) {
            throw new IOException("Invalid message length: " + messageLength);
        }
        
        // Read message type (1 byte)
        byte typeByte = in.readByte();
        MessageType type = MessageType.fromByte(typeByte);
        
        // Read payload (remaining bytes)
        int payloadLength = messageLength - 1;
        byte[] payload = new byte[payloadLength];
        
        if (payloadLength > 0) {
            in.readFully(payload);
        }
        
        return new Message(type, payload);
    }
    
    public int getTotalSize() {
        return 4 + 1 + payload.length;
    }
    
    @Override
    public String toString() {
        String payloadInfo = "";
        if (type == MessageType.HAVE || type == MessageType.REQUEST) {
            payloadInfo = ", pieceIndex=" + getPieceIndex();
        } else if (type == MessageType.PIECE) {
            payloadInfo = ", pieceIndex=" + getPieceIndex() + ", dataSize=" + (payload.length - 4);
        } else if (type == MessageType.BITFIELD) {
            payloadInfo = ", bitfieldSize=" + payload.length;
        }
        
        return "Message{" +
                "type=" + type +
                payloadInfo +
                '}';
    }
}
