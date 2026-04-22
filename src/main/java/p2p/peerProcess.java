package p2p;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

// main entry point — run with: java peerProcess <peerID>
// reads configs, starts server, connects to earlier peers, and runs the file sharing protocol
public class peerProcess {
    
    // Configuration
    private static final String COMMON_CONFIG_FILE = "Common.cfg";
    private static final String PEER_INFO_CONFIG_FILE = "PeerInfo.cfg";
    
    // Instance variables
    private final int myPeerId;
    private CommonConfig commonConfig;
    private PeerInfoConfig peerInfoConfig;
    private PeerInfo myPeerInfo;
    private FileManager fileManager;
    
    // Server socket for accepting connections
    private ServerSocket serverSocket;
    
    // Active connections to other peers
    private final Map<Integer, Socket> peerConnections;
    private final Map<Integer, DataOutputStream> outputStreams;
    private final Map<Integer, DataInputStream> inputStreams;
    private final Map<Integer, Boolean> peerChokingMe;
    private final Set<Integer> requestedPieces;
    
    // Bitfields tracking what pieces each peer has
    private Bitfield myBitfield;
    private final Map<Integer, Bitfield> peerBitfields;
    
    // Thread management
    private final ExecutorService threadPool;
    private ScheduledExecutorService scheduler;
    
    // Logger
    private static Logger logger;
    
    public peerProcess(int peerId) {
        this.myPeerId = peerId;
        this.peerConnections = new ConcurrentHashMap<>();
        this.outputStreams = new ConcurrentHashMap<>();
        this.inputStreams = new ConcurrentHashMap<>();
        this.peerChokingMe = new ConcurrentHashMap<>();
        this.requestedPieces = ConcurrentHashMap.newKeySet();
        this.peerBitfields = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool();
        
        // Initialize logger
        setupLogger();
    }
    
    // set up a file logger at log_peer_<peerID>.log
    private void setupLogger() {
        try {
            logger = Logger.getLogger("Peer_" + myPeerId);
            logger.setUseParentHandlers(false);
            
            FileHandler fileHandler = new FileHandler("log_peer_" + myPeerId + ".log");
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
            
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }
    
    // load Common.cfg and PeerInfo.cfg
    private void loadConfigurations() throws IOException {
        // Load common configuration
        commonConfig = new CommonConfig(COMMON_CONFIG_FILE);
        logger.info("Loaded common configuration: " + commonConfig);
        
        // Load peer information
        peerInfoConfig = new PeerInfoConfig(PEER_INFO_CONFIG_FILE);
        logger.info("Loaded peer configuration with " + peerInfoConfig.getPeerCount() + " peers");
        
        // Find my peer information
        myPeerInfo = peerInfoConfig.getPeerById(myPeerId);
        if (myPeerInfo == null) {
            throw new IllegalArgumentException("Peer ID " + myPeerId + " not found in " + PEER_INFO_CONFIG_FILE);
        }
        
        logger.info("My peer info: " + myPeerInfo);
    }
    
    // set up our bitfield based on whether we start with the file
    private void initializeBitfield() throws IOException {
        int pieceCount = commonConfig.getPieceCount();
        boolean hasFile = myPeerInfo.hasFile();

        fileManager = new FileManager(
                myPeerId,
                commonConfig.getFileName(),
                commonConfig.getFileSize(),
                commonConfig.getPieceSize(),
                pieceCount,
                hasFile
        );
        
        myBitfield = new Bitfield(pieceCount, hasFile);
        logger.info("Initialized bitfield: " + myBitfield);
        logger.info("Working directory for file pieces: " + fileManager.getPeerDirectory());
        
        if (hasFile) {
            logger.info("Peer " + myPeerId + " has the complete file");
        } else {
            logger.info("Peer " + myPeerId + " does not have the file");
        }
    }
    
    // open a server socket and spin up a thread to accept incoming connections
    private void startServer() throws IOException {
        serverSocket = new ServerSocket(myPeerInfo.getPort());
        logger.info("Server started on port " + myPeerInfo.getPort());
        
        // Start a thread to accept incoming connections
        threadPool.execute(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("Accepted incoming connection from " + clientSocket.getInetAddress());
                    
                    // Handle the connection in a separate thread
                    threadPool.execute(() -> handleIncomingConnection(clientSocket));
                    
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        logger.warning("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    // called when a peer connects to us — do handshake, exchange bitfields, then start messaging
    private void handleIncomingConnection(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            
            // Receive handshake from the peer
            Handshake receivedHandshake = Handshake.receive(in);
            int remotePeerId = receivedHandshake.getPeerId();
            
            logger.info("Received handshake from Peer " + remotePeerId);
            
            // Send my handshake
            Handshake myHandshake = new Handshake(myPeerId);
            myHandshake.send(out);
            
            logger.info("Sent handshake to Peer " + remotePeerId);
            
            // Store connection information
            peerConnections.put(remotePeerId, socket);
            inputStreams.put(remotePeerId, in);
            outputStreams.put(remotePeerId, out);
            peerChokingMe.put(remotePeerId, false);
            
            // Exchange bitfields
            exchangeBitfields(remotePeerId, in, out);
            
            // Start message handling for this peer
            handlePeerMessages(remotePeerId, in, out);
            
        } catch (IOException e) {
            logger.warning("Error handling incoming connection: " + e.getMessage());
        }
    }
    
    // connect to peers that are listed before us in PeerInfo.cfg
    private void connectToPeers() {
        List<PeerInfo> peersToConnect = peerInfoConfig.getPeersToConnectTo(myPeerId);
        
        logger.info("Connecting to " + peersToConnect.size() + " peers");
        
        for (PeerInfo peerInfo : peersToConnect) {
            threadPool.execute(() -> connectToPeer(peerInfo));
        }
    }
    
    // open a TCP connection to a peer, do handshake, exchange bitfields, start messaging
    private void connectToPeer(PeerInfo peerInfo) {
        try {
            // Establish TCP connection
            Socket socket = new Socket(peerInfo.getHostName(), peerInfo.getPort());
            logger.info("TCP connection established to Peer " + peerInfo.getPeerId() + 
                       " at " + peerInfo.getHostName() + ":" + peerInfo.getPort());
            
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            
            // Send handshake
            Handshake myHandshake = new Handshake(myPeerId);
            myHandshake.send(out);
            
            logger.info("Sent handshake to Peer " + peerInfo.getPeerId());
            
            // Receive handshake
            Handshake receivedHandshake = Handshake.receive(in);
            
            if (receivedHandshake.getPeerId() != peerInfo.getPeerId()) {
                logger.warning("Handshake peer ID mismatch. Expected: " + peerInfo.getPeerId() + 
                             ", Received: " + receivedHandshake.getPeerId());
                socket.close();
                return;
            }
            
            logger.info("Received handshake from Peer " + peerInfo.getPeerId());
            logger.info("Peer " + myPeerId + " makes a connection to Peer " + peerInfo.getPeerId());
            
            // Store connection information
            peerConnections.put(peerInfo.getPeerId(), socket);
            inputStreams.put(peerInfo.getPeerId(), in);
            outputStreams.put(peerInfo.getPeerId(), out);
            peerChokingMe.put(peerInfo.getPeerId(), false);
            
            // Exchange bitfields
            exchangeBitfields(peerInfo.getPeerId(), in, out);
            
            // Start message handling for this peer
            handlePeerMessages(peerInfo.getPeerId(), in, out);
            
        } catch (IOException e) {
            logger.warning("Failed to connect to Peer " + peerInfo.getPeerId() + ": " + e.getMessage());
        }
    }
    
    // send our bitfield, receive theirs, then send INTERESTED or NOT_INTERESTED
    private void exchangeBitfields(int peerId, DataInputStream in, DataOutputStream out) throws IOException {
        // Send my bitfield
        Message bitfieldMessage = Message.createBitfieldMessage(myBitfield.toBytes());
        sendMessage(out, bitfieldMessage);
        logger.info("Sent bitfield to Peer " + peerId);
        
        // Receive peer's bitfield
        Message receivedMessage = Message.receive(in);
        
        if (receivedMessage.getType() == MessageType.BITFIELD) {
            Bitfield peerBitfield = new Bitfield(commonConfig.getPieceCount(), receivedMessage.getPayload());
            peerBitfields.put(peerId, peerBitfield);
            logger.info("Received bitfield from Peer " + peerId + ": " + peerBitfield);
            
            // Determine interest
            determineAndSendInterest(peerId, out);
            maybeRequestNextPiece(peerId, out);
        }
    }
    
    // check if the peer has anything we need and send INTERESTED / NOT_INTERESTED accordingly
    private void determineAndSendInterest(int peerId, DataOutputStream out) throws IOException {
        Bitfield peerBitfield = peerBitfields.get(peerId);
        
        if (peerBitfield != null && peerBitfield.hasInterestingPiecesFor(myBitfield)) {
            Message interestedMessage = new Message(MessageType.INTERESTED);
            sendMessage(out, interestedMessage);
            logger.info("Sent INTERESTED to Peer " + peerId);
        } else {
            Message notInterestedMessage = new Message(MessageType.NOT_INTERESTED);
            sendMessage(out, notInterestedMessage);
            logger.info("Sent NOT_INTERESTED to Peer " + peerId);
        }
    }
    
    // loop that reads and processes messages from a connected peer
    private void handlePeerMessages(int peerId, DataInputStream in, DataOutputStream out) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Message message = Message.receive(in);
                logger.info("Received " + message + " from Peer " + peerId);
                
                // Process message based on type
                processMessage(peerId, message, out);
            }
        } catch (IOException e) {
            logger.info("Connection closed with Peer " + peerId + ": " + e.getMessage());
        }
    }
    
    // dispatch a received message to the appropriate handler logic
    private void processMessage(int peerId, Message message, DataOutputStream out) throws IOException {
        switch (message.getType()) {
            case CHOKE:
                peerChokingMe.put(peerId, true);
                logger.info("Peer " + myPeerId + " is choked by Peer " + peerId);
                break;
                
            case UNCHOKE:
                peerChokingMe.put(peerId, false);
                logger.info("Peer " + myPeerId + " is unchoked by Peer " + peerId);
                maybeRequestNextPiece(peerId, out);
                break;
                
            case INTERESTED:
                logger.info("Peer " + myPeerId + " received INTERESTED from Peer " + peerId);
                break;
                
            case NOT_INTERESTED:
                logger.info("Peer " + myPeerId + " received NOT_INTERESTED from Peer " + peerId);
                break;
                
            case HAVE:
                int pieceIndex = message.getPieceIndex();
                Bitfield peerBitfield = peerBitfields.get(peerId);
                if (peerBitfield != null) {
                    peerBitfield.setPiece(pieceIndex);
                }
                logger.info("Peer " + myPeerId + " received HAVE message for piece " + pieceIndex + " from Peer " + peerId);
                
                // Re-evaluate interest
                determineAndSendInterest(peerId, out);
                maybeRequestNextPiece(peerId, out);
                break;
                
            case REQUEST:
                handleRequestMessage(peerId, message, out);
                break;
                
            case PIECE:
                handlePieceMessage(peerId, message, out);
                break;
                
            case BITFIELD:
                // Already handled during initial exchange
                break;
        }
    }
    
    // kick everything off
    public void start() {
        try {
            logger.info("Starting Peer " + myPeerId);
            
            // Load configurations
            loadConfigurations();
            
            // Initialize bitfield
            initializeBitfield();
            
            // Start server to accept incoming connections
            startServer();
            
            // Give server time to start
            Thread.sleep(1000);
            
            // Connect to peers that appear before this peer in the config
            connectToPeers();
            
            logger.info("Peer " + myPeerId + " started successfully");
            
            // Keep the main thread alive
            while (true) {
                Thread.sleep(1000);
                
                // Check if all peers have the complete file
                if (allPeersHaveCompleteFile()) {
                    logger.info("All peers have downloaded the complete file");
                    shutdown();
                    break;
                }

                if (myBitfield.hasAllPieces() && !fileManager.getPeerFilePath().toFile().exists()) {
                    fileManager.writeFullFile();
                    logger.info("Peer " + myPeerId + " has downloaded the complete file");
                }
            }
            
        } catch (Exception e) {
            logger.severe("Error starting peer: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // returns true once every peer (including us) has all pieces
    private boolean allPeersHaveCompleteFile() {
        if (!myBitfield.hasAllPieces()) {
            return false;
        }
        
        for (Bitfield bitfield : peerBitfields.values()) {
            if (!bitfield.hasAllPieces()) {
                return false;
            }
        }
        
        return peerBitfields.size() == peerInfoConfig.getPeerCount() - 1;
    }
    
    // close all sockets and stop the thread pools
    private void shutdown() {
        try {
            logger.info("Shutting down Peer " + myPeerId);
            
            // Close all peer connections
            for (Socket socket : peerConnections.values()) {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }
            
            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            // Shutdown thread pools
            threadPool.shutdown();
            if (scheduler != null) {
                scheduler.shutdown();
            }
            
            logger.info("Peer " + myPeerId + " shut down successfully");
            
        } catch (IOException e) {
            logger.warning("Error during shutdown: " + e.getMessage());
        }
    }

    private void handleRequestMessage(int peerId, Message message, DataOutputStream out) throws IOException {
        int requestedPiece = message.getPieceIndex();
        byte[] pieceData = fileManager.readPiece(requestedPiece);

        if (pieceData == null) {
            logger.warning("Peer " + myPeerId + " received REQUEST for missing piece " + requestedPiece + " from Peer " + peerId);
            return;
        }

        Message pieceMessage = Message.createPieceMessage(requestedPiece, pieceData);
        sendMessage(out, pieceMessage);
    }

    private void handlePieceMessage(int peerId, Message message, DataOutputStream out) throws IOException {
        int receivedPieceIndex = message.getPieceIndex();
        byte[] pieceData = message.getPieceData();
        requestedPieces.remove(receivedPieceIndex);

        boolean wasNewPiece = fileManager.writePiece(receivedPieceIndex, pieceData);
        if (!wasNewPiece) {
            maybeRequestNextPiece(peerId, out);
            return;
        }

        myBitfield.setPiece(receivedPieceIndex);
        logger.info("Peer " + myPeerId + " received the piece " + receivedPieceIndex + " from Peer " + peerId +
                ". Now it has " + myBitfield.getPiecesOwned() + " pieces.");

        broadcastHave(receivedPieceIndex);

        if (myBitfield.hasAllPieces()) {
            fileManager.writeFullFile();
            logger.info("Peer " + myPeerId + " has downloaded the complete file");
            broadcastNotInterested();
            return;
        }

        maybeRequestNextPiece(peerId, out);
    }

    private void maybeRequestNextPiece(int peerId, DataOutputStream out) throws IOException {
        Boolean isChoking = peerChokingMe.get(peerId);
        if (isChoking != null && isChoking) {
            return;
        }

        Bitfield peerBitfield = peerBitfields.get(peerId);
        if (peerBitfield == null) {
            return;
        }

        int pieceToRequest = pickRequestablePiece(peerBitfield);
        if (pieceToRequest == -1) {
            Message notInterestedMessage = new Message(MessageType.NOT_INTERESTED);
            sendMessage(out, notInterestedMessage);
            return;
        }

        requestedPieces.add(pieceToRequest);
        Message requestMessage = Message.createRequestMessage(pieceToRequest);
        sendMessage(out, requestMessage);
        logger.info("Peer " + myPeerId + " requested piece " + pieceToRequest + " from Peer " + peerId);
    }

    private int pickRequestablePiece(Bitfield peerBitfield) {
        List<Integer> missingPieces = myBitfield.getMissingPiecesFrom(peerBitfield);
        if (missingPieces.isEmpty()) {
            return -1;
        }

        Collections.shuffle(missingPieces);
        for (int pieceIndex : missingPieces) {
            if (!requestedPieces.contains(pieceIndex)) {
                return pieceIndex;
            }
        }
        return -1;
    }

    private void broadcastHave(int pieceIndex) {
        Message haveMessage = Message.createHaveMessage(pieceIndex);
        for (Map.Entry<Integer, DataOutputStream> entry : outputStreams.entrySet()) {
            try {
                sendMessage(entry.getValue(), haveMessage);
            } catch (IOException e) {
                logger.warning("Failed to send HAVE to Peer " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    private void broadcastNotInterested() {
        Message notInterested = new Message(MessageType.NOT_INTERESTED);
        for (Map.Entry<Integer, DataOutputStream> entry : outputStreams.entrySet()) {
            try {
                sendMessage(entry.getValue(), notInterested);
            } catch (IOException e) {
                logger.warning("Failed to send NOT_INTERESTED to Peer " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    private void sendMessage(DataOutputStream out, Message message) throws IOException {
        synchronized (out) {
            message.send(out);
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java peerProcess <peerID>");
            System.exit(1);
        }
        
        try {
            int peerId = Integer.parseInt(args[0]);
            peerProcess peer = new peerProcess(peerId);
            peer.start();
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid peer ID: " + args[0]);
            System.exit(1);
        }
    }
}
