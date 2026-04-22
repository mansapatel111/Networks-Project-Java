package p2p;
import java.io.IOException;
import java.nio.file.*;
public class FileManager {
    private final String fileName;
    private final int fileSize;
    private final int pieceSize;
    private final int pieceCount;
    private final byte[][] pieces;
    private final boolean[] hasPiece;
    private final Path peerDirectory;
    private final Path peerFilePath;

    public FileManager(int peerId, String fileName, int fileSize, int pieceSize, int pieceCount, boolean startsWithFile) throws IOException {
        this.fileName = fileName; this.fileSize = fileSize;
        this.pieceSize = pieceSize; this.pieceCount = pieceCount;
        this.pieces = new byte[pieceCount][];
        this.hasPiece = new boolean[pieceCount];
        this.peerDirectory = Paths.get("peer_" + peerId);
        this.peerFilePath = peerDirectory.resolve(fileName);
        Files.createDirectories(peerDirectory);
        if (startsWithFile) { loadInitialFile(); }
    }

    private void loadInitialFile() throws IOException {
        byte[] fullFileData;
        if (Files.exists(peerFilePath)) {
            fullFileData = Files.readAllBytes(peerFilePath);
        } else {
            Path fallback = Paths.get(fileName);
            if (Files.exists(fallback)) {
                fullFileData = Files.readAllBytes(fallback);
                Files.write(peerFilePath, fullFileData);
            } else {
                fullFileData = new byte[fileSize];
                Files.write(peerFilePath, fullFileData);
            }
        }
        for (int i = 0; i < pieceCount; i++) {
            int start = i * pieceSize;
            int len = getPieceLength(i);
            byte[] pieceData = new byte[len];
            System.arraycopy(fullFileData, start, pieceData, 0, len);
            pieces[i] = pieceData;
            hasPiece[i] = true;
        }
    }

    public synchronized boolean hasPiece(int pieceIndex) { checkIndex(pieceIndex); return hasPiece[pieceIndex]; }

    public synchronized byte[] readPiece(int pieceIndex) {
        checkIndex(pieceIndex);
        if (!hasPiece[pieceIndex]) return null;
        byte[] copy = new byte[pieces[pieceIndex].length];
        System.arraycopy(pieces[pieceIndex], 0, copy, 0, copy.length);
        return copy;
    }

    public synchronized boolean writePiece(int pieceIndex, byte[] data) throws IOException {
        checkIndex(pieceIndex);
        if (hasPiece[pieceIndex]) return false;
        int expected = getPieceLength(pieceIndex);
        if (data == null || data.length != expected)
            throw new IOException("Bad piece length for " + pieceIndex + ": expected " + expected + ", got " + (data == null ? 0 : data.length));
        byte[] copy = new byte[data.length];
        System.arraycopy(data, 0, copy, 0, copy.length);
        pieces[pieceIndex] = copy;
        hasPiece[pieceIndex] = true;
        return true;
    }

    public synchronized int getPieceLength(int pieceIndex) {
        checkIndex(pieceIndex);
        if (pieceIndex == pieceCount - 1) {
            int rem = fileSize % pieceSize;
            return rem == 0 ? pieceSize : rem;
        }
        return pieceSize;
    }

    public synchronized int getOwnedPieceCount() {
        int count = 0;
        for (boolean b : hasPiece) if (b) count++;
        return count;
    }

    public synchronized boolean isComplete() { return getOwnedPieceCount() == pieceCount; }

    public synchronized void writeFullFile() throws IOException {
        if (!isComplete()) throw new IOException("Cannot write: missing pieces");
        byte[] fullData = new byte[fileSize];
        for (int i = 0; i < pieceCount; i++) System.arraycopy(pieces[i], 0, fullData, i * pieceSize, pieces[i].length);
        Files.write(peerFilePath, fullData);
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= pieceCount) throw new IndexOutOfBoundsException("Invalid piece index: " + i);
    }

    public Path getPeerFilePath() { return peerFilePath; }
    public Path getPeerDirectory() { return peerDirectory; }
}