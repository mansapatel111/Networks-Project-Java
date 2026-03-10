package p2p;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * peerProcess is the main entry point for the P2P file sharing system.
 * Each peer runs this program with its peer ID as a command-line argument.
 * 
 * Usage: java peerProcess <peerID>
 * 
 * The peer will:
 *   1. Read configuration files (Common.cfg and PeerInfo.cfg)
 *   2. Initialize its state and file manager
 *   3. Start a server socket to accept incoming connections
 *   4. Connect to all peers that appear before it in PeerInfo.cfg
 *   5. Exchange handshakes and bitfields
 *   6. Begin file transfer protocol
 *   7. Start choking/unchoking schedulers
 */
public class peerProcess {
    
    // Configuration
    private static final String COMMON_CONFIG_FILE = "Common.cfg";
    private static final String PEER_INFO_CONFIG_FILE = "PeerInfo.cfg";
    
    // Instance variables
    private final int myPeerId;
    private CommonConfig commonConfig;
    private PeerInfoConfig peerInfoConfig;
    private PeerInfo myPeerInfo;
    
    // Server socket for accepting connections
    private ServerSocket serverSocket;
    
    // Active connections to other peers
    private final Map<Integer, Socket> peerConnections;
    private final Map<Integer, DataOutputStream> outputStreams;
    private final Map<Integer, DataInputStream> inputStreams;
    
    // Bitfields tracking what pieces each peer has
    private Bitfield myBitfield;
    private final Map<Integer, Bitfield> peerBitfields;
    
    // Thread management
    private final ExecutorService threadPool;
    private ScheduledExecutorService scheduler;
    
    // Logger
    private static Logger logger;
    
    /**
     * Constructor for peerProcess.
     * @param peerId The peer ID for this process
     */
    public peerProcess(int peerId) {
        this.myPeerId = peerId;
        this.peerConnections = new ConcurrentHashMap<>();
        this.outputStreams = new ConcurrentHashMap<>();
        this.inputStreams = new ConcurrentHashMap<>();
        this.peerBitfields = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool();
        
        // Initialize logger
        setupLogger();
    }
    
    /**
     * Sets up the logger for this peer.
     * Logs are written to log_peer_<peerID>.log
     */
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
    
    /**
     * Loads configuration files.
     */
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
    
    /**
     * Initializes the bitfield for this peer.
     */
    private void initializeBitfield() {
        int pieceCount = commonConfig.getPieceCount();
        boolean hasFile = myPeerInfo.hasFile();
        
        myBitfield = new Bitfield(pieceCount, hasFile);
        logger.info("Initialized bitfield: " + myBitfield);
        
        if (hasFile) {
            logger.info("Peer " + myPeerId + " has the complete file");
        } else {
            logger.info("Peer " + myPeerId + " does not have the file");
        }
    }
    
    /**
     * Starts the server socket to accept incoming connections from other peers.
     */
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
    
    /**
     * Handles an incoming connection from another peer.
     * @param socket Socket for the incoming connection
     */
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
            
            // Exchange bitfields
            exchangeBitfields(remotePeerId, in, out);
            
            // Start message handling for this peer
            handlePeerMessages(remotePeerId, in, out);
            
        } catch (IOException e) {
            logger.warning("Error handling incoming connection: " + e.getMessage());
        }
    }
    
    /**
     * Connects to all peers that appear before this peer in PeerInfo.cfg.
     */
    private void connectToPeers() {
        List<PeerInfo> peersToConnect = peerInfoConfig.getPeersToConnectTo(myPeerId);
        
        logger.info("Connecting to " + peersToConnect.size() + " peers");
        
        for (PeerInfo peerInfo : peersToConnect) {
            threadPool.execute(() -> connectToPeer(peerInfo));
        }
    }
    
    /**
     * Connects to a specific peer.
     * @param peerInfo Information about the peer to connect to
     */
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
            
            // Exchange bitfields
            exchangeBitfields(peerInfo.getPeerId(), in, out);
            
            // Start message handling for this peer
            handlePeerMessages(peerInfo.getPeerId(), in, out);
            
        } catch (IOException e) {
            logger.warning("Failed to connect to Peer " + peerInfo.getPeerId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Exchanges bitfield messages with a peer.
     * @param peerId Peer ID to exchange with
     * @param in Input stream for reading messages
     * @param out Output stream for sending messages
     */
    private void exchangeBitfields(int peerId, DataInputStream in, DataOutputStream out) throws IOException {
        // Send my bitfield
        Message bitfieldMessage = Message.createBitfieldMessage(myBitfield.toBytes());
        bitfieldMessage.send(out);
        logger.info("Sent bitfield to Peer " + peerId);
        
        // Receive peer's bitfield
        Message receivedMessage = Message.receive(in);
        
        if (receivedMessage.getType() == MessageType.BITFIELD) {
            Bitfield peerBitfield = new Bitfield(commonConfig.getPieceCount(), receivedMessage.getPayload());
            peerBitfields.put(peerId, peerBitfield);
            logger.info("Received bitfield from Peer " + peerId + ": " + peerBitfield);
            
            // Determine interest
            determineAndSendInterest(peerId, out);
        }
    }
    
    /**
     * Determines if this peer is interested in another peer and sends appropriate message.
     * @param peerId Peer ID to evaluate
     * @param out Output stream for sending messages
     */
    private void determineAndSendInterest(int peerId, DataOutputStream out) throws IOException {
        Bitfield peerBitfield = peerBitfields.get(peerId);
        
        if (peerBitfield != null && peerBitfield.hasInterestingPiecesFor(myBitfield)) {
            Message interestedMessage = new Message(MessageType.INTERESTED);
            interestedMessage.send(out);
            logger.info("Sent INTERESTED to Peer " + peerId);
        } else {
            Message notInterestedMessage = new Message(MessageType.NOT_INTERESTED);
            notInterestedMessage.send(out);
            logger.info("Sent NOT_INTERESTED to Peer " + peerId);
        }
    }
    
    /**
     * Handles incoming messages from a peer.
     * @param peerId Peer ID
     * @param in Input stream for reading messages
     * @param out Output stream for sending messages
     */
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
    
    /**
     * Processes a received message.
     * @param peerId Peer ID that sent the message
     * @param message The received message
     * @param out Output stream for sending response messages
     */
    private void processMessage(int peerId, Message message, DataOutputStream out) throws IOException {
        switch (message.getType()) {
            case CHOKE:
                logger.info("Peer " + myPeerId + " is choked by Peer " + peerId);
                break;
                
            case UNCHOKE:
                logger.info("Peer " + myPeerId + " is unchoked by Peer " + peerId);
                // TODO: Request a piece if interested
                break;
                
            case INTERESTED:
                logger.info("Peer " + myPeerId + " received INTERESTED from Peer " + peerId);
                // TODO: Update interested peers list for choking algorithm
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
                break;
                
            case REQUEST:
                // TODO: Send requested piece if peer is unchoked
                break;
                
            case PIECE:
                // TODO: Save piece, update bitfield, send HAVE to all peers
                int receivedPieceIndex = message.getPieceIndex();
                logger.info("Peer " + myPeerId + " received the piece " + receivedPieceIndex + " from Peer " + peerId);
                break;
                
            case BITFIELD:
                // Already handled during initial exchange
                break;
        }
    }
    
    /**
     * Starts the peer process.
     */
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
            }
            
        } catch (Exception e) {
            logger.severe("Error starting peer: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Checks if all peers have the complete file.
     * @return True if all peers have all pieces, false otherwise
     */
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
    
    /**
     * Shuts down the peer process.
     */
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
    
    /**
     * Main entry point for the peer process.
     * @param args Command line arguments (expects peer ID)
     */
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
