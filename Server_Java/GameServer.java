import GameApp.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.omg.CORBA.*;

public class GameServer extends GameServicePOA {
    private ORB orb;
    private Connection conn;
    private List<String> wordList = new ArrayList<>();    
    private Map<String, GameSession> gameSessions = new ConcurrentHashMap<>();
    private Map<String, PlayerSession> playerSessions = new ConcurrentHashMap<>();
    private Map<String, Boolean> loggedInUsers = new ConcurrentHashMap<>();
    
    // NEW: Hash maps to store timers and used words per game to prevent cross-game interference
    private Map<String, java.util.Timer> gameTimers = new ConcurrentHashMap<>();
    private Map<String, Set<String>> gameUsedWords = new ConcurrentHashMap<>();
    
    // Game configuration
    private long waitingTime = 10;
    private long roundDuration = 30;

    public void setORB(ORB orb_val) {
        orb = orb_val;
    }
    
    public GameServer() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/game_db", "root", "");
            loadWordsFromFile();
            loadGameConfig();
            // Add shutdown hook to clean up logged in users
            Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanup()));
            
            System.out.println("GameServer initialized successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL driver not found: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error during server initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void loadWordsFromFile() {
        try {
            Path path = Paths.get("words.txt");
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String word = line.trim().toUpperCase();
                if (!word.isEmpty()) {
                    wordList.add(word);
                }
            }
            System.out.println("Loaded " + wordList.size() + " words from file");
        } catch (IOException e) {
            System.err.println("Error loading words from file: " + e.getMessage());
            // Add some default words if file is not found
            wordList.addAll(Arrays.asList("java", "corba", "system", "network", "program", "database", "application", "interface", "protocol"));
        }
    }

    private void loadGameConfig() {
        try {
            String query = "SELECT config_name, config_value FROM game_config WHERE config_name IN ('wait_time', 'round_duration')";
            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            
            boolean foundWaitTime = false;
            boolean foundRoundDuration = false;
            
            while (rs.next()) {
                String configName = rs.getString("config_name");
                int configValue = rs.getInt("config_value");
                
                if ("wait_time".equals(configName)) {
                    this.waitingTime = configValue;
                    foundWaitTime = true;
                    System.out.println("DEBUG: Loaded wait_time from database: " + configValue + " seconds");
                } else if ("round_duration".equals(configName)) {
                    this.roundDuration = configValue;
                    foundRoundDuration = true;
                    System.out.println("DEBUG: Loaded round_duration from database: " + configValue + " seconds");
                }
            }
            
            if (!foundWaitTime) {
                System.out.println("DEBUG: wait_time not found in database, using default: " + waitingTime + " seconds");
            }
            if (!foundRoundDuration) {
                System.out.println("DEBUG: round_duration not found in database, using default: " + roundDuration + " seconds");
            }
            
            System.out.println("Game configuration loaded: wait_time=" + waitingTime + "s, round_duration=" + roundDuration + "s");
        } catch (SQLException e) {
            System.err.println("Error loading game configuration: " + e.getMessage());
            System.out.println("Using default values: wait_time=10s, round_duration=30s");
        }
    }
    
    // IDL-defined methods
    public String login(String username, String password) {
        try {
            // First verify credentials from database
            String query = "SELECT password, user_type, session_token FROM users WHERE username = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String dbPassword = rs.getString("password");
                String userType = rs.getString("user_type");
                String currentSessionToken = rs.getString("session_token");
                
                if (password.equals(dbPassword)) {
                    // Generate new 4-digit session token
                    String newSessionToken = generateSessionToken();                    // Check if user is already logged in with a different session
                    GameSession gameToTakeOver = null;
                    if (currentSessionToken != null && !currentSessionToken.isEmpty()) {
                        // Get the existing session if any
                        PlayerSession existingSession = playerSessions.get(username);
                        if (existingSession != null && !existingSession.getSessionToken().equals(newSessionToken)) {
                            // Mark existing session for forced logout
                            existingSession.markForForceLogout("This account has been logged in from another client");
                            System.out.println("User " + username + " session overridden - existing session will be force logged out");
                            
                            // Check if user is currently in a game - preserve game state for takeover
                            GameSession currentGame = findPlayerGame(username);
                            if (currentGame != null) {
                                gameToTakeOver = currentGame;
                                System.out.println("User " + username + " is in game " + currentGame.getGameId() + " - new session will take over the game automatically");
                            }
                        }
                    }
                    
                    // Update database with new session token
                    String updateQuery = "UPDATE users SET session_token = ? WHERE username = ?";
                    PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
                    updateStmt.setString(1, newSessionToken);
                    updateStmt.setString(2, username);
                    updateStmt.executeUpdate();
                    
                    // Mark user as logged in with new session
                    loggedInUsers.put(username, true);
                    
                    // Create new player session with updated token (preserving game state)
                    PlayerSession session = new PlayerSession(username, userType, newSessionToken);
                    playerSessions.put(username, session);
                    
                    System.out.println("User " + username + " logged in successfully as " + userType + " with token: " + newSessionToken);
                    
                    // If taking over a game, return game information immediately
                    if (gameToTakeOver != null) {
                        String gameStatus = gameToTakeOver.isWaitingForPlayers() ? "waiting" : "active";
                        System.out.println("Auto-loading game " + gameToTakeOver.getGameId() + " for " + username + " (status: " + gameStatus + ")");
                        return "SUCCESS:" + newSessionToken + ":GAME_TAKEOVER:" + gameToTakeOver.getGameId() + ":" + gameStatus;
                    }
                    
                    return "SUCCESS:" + newSessionToken;
                } else {
                    // Invalid password
                    System.out.println("Invalid credentials for user " + username);
                    return "INVALID_CREDENTIALS";
                }
            } else {
                // User not found
                System.out.println("User " + username + " not found");                
                return "INVALID_CREDENTIALS";
            }
        } catch (SQLException e) {
            System.err.println("Login error: " + e.getMessage());
            return "ERROR";
        }
    }

    public String getUserType(String username) {
        try {
            // First check if user has an active session
            PlayerSession session = playerSessions.get(username);
            if (session != null) {
                return session.getUserType();
            }
            
            // If no active session, query database
            String query = "SELECT user_type FROM users WHERE username = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("user_type");
            } else {
                System.out.println("User " + username + " not found in getUserType");
                return "unknown";
            }
        } catch (SQLException e) {
            System.err.println("Get user type error: " + e.getMessage());
            return "error";
        }
    }
    
    public void logout(String username) {
        try {
            // Update database to clear session token
            String updateQuery = "UPDATE users SET session_token = NULL WHERE username = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
            updateStmt.setString(1, username);
            updateStmt.executeUpdate();
            
            // Remove from active games if participating
            synchronized (this) {
                for (GameSession game : gameSessions.values()) {
                    game.removePlayer(username);
                }
            }
            
            // Mark user as logged out
            loggedInUsers.put(username, false);
            playerSessions.remove(username);
            
            System.out.println("User " + username + " logged out and session token cleared");        
        } catch (Exception e) {
            System.err.println("Logout error: " + e.getMessage());
        }
    }

    // Cleanup method to remove game data from server-level hash maps when games end
    private void cleanupGame(String gameId) {
        // Cancel and remove any active timer for this game
        java.util.Timer activeTimer = gameTimers.get(gameId);
        if (activeTimer != null) {
            System.out.println("DEBUG: Canceling and cleaning up timer for game " + gameId);
            activeTimer.cancel();
            gameTimers.remove(gameId);
        }
        
        // Remove used words tracking for this game
        Set<String> removedWords = gameUsedWords.remove(gameId);
        if (removedWords != null) {
            System.out.println("DEBUG: Cleaned up word tracking for game " + gameId + " (" + removedWords.size() + " words were used)");
        }
        
        System.out.println("DEBUG: Game " + gameId + " cleanup completed");
    }
    
    public void quitGame(String username) {
        synchronized (this) {
            try {
                // Remove player from their current game
                GameSession game = findPlayerGame(username);
                if (game != null) {
                    game.removePlayer(username);
                    System.out.println("Player " + username + " quit game " + game.getGameId());
                    // If game becomes empty, remove it and clean up server-level data
                    if (game.getPlayerCount() == 0) {
                        String gameId = game.getGameId();
                        gameSessions.remove(gameId);
                        cleanupGame(gameId); // Clean up timers and word tracking
                        System.out.println("Game " + gameId + " removed and cleaned up (no players)");
                    }
                } else {
                    System.out.println("Player " + username + " tried to quit but was not in any game");
                }
            } catch (Exception e) {
                System.err.println("Quit game error: " + e.getMessage());
            }
        }
    }

    public String[] startGame(String username) {
        synchronized (this) {
            try {
                PlayerSession player = playerSessions.get(username);
                if (player == null) {
                    return new String[]{"ERROR", "Not logged in"};
                }                // Check if player is already in a game (including takeover scenarios)
                for (GameSession game : gameSessions.values()) {
                    if (game.hasPlayer(username)) {
                        System.out.println("User " + username + " resuming existing game " + game.getGameId() + " (may be due to session takeover)");
                        
                        if (game.isWaitingForPlayers()) {
                            return new String[]{"SUCCESS", "Waiting for another player", game.getGameId()};
                        } else {
                            return new String[]{"SUCCESS", "Resuming game in progress", game.getGameId()};
                        }
                    }
                }

                // Look for existing game waiting for players
                for (GameSession game : gameSessions.values()) {
                    if (game.isWaitingForPlayers() && game.getPlayerCount() < 2) {
                        game.addPlayer(username);
                        if (game.getPlayerCount() == 2) {
                            game.startGame();
                            return new String[]{"SUCCESS", "Game started", game.getGameId()};
                        } else {
                            return new String[]{"SUCCESS", "Waiting for another player", game.getGameId()};
                        }
                    }
                }

                // Create new game session
                String gameId = UUID.randomUUID().toString().substring(0, 8);
                System.out.println("DEBUG: Creating new GameSession with waitingTime=" + waitingTime + "s, roundDuration=" + roundDuration + "s");
                GameSession newGame = new GameSession(gameId, wordList, waitingTime, roundDuration);
                newGame.addPlayer(username);
                gameSessions.put(gameId, newGame);
                
                return new String[]{"SUCCESS", "Waiting for another player", gameId};
            } catch (Exception e) {
                System.err.println("Start game error: " + e.getMessage());
                return new String[]{"ERROR", "Failed to start game"};
            }
        }
    }

    public boolean guessLetter(String username, char letter) {
        synchronized (this) {
            try {
                GameSession game = findPlayerGame(username);
                if (game == null) {
                    return false;
                }
                
                return game.guessLetter(username, letter);
            } catch (Exception e) {
                System.err.println("Guess letter error: " + e.getMessage());
                return false;
            }
        }
    }

    public String getPartialWord(String username) {
        synchronized (this) {
            try {
                GameSession game = findPlayerGame(username);
                if (game == null) {
                    return "Not in a game";
                }
                
                return game.getPartialWord(username);
            } catch (Exception e) {
                System.err.println("Get partial word error: " + e.getMessage());
                return "Error getting game status";
            }
        }
    }

    public String getLeaderboard() {
        try {
            String query = "SELECT username, wins FROM users WHERE user_type = 'player' ORDER BY wins DESC LIMIT 5";
            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            
            StringBuilder leaderboard = new StringBuilder("LEADERBOARD:\n");
            int rank = 1;
            while (rs.next()) {
                String username = rs.getString("username");
                int wins = rs.getInt("wins");
                leaderboard.append(rank).append(". ").append(username).append(" - ").append(wins).append(" wins\n");
                rank++;
            }
            
            return leaderboard.toString();
        } catch (SQLException e) {
            System.err.println("Leaderboard error: " + e.getMessage());
            return "Error retrieving leaderboard";
        }
    }
    
    // Admin methods
    public boolean createPlayer(String username, String password) {
        try {
            String query = "INSERT INTO users (username, password, user_type, wins) VALUES (?, ?, 'player', 0)";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            
            int result = stmt.executeUpdate();
            return result > 0;
        } catch (SQLException e) {            
            System.err.println("Create player error: " + e.getMessage());
            return false;
        }
    }

    public boolean updatePlayer(String username, String newPassword) {
        try {
            String query = "UPDATE users SET password = ? WHERE username = ? AND user_type = 'player'";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            
            int result = stmt.executeUpdate();
            return result > 0;
        } catch (SQLException e) {
            System.err.println("Update player error: " + e.getMessage());
            return false;
        }
    }
    
    public boolean deletePlayer(String username) {
        try {
            // First clear the session token
            String updateQuery = "UPDATE users SET session_token = NULL WHERE username = ? AND user_type = 'player'";
            PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
            updateStmt.setString(1, username);
            updateStmt.executeUpdate();
            
            // Then delete the player
            String query = "DELETE FROM users WHERE username = ? AND user_type = 'player'";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            
            int result = stmt.executeUpdate();
            if (result > 0) {
                // Also remove from logged in users
                loggedInUsers.remove(username);
                playerSessions.remove(username);
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Delete player error: " + e.getMessage());
            return false;
        }
    }

    public String searchPlayer(String searchTerm) {
        try {
            String query = "SELECT username, wins FROM users WHERE username LIKE ? AND user_type = 'player'";
            PreparedStatement stmt = conn.prepareStatement(query);
            String searchPattern = "%" + searchTerm + "%";
            stmt.setString(1, searchPattern);
            
            ResultSet rs = stmt.executeQuery();
            StringBuilder results = new StringBuilder("SEARCH RESULTS:\n");
            
            while (rs.next()) {
                String username = rs.getString("username");
                int wins = rs.getInt("wins");
                results.append(username).append(" - ").append(wins).append(" wins\n");
            }
            
            if (results.toString().equals("SEARCH RESULTS:\n")) {
                return "No players found matching: " + searchTerm;
            }
            
            return results.toString();
        } catch (SQLException e) {
            System.err.println("Search player error: " + e.getMessage());
            return "Error searching players";
        }
    }
    
    public boolean setGameConfig(int waitTime, int roundDuration) {
        try {
            // Update wait_time in database
            String updateWaitQuery = "UPDATE game_config SET config_value = ? WHERE config_name = 'wait_time'";
            PreparedStatement waitStmt = conn.prepareStatement(updateWaitQuery);
            waitStmt.setInt(1, waitTime);
            waitStmt.executeUpdate();
            
            // Update round_duration in database
            String updateRoundQuery = "UPDATE game_config SET config_value = ? WHERE config_name = 'round_duration'";
            PreparedStatement roundStmt = conn.prepareStatement(updateRoundQuery);
            roundStmt.setInt(1, roundDuration);
            roundStmt.executeUpdate();
            
            // Update local variables
            this.waitingTime = waitTime;
            this.roundDuration = roundDuration;
            
            System.out.println("Game configuration updated in database: wait_time=" + waitTime + "s, round_duration=" + roundDuration + "s");
            return true;
        } catch (SQLException e) {
            System.err.println("Set game config error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Set game config error: " + e.getMessage());
            return false;
        }
    }

    public String getGameConfig() {
        try {
            String query = "SELECT config_name, config_value FROM game_config WHERE config_name IN ('wait_time', 'round_duration')";
            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            
            StringBuilder config = new StringBuilder("GAME CONFIGURATION:\n");
            while (rs.next()) {
                String configName = rs.getString("config_name");
                int configValue = rs.getInt("config_value");
                config.append(configName).append(": ").append(configValue).append(" seconds\n");
            }
            
            return config.toString();
        } catch (SQLException e) {
            System.err.println("Get game config error: " + e.getMessage());
            return "Error retrieving game configuration";
        }
    }
    
    // Helper methods    
    private void cleanup() {
        try {
            System.out.println("Server shutting down, logging out all users...");
            // Update database to clear all session tokens
            String updateQuery = "UPDATE users SET session_token = NULL WHERE session_token IS NOT NULL";
            PreparedStatement stmt = conn.prepareStatement(updateQuery);
            int updated = stmt.executeUpdate();
            System.out.println("Cleared session tokens for " + updated + " users from database");
            
            // Clean up all active game timers
            System.out.println("Cleaning up " + gameTimers.size() + " active game timers...");
            for (java.util.Timer timer : gameTimers.values()) {
                timer.cancel();
            }
            gameTimers.clear();
            gameUsedWords.clear();
            System.out.println("All game timers and data cleaned up");
            
            // Close database connection
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
    
    private GameSession findPlayerGame(String username) {
        for (GameSession game : gameSessions.values()) {
            if (game.hasPlayer(username)) {
                return game;
            }
        }
        return null;
    }

    private void updatePlayerWins(String username) {
        try {
            String query = "UPDATE users SET wins = wins + 1 WHERE username = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Update wins error: " + e.getMessage());
        }
    }
    
    private String generateSessionToken() {
        // Generate a random 4-digit session token
        Random random = new Random();
        int token = 1000 + random.nextInt(9000); // Generates 1000-9999
        return String.valueOf(token);
    }

    // New method for session monitoring
    public String checkSessionStatus(String username, String sessionToken) {
        try {
            PlayerSession session = playerSessions.get(username);
            if (session == null) {
                return "NO_SESSION";
            }
              // Check if this session token matches the current session
            if (!sessionToken.equals(session.getSessionToken())) {
                // This means the user has logged in from another client
                GameSession currentGame = findPlayerGame(username);
                if (currentGame != null) {
                    System.out.println("Session displacement detected for " + username + " who is in game " + currentGame.getGameId() + " - game will be taken over by new session");
                }
                return "DISPLACED:" + "This account has been logged in from another client";
            }
            
            // Check if this session is marked for force logout
            if (session.isMarkedForForceLogout()) {
                // Don't remove immediately, let the client handle the logout first
                String message = session.getForceLogoutMessage();
                return "FORCE_LOGOUT:" + message;
            }
            
            return "ACTIVE";
        } catch (Exception e) {
            System.err.println("Check session status error: " + e.getMessage());
            return "ERROR";
        }
    }
    
    // Inner classes
    private class PlayerSession {
        private String username;
        private String userType;
        private String sessionToken;
        private long loginTime;
        private boolean forceLogout = false;
        private String forceLogoutMessage = "";

        public PlayerSession(String username, String userType, String sessionToken) {
            this.username = username;
            this.userType = userType;
            this.sessionToken = sessionToken;
            this.loginTime = System.currentTimeMillis();
        }

        public String getUsername() { return username; }        
        public String getUserType() { return userType; }
        public String getSessionToken() { return sessionToken; }
        public long getLoginTime() { return loginTime; }
        public boolean isMarkedForForceLogout() { return forceLogout; }
        public String getForceLogoutMessage() { return forceLogoutMessage; }
        
        public void markForForceLogout(String message) {
            this.forceLogout = true;
            this.forceLogoutMessage = message;
        }
        
        public void clearForceLogout() {
            this.forceLogout = false;
            this.forceLogoutMessage = "";
        }
    }
    
    private class GameSession {
        private String gameId;
        private List<String> players;
        private Map<String, Integer> scores;
        private Map<String, Integer> wrongGuesses;
        private Map<String, Set<Character>> guessedLetters;
        private List<String> gameWords;
        private List<String> availableWords;
        private String currentWord;
        private long roundStartTime;
        private boolean gameStarted;
        private boolean waitingForPlayers;
        private int currentRound;
        private long waitingTime;
        private long roundDuration;
        private String winner;        
        private String roundWinner;
        private boolean roundCompleted;
        private long roundCompletionTime;

        public GameSession(String gameId, List<String> wordList, long waitingTime, long roundDuration) {
            this.gameId = gameId;
            this.players = new ArrayList<>();
            this.scores = new HashMap<>();
            this.wrongGuesses = new HashMap<>();
            this.guessedLetters = new HashMap<>();
            this.gameWords = new ArrayList<>(wordList);
            
            //Initialize word tracking per game using hash map
            gameUsedWords.put(gameId, new HashSet<>());
            this.availableWords = new ArrayList<>(wordList);
            Collections.shuffle(this.availableWords);
            
            this.gameStarted = false;
            this.waitingForPlayers = true;
            this.currentRound = 0;
            this.waitingTime = waitingTime;
            this.roundDuration = roundDuration;
            this.winner = null;
            this.roundWinner = null;
            this.roundCompleted = false;
            this.roundCompletionTime = 0;
            
            System.out.println("Game " + gameId + " initialized with " + availableWords.size() + " available words");
        }

        public void addPlayer(String username) {
            if (!players.contains(username)) {
                players.add(username);
                scores.put(username, 0);
                wrongGuesses.put(username, 0);
                guessedLetters.put(username, new HashSet<>());
                System.out.println("Player " + username + " added to game " + gameId);
            }
        }

        public void removePlayer(String username) {
            players.remove(username);
            scores.remove(username);
            wrongGuesses.remove(username);
            guessedLetters.remove(username);
            System.out.println("Player " + username + " removed from game " + gameId);
        }

        public boolean hasPlayer(String username) {
            return players.contains(username);
        }

        public int getPlayerCount() {
            return players.size();
        }

        public boolean isWaitingForPlayers() {
            return waitingForPlayers;
        }

        public String getGameId() {
            return gameId;
        }
        
        public void startGame() {
            waitingForPlayers = false;
            gameStarted = true;            
            // Add countdown delay before starting the first round
            System.out.println("Game " + gameId + " starting in 3 seconds...");
            java.util.Timer startTimer = new java.util.Timer();
            startTimer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    System.out.println("DEBUG: 3-second countdown completed, starting first round...");
                    startNewRound();
                    startTimer.cancel();
                }
            }, 3000);
            
            System.out.println("Game " + gameId + " initialized with players: " + players);
        }
        
        private void startNewRound() {
            java.util.Timer activeRoundTimer = gameTimers.get(gameId);
            if (activeRoundTimer != null) {
                System.out.println("DEBUG: Canceling previous round timer before starting new round");
                activeRoundTimer.cancel();
                gameTimers.remove(gameId);
            }
            
            // Reset round completion flags
            roundCompleted = false;
            roundWinner = null;
            roundCompletionTime = 0;
            
            // Select word that hasn't been used in THIS game
            String selectedWord = getNextAvailableWord();
            
            if (selectedWord != null) {
                currentWord = selectedWord;
                gameUsedWords.get(gameId).add(selectedWord); // Mark as used for THIS gameID
                roundStartTime = System.currentTimeMillis();
                currentRound++;
                
                System.out.println("DEBUG: Starting round " + currentRound + " in game " + gameId + " with word: " + currentWord);
                
                // Reset wrong guesses and guessed letters for this round
                for (String player : players) {
                    wrongGuesses.put(player, 0);
                    guessedLetters.put(player, new HashSet<>());
                }
                
                System.out.println("Round " + currentRound + " started in game " + gameId + " with word: " + currentWord);
                
                // Start automatic round timeout timer and track it
                java.util.Timer newRoundTimer = new java.util.Timer();
                gameTimers.put(gameId, newRoundTimer);
                newRoundTimer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        System.out.println("DEBUG: Round " + currentRound + " timed out in game " + gameId);
                        roundCompleted = true;
                        roundWinner = "NO_WINNER";
                        roundCompletionTime = System.currentTimeMillis();
                        
                        // Schedule next round start
                        java.util.Timer nextRoundTimer = new java.util.Timer();
                        nextRoundTimer.schedule(new java.util.TimerTask() {
                            @Override
                            public void run() {
                                startNewRound();
                                nextRoundTimer.cancel();
                            }
                        }, 3000);
                        
                        newRoundTimer.cancel();
                        gameTimers.remove(gameId);
                    }
                }, roundDuration * 1000);                
            } else {
                System.out.println("No more words available for game " + gameId);
                winner = "DRAW";
            }
        }
        
        public boolean guessLetter(String username, char letter) {
            if (!gameStarted || currentWord == null) {
                return false;
            }

            if (wrongGuesses.get(username) >= 5) {
                return false;
            }

            if (System.currentTimeMillis() - roundStartTime > roundDuration * 1000) {
                return false;
            }

            char guessChar = Character.toUpperCase(letter);
            Set<Character> playerGuesses = guessedLetters.get(username);
            
            if (playerGuesses.contains(guessChar)) {
                return false;
            }            
            
            playerGuesses.add(guessChar);            
            if (currentWord.indexOf(guessChar) >= 0) {
                // Correct guess - check if word is now complete
                boolean wordComplete = true;
                for (char c : currentWord.toCharArray()) {
                    if (!playerGuesses.contains(c)) {
                        wordComplete = false;
                        break;
                    }
                }
                
                if (wordComplete) {
                    // Player won this round
                    int newScore = scores.get(username) + 1;
                    scores.put(username, newScore);
                    roundCompleted = true;
                    roundWinner = username;
                    roundCompletionTime = System.currentTimeMillis();
                    
                    // Cancel the round timer
                    java.util.Timer activeTimer = gameTimers.get(gameId);
                    if (activeTimer != null) {
                        activeTimer.cancel();
                        gameTimers.remove(gameId);
                    }
                    
                    updatePlayerWins(username);
                    
                    if (newScore >= 3) {
                        winner = username;
                        System.out.println("Game " + gameId + " completed! Winner: " + username);
                    } else {
                        // Schedule next round
                        java.util.Timer nextRoundTimer = new java.util.Timer();
                        nextRoundTimer.schedule(new java.util.TimerTask() {
                            @Override
                            public void run() {
                                startNewRound();
                                nextRoundTimer.cancel();
                            }
                        }, 3000);
                    }
                }
                return true;            
            } else {
                // Wrong guess
                int newWrongCount = wrongGuesses.get(username) + 1;
                wrongGuesses.put(username, newWrongCount);
                
                if (newWrongCount >= 5) {
                    System.out.println("Player " + username + " reached maximum wrong guesses in game " + gameId);
                }
                return false;
            }
        }

        public String getPartialWord(String username) {
            // Check if game is waiting to start (before first round)
            if (gameStarted && currentWord == null && currentRound == 0) {
                return "Game starting soon... Please wait for the first round to begin.";
            }
            
            if (currentWord == null) return "No active game";
            
            StringBuilder partial = new StringBuilder();
            Set<Character> playerGuesses = guessedLetters.get(username);
            
            for (char c : currentWord.toCharArray()) {
                if (playerGuesses.contains(c)) {
                    partial.append(c);
                } else {
                    partial.append('_');
                }
                partial.append(' ');
            }
            
            String result = partial.toString().trim();
            
            // Add game status info
            if (winner != null) {
                if (winner.equals(username)) {
                    return currentWord + " | Congratulations! You won the game! The final word was: " + currentWord + " (3/3 rounds)";
                } else {
                    return currentWord + " | Game finished. Winner: " + winner + ". The final word was: " + currentWord + " (3/3 rounds)";
                }
            }
            
            // Check if round was just completed
            if (roundCompleted && roundCompletionTime > 0) {
                long timeSinceCompletion = System.currentTimeMillis() - roundCompletionTime;
                long countdownRemaining = 3000 - timeSinceCompletion;
                
                if (countdownRemaining > 0) {
                    int secondsLeft = (int) Math.ceil(countdownRemaining / 1000.0);                    
                    if ("NO_WINNER".equals(roundWinner)) {
                        return currentWord + " | Round timed out! The word was: " + currentWord + ". Starting next round in " + secondsLeft + " second" + (secondsLeft == 1 ? "" : "s") + "...";
                    } else if (roundWinner.equals(username)) {
                        return result + " | You won the round! Starting next round in " + secondsLeft + " second" + (secondsLeft == 1 ? "" : "s") + "...";
                    } else {
                        return currentWord + " | " + roundWinner + " won the round! The word was: " + currentWord + ". Starting next round in " + secondsLeft + " second" + (secondsLeft == 1 ? "" : "s") + "...";
                    }
                } else {
                    // Reset round completion flags after countdown
                    roundCompleted = false;
                    roundWinner = null;
                    roundCompletionTime = 0;
                }
            }
            
            long timeLeft = (roundDuration * 1000) - (System.currentTimeMillis() - roundStartTime);
            if (timeLeft <= 0) {
                if (!roundCompleted) {
                    return currentWord + " | Round expired! The word was: " + currentWord + ". Waiting for next round...";
                } else {
                    return currentWord + " | Round expired! The word was: " + currentWord;
                }
            }
            
            int playerScore = scores.get(username);
            int wrongCount = wrongGuesses.get(username);
            
            if (wrongCount >= 5) {
                return currentWord + " | You got 5 letters wrong! The word was: " + currentWord + ". Waiting for the other player's round result...";
            }
            return String.format("%s | Score: %d/3 | Wrong: %d/5 | Time: %ds", result, playerScore, wrongCount, timeLeft / 1000);
        }
        
        private String getNextAvailableWord() {
            Set<String> usedWordsInThisGame = gameUsedWords.get(gameId);
            for (String word : availableWords) {
                if (!usedWordsInThisGame.contains(word)) {
                    return word;
                }
            }
            return null;
        }
    }
}
