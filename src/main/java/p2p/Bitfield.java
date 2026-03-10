package p2p;

import java.util.BitSet;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

// tracks which pieces this peer has using a BitSet
public class Bitfield {
    
    private final BitSet bitset;
    private final int pieceCount;
    private int piecesOwned;
    
    // empty bitfield — no pieces owned yet
    public Bitfield(int pieceCount) {
        this.pieceCount = pieceCount;
        this.bitset = new BitSet(pieceCount);
        this.piecesOwned = 0;
    }
    
    // create a bitfield, optionally marking all pieces as already owned
    public Bitfield(int pieceCount, boolean hasAllPieces) {
        this.pieceCount = pieceCount;
        this.bitset = new BitSet(pieceCount);
        this.piecesOwned = 0;
        
        if (hasAllPieces) {
            for (int i = 0; i < pieceCount; i++) {
                setPiece(i);
            }
        }
    }
    
    // build a bitfield from the bytes received in a BITFIELD message
    public Bitfield(int pieceCount, byte[] bytes) {
        this.pieceCount = pieceCount;
        this.bitset = BitSet.valueOf(bytes);
        this.piecesOwned = bitset.cardinality();
    }
    
    // mark a piece as owned; returns false if we already had it
    public synchronized boolean setPiece(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= pieceCount) {
            throw new IndexOutOfBoundsException("Invalid piece index: " + pieceIndex);
        }
        
        boolean wasSet = bitset.get(pieceIndex);
        if (!wasSet) {
            bitset.set(pieceIndex);
            piecesOwned++;
            return true;
        }
        return false;
    }
    
    // check if we own a given piece
    public synchronized boolean hasPiece(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= pieceCount) {
            return false;
        }
        return bitset.get(pieceIndex);
    }
    
    // true if we have every piece — download complete
    public synchronized boolean hasAllPieces() {
        return piecesOwned == pieceCount;
    }
    
    public synchronized int getPiecesOwned() {
        return piecesOwned;
    }
    
    public int getPieceCount() {
        return pieceCount;
    }
    
    // returns true if we have at least one piece that the other peer is missing
    public synchronized boolean hasInterestingPiecesFor(Bitfield other) {
        for (int i = 0; i < pieceCount; i++) {
            if (this.hasPiece(i) && !other.hasPiece(i)) {
                return true;
            }
        }
        return false;
    }
    
    // pieces we're missing that the other peer has — these are what we'd want to request
    public synchronized List<Integer> getMissingPiecesFrom(Bitfield other) {
        List<Integer> missingPieces = new ArrayList<>();
        for (int i = 0; i < pieceCount; i++) {
            if (!this.hasPiece(i) && other.hasPiece(i)) {
                missingPieces.add(i);
            }
        }
        return missingPieces;
    }
    
    // pick a random piece to request from the given peer; returns -1 if nothing useful
    public synchronized int getRandomMissingPieceFrom(Bitfield other) {
        List<Integer> missingPieces = getMissingPiecesFrom(other);
        
        if (missingPieces.isEmpty()) {
            return -1;
        }
        
        Random random = new Random();
        return missingPieces.get(random.nextInt(missingPieces.size()));
    }
    
    // serialize to bytes for sending in a BITFIELD message
    public synchronized byte[] toBytes() {
        // Calculate the number of bytes needed
        int byteCount = (pieceCount + 7) / 8; // Ceiling division
        byte[] bytes = new byte[byteCount];
        
        byte[] bitsetBytes = bitset.toByteArray();
        System.arraycopy(bitsetBytes, 0, bytes, 0, Math.min(bitsetBytes.length, byteCount));
        
        return bytes;
    }
    
    // deep copy
    public synchronized Bitfield copy() {
        Bitfield copy = new Bitfield(pieceCount);
        for (int i = 0; i < pieceCount; i++) {
            if (this.hasPiece(i)) {
                copy.setPiece(i);
            }
        }
        return copy;
    }
    
    // how far along the download is, as a percentage
    public synchronized double getCompletionPercentage() {
        if (pieceCount == 0) {
            return 100.0;
        }
        return (double) piecesOwned / pieceCount * 100.0;
    }
    
    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bitfield{pieces=");
        for (int i = 0; i < pieceCount; i++) {
            sb.append(hasPiece(i) ? "1" : "0");
        }
        sb.append(", owned=").append(piecesOwned);
        sb.append("/").append(pieceCount);
        sb.append(", completion=").append(String.format("%.2f", getCompletionPercentage())).append("%");
        sb.append("}");
        return sb.toString();
    }
}
