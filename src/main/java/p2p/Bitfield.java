package p2p;
import java.util.BitSet;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
public class Bitfield {
    private final BitSet bitset;
    private final int pieceCount;
    private int piecesOwned;
    public Bitfield(int pieceCount) {
        this.pieceCount = pieceCount;
        this.bitset = new BitSet(pieceCount);
        this.piecesOwned = 0;
    }
    public Bitfield(int pieceCount, boolean hasAllPieces) {
        this.pieceCount = pieceCount;
        this.bitset = new BitSet(pieceCount);
        this.piecesOwned = 0;
        if (hasAllPieces) { for (int i = 0; i < pieceCount; i++) setPiece(i); }
    }
    public Bitfield(int pieceCount, byte[] bytes) {
        this.pieceCount = pieceCount;
        this.bitset = BitSet.valueOf(reverseBytes(bytes, pieceCount));
        this.piecesOwned = 0;
        for (int i = 0; i < pieceCount; i++) if (bitset.get(i)) piecesOwned++;
    }
    // BitSet uses little-endian bit order; protocol uses big-endian, so we reverse
    private static byte[] reverseBytes(byte[] bytes, int pieceCount) {
        int byteCount = (pieceCount + 7) / 8;
        byte[] reversed = new byte[byteCount];
        for (int i = 0; i < Math.min(bytes.length, byteCount); i++) {
            reversed[i] = reverseBits(bytes[i]);
        }
        return reversed;
    }
    private static byte reverseBits(byte b) {
        byte result = 0;
        for (int i = 0; i < 8; i++) {
            result = (byte)((result << 1) | (b & 1));
            b >>= 1;
        }
        return result;
    }
    public synchronized boolean setPiece(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= pieceCount) throw new IndexOutOfBoundsException("Invalid piece index: " + pieceIndex);
        boolean wasSet = bitset.get(pieceIndex);
        if (!wasSet) { bitset.set(pieceIndex); piecesOwned++; return true; }
        return false;
    }
    public synchronized boolean hasPiece(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= pieceCount) return false;
        return bitset.get(pieceIndex);
    }
    public synchronized boolean hasAllPieces() { return piecesOwned == pieceCount; }
    public synchronized int getPiecesOwned() { return piecesOwned; }
    public int getPieceCount() { return pieceCount; }
    public synchronized boolean hasInterestingPiecesFor(Bitfield other) {
        for (int i = 0; i < pieceCount; i++) if (this.hasPiece(i) && !other.hasPiece(i)) return true;
        return false;
    }
    public synchronized List<Integer> getMissingPiecesFrom(Bitfield other) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < pieceCount; i++) if (!this.hasPiece(i) && other.hasPiece(i)) missing.add(i);
        return missing;
    }
    public synchronized int getRandomMissingPieceFrom(Bitfield other) {
        List<Integer> missing = getMissingPiecesFrom(other);
        if (missing.isEmpty()) return -1;
        return missing.get(new Random().nextInt(missing.size()));
    }
    public synchronized byte[] toBytes() {
        int byteCount = (pieceCount + 7) / 8;
        byte[] result = new byte[byteCount];
        for (int i = 0; i < pieceCount; i++) {
            if (bitset.get(i)) {
                int byteIdx = i / 8;
                int bitIdx = 7 - (i % 8); // big-endian within byte
                result[byteIdx] |= (1 << bitIdx);
            }
        }
        return result;
    }
    public synchronized Bitfield copy() {
        Bitfield copy = new Bitfield(pieceCount);
        for (int i = 0; i < pieceCount; i++) if (this.hasPiece(i)) copy.setPiece(i);
        return copy;
    }
    public synchronized double getCompletionPercentage() {
        if (pieceCount == 0) return 100.0;
        return (double) piecesOwned / pieceCount * 100.0;
    }
    @Override
    public synchronized String toString() {
        return "Bitfield{owned=" + piecesOwned + "/" + pieceCount + ", " + String.format("%.1f", getCompletionPercentage()) + "%}";
    }
}