import GameApp.*;
import org.omg.CORBA.*;
import org.omg.CosNaming.*;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;

public class GameClient extends JFrame {
    private ORB orb;    
    private GameService gameService;
    private String username;
    private String userType;
    private String sessionToken; // Store session token
    private boolean isLoggedIn = false;
    
    // Session monitoring
    private Timer sessionCheckTimer;
    
    // Login components
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JPanel loginPanel;
    
    // Main components
    private JPanel mainPanel;
    private JPanel contentPanel;
    private CardLayout cardLayout;
    
    // Player components
    private JButton startGameButton;
    private JButton viewLeaderboardButton;
    
    // Game components
    private JPanel gamePanel;
    private JTextArea gameStatusArea;
    private JTextField letterInputField;
    private JButton guessButton;
    private JButton quitGameButton;
    private JLabel gameInfoLabel;
    private boolean inGame = false;
    
    // Admin components
    private JTabbedPane adminTabbedPane;

    // Player management components
    private JTextField newUsernameField;
    private JPasswordField newPasswordField;
    private JButton createUserButton;
    
    private JTextField updateUsernameField;
    private JPasswordField updatePasswordField;
    private JButton updateUserButton;
    
    private JTextField deleteUsernameField;
    private JButton deleteUserButton;
    
    private JTextField searchField;
    private JTextArea searchResults;
    private JButton searchButton;
    
    // Game config components
    private JTextField waitTimeField;
    private JTextField roundTimeField;
    private JButton updateConfigButton;

    // Log area
    private JTextArea logArea;
    
    // Matchmaking countdown components
    private Timer matchmakingTimer;
    private int remainingWaitTime;
    private boolean isWaitingForMatch = false;
    private boolean hasShownGameStartCountdown = false;
    private String gameId = null; // Store the current game ID    
    public GameClient(ORB orb) {
        this.orb = orb;
        initializeGUI();
        connectToServer();
        
        // Add shutdown hook to automatically logout on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isLoggedIn && username != null && gameService != null) {
                try {
                    System.out.println("Logging out due to application shutdown...");
                    gameService.logout(username);
                } catch (Exception e) {
                    System.err.println("Error during shutdown logout: " + e.getMessage());
                }
            }
        }));
    }
    
    private void connectToServer() {
        try {
            // Use the ORB that was initialized with command line arguments in main()
            ORB orb = this.orb;

            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            NameComponent path[] = ncRef.to_name("Game");
            gameService = GameServiceHelper.narrow(ncRef.resolve(path));

            log("Connected to Game Server successfully!");
        } catch (Exception e) {
            log("Failed to connect to server: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Failed to connect to server: " + e.getMessage(),
                                        "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initializeGUI() {
        setTitle("What's The Word - Game Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);

        // Create login panel
        createLoginPanel();
        
        // Create main panel with card layout for different user types
        createMainPanel();
        
        // Start with login panel
        add(loginPanel);
        
        setVisible(true);
    }

    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Title
        JLabel titleLabel = new JLabel("What's The Word - Login");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 20, 30, 20);
        loginPanel.add(titleLabel, gbc);
        
        // Username field
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.insets = new Insets(10, 20, 5, 10);
        gbc.anchor = GridBagConstraints.EAST;
        loginPanel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        usernameField = new JTextField(20);
        loginPanel.add(usernameField, gbc);
        
        // Password field
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        loginPanel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        passwordField = new JPasswordField(20);
        loginPanel.add(passwordField, gbc);
        
        // Login button
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.anchor = GridBagConstraints.CENTER;
        loginButton = new JButton("Login");
        loginButton.setPreferredSize(new Dimension(100, 30));
        loginButton.addActionListener(e -> performLogin());
        loginPanel.add(loginButton, gbc);
        
        // Allow Enter key to login
        passwordField.addActionListener(e -> performLogin());
        usernameField.addActionListener(e -> passwordField.requestFocus());
    }

    private void createMainPanel() {
        mainPanel = new JPanel(new BorderLayout());
        
        // Top panel with user info and logout
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEtchedBorder());
        
        JLabel userInfoLabel = new JLabel();
        topPanel.add(userInfoLabel, BorderLayout.WEST);
        
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> logout());
        topPanel.add(logoutButton, BorderLayout.EAST);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Content panel with card layout
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        
        // Create player interface
        createPlayerInterface();
        
        // Create admin interface
        createAdminInterface();
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        // Log area at bottom
        logArea = new JTextArea(8, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        mainPanel.add(logScroll, BorderLayout.SOUTH);
    }

    private void createPlayerInterface() {
        JPanel playerPanel = new JPanel(new BorderLayout());
        
        // Main menu panel
        JPanel menuPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        JLabel welcomeLabel = new JLabel("Welcome to What's The Word!");
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(20, 20, 30, 20);
        menuPanel.add(welcomeLabel, gbc);
        
        startGameButton = new JButton("Start Game");
        startGameButton.setPreferredSize(new Dimension(200, 40));
        startGameButton.addActionListener(e -> startGame());
        gbc.gridy = 1;
        gbc.insets = new Insets(10, 20, 10, 20);
        menuPanel.add(startGameButton, gbc);
        
        viewLeaderboardButton = new JButton("View Leaderboard");
        viewLeaderboardButton.setPreferredSize(new Dimension(200, 40));
        viewLeaderboardButton.addActionListener(e -> viewLeaderboard());
        gbc.gridy = 2;
        menuPanel.add(viewLeaderboardButton, gbc);
        
        playerPanel.add(menuPanel, BorderLayout.CENTER);
        
        // Create game panel
        createGamePanel();
        
        contentPanel.add(playerPanel, "PLAYER_MENU");
        contentPanel.add(gamePanel, "PLAYER_GAME");
    }

    private void createGamePanel() {
        gamePanel = new JPanel(new BorderLayout());
        
        // Game info at top
        gameInfoLabel = new JLabel("Game Status: Not in game");
        gameInfoLabel.setFont(new Font("Arial", Font.BOLD, 14));
        gameInfoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        gamePanel.add(gameInfoLabel, BorderLayout.NORTH);
        
        // Game status area in center
        gameStatusArea = new JTextArea(15, 50);
        gameStatusArea.setEditable(false);
        gameStatusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        gameStatusArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JScrollPane gameScroll = new JScrollPane(gameStatusArea);
        gameScroll.setBorder(BorderFactory.createTitledBorder("Game Progress"));
        gamePanel.add(gameScroll, BorderLayout.CENTER);
        
        // Input panel at bottom
        JPanel inputPanel = new JPanel(new FlowLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Your Turn"));
        inputPanel.add(new JLabel("Enter letter:"));
        letterInputField = new JTextField(5);
        
        // Restrict input to single letters only
        ((AbstractDocument) letterInputField.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string != null && string.length() == 1 && Character.isLetter(string.charAt(0)) && fb.getDocument().getLength() == 0) {
                    super.insertString(fb, offset, string.toUpperCase(), attr);
                }
            }
            
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text != null && text.length() == 1 && Character.isLetter(text.charAt(0))) {
                    super.replace(fb, offset, length, text.toUpperCase(), attrs);
                } else if (text != null && text.length() == 0) {
                    // Allow deletion (clearing the field)
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
        
        inputPanel.add(letterInputField);
        
        guessButton = new JButton("Guess");
        guessButton.addActionListener(e -> makeGuess());
        inputPanel.add(guessButton);
        
        quitGameButton = new JButton("Quit Game");
        quitGameButton.addActionListener(e -> quitGame());
        inputPanel.add(quitGameButton);
        
        // Allow Enter key to make guess
        letterInputField.addActionListener(e -> makeGuess());
        
        gamePanel.add(inputPanel, BorderLayout.SOUTH);
    }

    private void createAdminInterface() {
        adminTabbedPane = new JTabbedPane();
        
        // User Management tab
        JPanel userPanel = new JPanel();
        userPanel.setLayout(new BoxLayout(userPanel, BoxLayout.Y_AXIS));
        
        // Create User section
        JPanel createPanel = new JPanel(new FlowLayout());
        createPanel.setBorder(BorderFactory.createTitledBorder("Create Player"));
        createPanel.add(new JLabel("Username:"));
        newUsernameField = new JTextField(15);
        createPanel.add(newUsernameField);        createPanel.add(new JLabel("Password:"));
        newPasswordField = new JPasswordField(15);
        createPanel.add(newPasswordField);
        createUserButton = new JButton("Create");
        createUserButton.addActionListener(e -> createUser());
        createPanel.add(createUserButton);
        userPanel.add(createPanel);
        
        // Update User section
        JPanel updatePanel = new JPanel(new FlowLayout());
        updatePanel.setBorder(BorderFactory.createTitledBorder("Update Player"));
        updatePanel.add(new JLabel("Username:"));
        updateUsernameField = new JTextField(15);
        updatePanel.add(updateUsernameField);        updatePanel.add(new JLabel("New Password:"));
        updatePasswordField = new JPasswordField(15);
        updatePanel.add(updatePasswordField);
        updateUserButton = new JButton("Update");
        updateUserButton.addActionListener(e -> updateUser());
        updatePanel.add(updateUserButton);
        userPanel.add(updatePanel);
        
        // Delete User section
        JPanel deletePanel = new JPanel(new FlowLayout());
        deletePanel.setBorder(BorderFactory.createTitledBorder("Delete Player"));
        deletePanel.add(new JLabel("Username:"));
        deleteUsernameField = new JTextField(20);
        deletePanel.add(deleteUsernameField);
        deleteUserButton = new JButton("Delete");
        deleteUserButton.addActionListener(e -> deleteUser());
        deletePanel.add(deleteUserButton);
        userPanel.add(deletePanel);
        
        // Search User section
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Players"));
        JPanel searchInputPanel = new JPanel(new FlowLayout());
        searchInputPanel.add(new JLabel("Search:"));
        searchField = new JTextField(20);
        searchInputPanel.add(searchField);
        searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchUsers());
        searchInputPanel.add(searchButton);
        searchPanel.add(searchInputPanel, BorderLayout.NORTH);
        
        searchResults = new JTextArea(8, 50);
        searchResults.setEditable(false);
        searchResults.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        searchPanel.add(new JScrollPane(searchResults), BorderLayout.CENTER);
        userPanel.add(searchPanel);
        
        adminTabbedPane.addTab("Player Management", userPanel);        // Game Configuration tab
        JPanel configTab = new JPanel(new BorderLayout());
        
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Game Configuration"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Wait Time (seconds):"), gbc);
        gbc.gridx = 1;
        waitTimeField = new JTextField("10", 10);
        configPanel.add(waitTimeField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        configPanel.add(new JLabel("Round Duration (seconds):"), gbc);
        gbc.gridx = 1;
        roundTimeField = new JTextField("30", 10);
        configPanel.add(roundTimeField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        updateConfigButton = new JButton("Update Configuration");
        updateConfigButton.addActionListener(e -> updateGameConfig());
        configPanel.add(updateConfigButton, gbc);
        
        configTab.add(configPanel, BorderLayout.NORTH);
        adminTabbedPane.addTab("Game Configuration", configTab);
        // Add change listener to automatically load config when tab is selected
        adminTabbedPane.addChangeListener(e -> {
            if (adminTabbedPane.getSelectedIndex() == 1) { // Game Configuration tab
                loadCurrentConfig();
            }
        });
        
        contentPanel.add(adminTabbedPane, "ADMIN");
    }    
    
    private void performLogin() {
        String enteredUsername = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (enteredUsername.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both username and password.", "Login Error", JOptionPane.ERROR_MESSAGE);
            return;
        }          try {
            String loginResult = gameService.login(enteredUsername, password);
            if (loginResult.startsWith("SUCCESS:")) {
                // Parse the login response: SUCCESS:token[:GAME_TAKEOVER:gameId:status]
                String[] loginParts = loginResult.split(":", -1);
                
                // Extract session token (always at index 1)
                sessionToken = loginParts[1];
                username = enteredUsername;
                isLoggedIn = true;
                userType = determineUserType(enteredUsername);
                
                log(userType.substring(0, 1).toUpperCase() + userType.substring(1) + " " + username + " logged in successfully with session: " + sessionToken);
                
                // Start session monitoring
                startSessionMonitoring();
                
                // Update title and user info
                setTitle("What's The Word - " + userType.substring(0, 1).toUpperCase() + userType.substring(1) + " Client");
                updateUserInfo();
                
                // Switch to appropriate interface
                remove(loginPanel);
                add(mainPanel);
                
                // Check if this is a game takeover scenario
                boolean isGameTakeover = loginParts.length >= 5 && "GAME_TAKEOVER".equals(loginParts[2]);
                
                if ("admin".equals(userType)) {
                    cardLayout.show(contentPanel, "ADMIN");
                } else if (isGameTakeover) {
                    // Automatic game takeover - parse game details and load game interface
                    String takeoverGameId = loginParts[3];
                    String gameStatus = loginParts[4];
                    
                    log("Game takeover detected - automatically loading game " + takeoverGameId + " (status: " + gameStatus + ")");
                    
                    // Set game state
                    gameId = takeoverGameId;
                    inGame = true;
                    hasShownGameStartCountdown = false;
                    
                    // Determine waiting state based on game status
                    if ("waiting".equals(gameStatus)) {
                        isWaitingForMatch = true;
                        gameInfoLabel.setText("Game Status: Waiting for another player - " + gameId);
                        // Switch to game interface and start matchmaking countdown
                        cardLayout.show(contentPanel, "PLAYER_GAME");
                        startMatchmakingCountdown();
                        startGameStatusPolling();
                        log("Resumed waiting state in game " + gameId);
                    } else {
                        // Game is active
                        isWaitingForMatch = false;
                        gameInfoLabel.setText("Game Status: In game - " + gameId);
                        // Switch to game interface and start status polling
                        cardLayout.show(contentPanel, "PLAYER_GAME");
                        startGameStatusPolling();
                        log("Resumed active game " + gameId);
                    }
                } else {
                    cardLayout.show(contentPanel, "PLAYER_MENU");
                }
                
                revalidate();
                repaint();
            } else {
                // Handle different error types with specific messages
                String errorMessage;
                if ("ALREADY_LOGGED_IN".equals(loginResult)) {
                    errorMessage = "User is currently logged in. Please try again later or contact an administrator.";
                } else if ("INVALID_CREDENTIALS".equals(loginResult)) {
                    errorMessage = "Login failed! Invalid username or password.";
                } else if ("ERROR".equals(loginResult)) {
                    errorMessage = "Login failed! Server error occurred. Please try again.";
                } else {
                    errorMessage = "Login failed! Unknown error: " + loginResult;
                }
                
                JOptionPane.showMessageDialog(this, errorMessage, "Login Failed", JOptionPane.ERROR_MESSAGE);
                log("Login failed for user: " + enteredUsername + " - Reason: " + loginResult);
            }
        } catch (Exception e) {
            log("Login error: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Login error: " + e.getMessage(), "Login Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String determineUserType(String username) {
        if (username.toLowerCase().contains("admin") || username.equals("admin")) {
            return "admin";
        }
        return "player";
    }

    private void updateUserInfo() {
        Component[] components = ((JPanel) mainPanel.getComponent(0)).getComponents();
        if (components.length > 0 && components[0] instanceof JLabel) {
            JLabel userInfoLabel = (JLabel) components[0];
            userInfoLabel.setText("   Logged in as: " + username);
        }    
    }    
    
    // Player methods
    private void startGame() {
        try {
            log("Starting game...");
            String[] result = gameService.startGame(username);
            
            if (result.length > 0) {
                if (result[0].equals("SUCCESS")) {                    
                    log("Game Status: " + result[1]);
                    if (result.length > 2) {
                        gameId = result[2]; // Store the game ID
                        log("Game ID: " + gameId);
                        gameInfoLabel.setText("Game Status: In game - " + gameId);
                    }if (result[1].contains("Game started")) {
                        // Game started immediately (joining player scenario) - switch to game panel
                        inGame = true;
                        isWaitingForMatch = false;
                        hasShownGameStartCountdown = false; // Reset countdown flag for new game
                        cardLayout.show(contentPanel, "PLAYER_GAME");
                        
                        // Show 3-second countdown for joining player too
                        log("Joining player - showing game start countdown...");
                        hasShownGameStartCountdown = true;
                        
                        // Start status polling immediately for joining player (before countdown)
                        startGameStatusPolling();
                        showGameStartCountdown();
                    } else if (result[1].contains("Waiting for another player")) {
                        // Player is waiting for match - start countdown
                        inGame = true;
                        isWaitingForMatch = true;
                        hasShownGameStartCountdown = false; // Reset countdown flag for new game
                        cardLayout.show(contentPanel, "PLAYER_GAME");
                        gameInfoLabel.setText("Game Status: " + result[1]);
                        // Get wait time from server and start countdown
                        startMatchmakingCountdown();
                        startGameStatusPolling();
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to start game: " + result[1], "Game Error", JOptionPane.ERROR_MESSAGE);
                    log("Failed to start game: " + result[1]);
                }
            } else {
                log("No response from server");
                JOptionPane.showMessageDialog(this, "No response from server.", "Game Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            log("Error starting game: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error starting game: " + e.getMessage(), "Game Error", JOptionPane.ERROR_MESSAGE);
        }
    }    private void startGameStatusPolling() {
        // Check if timer is already running to avoid conflicts
        Timer existingTimer = (Timer) gamePanel.getClientProperty("statusTimer");
        if (existingTimer != null && existingTimer.isRunning()) {
            log("Status polling already running");
            return;
        }
        
        Timer timer = new Timer(1000, e -> updateGameStatus());
        timer.start();
          // Store timer reference to stop it later
        gamePanel.putClientProperty("statusTimer", timer);
        log("Started new status polling timer");
    }    
    
    private void updateGameStatus() {
        if (!inGame) {
            Timer timer = (Timer) gamePanel.getClientProperty("statusTimer");
            if (timer != null) {
                timer.stop();
            }
            return;
        }
        try {
            String status = gameService.getPartialWord(username);
            if (status != null && !status.isEmpty()) {                
                // Check if match was found (game actually started) while waiting
                if (isWaitingForMatch && !status.contains("Waiting") && !status.contains("No active game")) {
                    // Match found! Stop countdown timer and start game countdown
                    if (matchmakingTimer != null) {
                        matchmakingTimer.stop();
                        matchmakingTimer = null;
                    }
                    isWaitingForMatch = false;                    
                    log("Match found! Starting game countdown...");
                    
                    // Update the top status bar to show game is active with game ID
                    if (gameId != null) {
                        gameInfoLabel.setText("Game Status: In game - " + gameId);
                    } else {
                        gameInfoLabel.setText("Game Status: In game");
                    }
                    
                    // Show 3-second countdown before game starts
                    if (!hasShownGameStartCountdown) {
                        hasShownGameStartCountdown = true;
                        showGameStartCountdown();
                    }
                }
                  // Only update display if not waiting for match (prevents countdown from overwriting)
                if (!isWaitingForMatch) {
                    gameStatusArea.setText(status);
                    gameStatusArea.setCaretPosition(gameStatusArea.getDocument().getLength());
                }
                
                // Check if player reached maximum wrong guesses
                if (status.contains("You got 5 letters wrong! Waiting for the other player's round result")) {
                    // Disable input controls since player can't guess anymore this round
                    letterInputField.setEnabled(false);
                    guessButton.setEnabled(false);
                    log("Maximum wrong guesses reached - waiting for other player...");
                } else {
                    // Re-enable input controls if they were disabled
                    letterInputField.setEnabled(true);
                    guessButton.setEnabled(true);
                }
                
                // Check if game just started for any player (including the one who joined)
                if (status.contains("Game started") && !status.contains("Waiting") && !hasShownGameStartCountdown) {
                    // This handles the case where a player joins an existing lobby
                    // and needs to see the countdown too
                    log("Game started detected - showing countdown...");
                    hasShownGameStartCountdown = true;
                    showGameStartCountdown();
                }                // Check if round expired (but game continues)
                if (status.contains("Round expired")) {
                    // Round expired, but game continues - just log it
                    log("Round expired - waiting for next round to start");
                    // Don't end the game, just continue with normal status display
                }// Check if game ended with winner/loser
                if (status.contains("Congratulations! You won the game!") || 
                    (status.contains("Game finished. Winner:") && status.contains("(3/3 rounds)"))) {
                    inGame = false;
                    gameId = null; // Clear game ID
                    hasShownGameStartCountdown = false; // Reset flag when game ends
                    
                    // Stop the timer
                    Timer timer = (Timer) gamePanel.getClientProperty("statusTimer");
                    if (timer != null) {
                        timer.stop();
                    }
                    
                    // Show winner/loser popup window
                    SwingUtilities.invokeLater(() -> {
                        String dialogTitle;
                        String message;
                        
                        if (status.contains("Congratulations! You won the game!")) {
                            dialogTitle = "Victory!";
                            message = "Congratulations!\n\nYou won the game by winning 3 rounds!\n\nWell played!";                        } else {
                            // Extract winner name from status message
                            String winnerName = "Unknown";
                            if (status.contains("Winner: ")) {
                                int startIndex = status.indexOf("Winner: ") + 8;
                                int endIndex = status.indexOf(".", startIndex); // Look for the period after winner name
                                if (startIndex > 7 && endIndex > startIndex) {
                                    winnerName = status.substring(startIndex, endIndex);
                                }
                            }
                            dialogTitle = "Game Over";
                            message = "Game Over!\n\n" + winnerName + " won the game with 3 rounds.\n\nBetter luck next time!";
                        }
                        
                        java.lang.Object[] options = {"Return to Main Menu"};
                        JOptionPane.showOptionDialog(this,
                            message,
                            dialogTitle, 
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.INFORMATION_MESSAGE,
                            null,
                            options,
                            options[0]);
                        
                        // Automatically return to main menu
                        try {
                            gameService.quitGame(username);
                        } catch (Exception e) {
                            log("Error quitting game: " + e.getMessage());
                        }
                        
                        cardLayout.show(contentPanel, "PLAYER_MENU");
                        gameInfoLabel.setText("Game Status: Not in game");
                        gameStatusArea.setText("");
                        letterInputField.setText("");
                    });
                    return; // Exit early to avoid other checks
                }
                
                // Check if game ended (other conditions like "Not in a game")
                if (status.contains("Not in a game")) {
                    inGame = false;
                    gameId = null; // Clear game ID
                    hasShownGameStartCountdown = false; // Reset flag when game ends
                    gameInfoLabel.setText("Game Status: Game completed");
                    
                    // Stop the timer
                    Timer timer = (Timer) gamePanel.getClientProperty("statusTimer");
                    if (timer != null) {
                        timer.stop();
                    }
                    
                    // Return to main menu without popup for disconnection scenarios
                    SwingUtilities.invokeLater(() -> {
                        cardLayout.show(contentPanel, "PLAYER_MENU");
                        gameInfoLabel.setText("Game Status: Not in game");
                        gameStatusArea.setText("");
                        letterInputField.setText("");
                    });
                }
            }
        } catch (Exception ex) {
            log("Error updating game status: " + ex.getMessage());
        }
    }
    private void makeGuess() {
        if (!inGame) {
            JOptionPane.showMessageDialog(this, "You are not in a game!", "Not In Game", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String letter = letterInputField.getText().trim().toLowerCase();
        if (letter.isEmpty() || letter.length() != 1 || !Character.isLetter(letter.charAt(0))) {
            JOptionPane.showMessageDialog(this, "Please enter a single letter.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            letterInputField.setText(""); // Clear input on invalid input
            return;
        }
        try {
            char letterChar = letter.charAt(0);
            boolean success = gameService.guessLetter(username, letterChar);
            
            // Clear input field after every guess, regardless of success or failure
            letterInputField.setText("");
            
            if (success) {
                log("Made guess: " + letter);
            } else {
                log("Failed to make guess: " + letter);
                JOptionPane.showMessageDialog(this, "Failed to make guess. Letter may be wrong or already guessed.", "Guess Failed", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            letterInputField.setText(""); // Clear input on error
            log("Error making guess: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error making guess: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void quitGame() {
        if (!inGame) {
            cardLayout.show(contentPanel, "PLAYER_MENU");
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to quit the current game?", "Quit Game", JOptionPane.YES_NO_OPTION);            
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // Call the server to quit the game
                gameService.quitGame(username);
                inGame = false;
                gameId = null; // Clear game ID
                gameInfoLabel.setText("Game Status: Not in game");
                
                // Stop matchmaking countdown timer if running
                if (matchmakingTimer != null) {
                    matchmakingTimer.stop();
                    matchmakingTimer = null;
                    log("Stopped matchmaking countdown timer");
                }
                
                // Reset waiting state
                isWaitingForMatch = false;
                
                cardLayout.show(contentPanel, "PLAYER_MENU");
                log("Quit current game");
                
                // Stop status timer
                Timer timer = (Timer) gamePanel.getClientProperty("statusTimer");
                if (timer != null) {
                    timer.stop();
                }
                
                // Clear game status area
                gameStatusArea.setText("");
                letterInputField.setText("");
            } catch (Exception e) {
                log("Error quitting game: " + e.getMessage());
            }
        }
    }

    private void viewLeaderboard() {
        try {
            String leaderboard = gameService.getLeaderboard();
            JTextArea textArea = new JTextArea(leaderboard);
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(400, 300));
            
            JOptionPane.showMessageDialog(this, scrollPane, "Leaderboard", JOptionPane.INFORMATION_MESSAGE);
            log("Viewed leaderboard");
        } catch (Exception e) {
            log("Error viewing leaderboard: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error viewing leaderboard: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }    
    
    // Admin methods
    private void createUser() {
        String newUsername = newUsernameField.getText().trim();
        String password = new String(newPasswordField.getPassword());
        
        if (newUsername.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username and password.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            boolean success = gameService.createPlayer(newUsername, password);
            if (success) {
                log("Player created successfully: " + newUsername);
                newUsernameField.setText("");
                newPasswordField.setText("");
                JOptionPane.showMessageDialog(this, "Player created successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                log("Failed to create player: " + newUsername);
                JOptionPane.showMessageDialog(this, "Failed to create player. Username may already exist.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            log("Create user error: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateUser() {
        String updateUsername = updateUsernameField.getText().trim();
        String password = new String(updatePasswordField.getPassword());
        
        if (updateUsername.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username and new password.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            boolean success = gameService.updatePlayer(updateUsername, password);
            if (success) {
                log("Player updated successfully: " + updateUsername);
                updateUsernameField.setText("");
                updatePasswordField.setText("");
                JOptionPane.showMessageDialog(this, "Player updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                log("Failed to update player: " + updateUsername);
                JOptionPane.showMessageDialog(this, "Failed to update player. Player may not exist.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            log("Update player error: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteUser() {
        String deleteUsername = deleteUsernameField.getText().trim();
        
        if (deleteUsername.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username to delete.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }        
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete player: " + deleteUsername + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                boolean success = gameService.deletePlayer(deleteUsername);
                if (success) {
                    log("Player deleted successfully: " + deleteUsername);
                    deleteUsernameField.setText("");
                    JOptionPane.showMessageDialog(this, "Player deleted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    log("Failed to delete player: " + deleteUsername);
                    JOptionPane.showMessageDialog(this, "Failed to delete player. Player may not exist.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                log("Delete player error: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void searchUsers() {
        String searchTerm = searchField.getText().trim();
        
        try {
            String results = gameService.searchPlayer(searchTerm);
            searchResults.setText(results);
            log("Search performed for: " + searchTerm);
        } catch (Exception e) {
            log("Search error: " + e.getMessage());
            searchResults.setText("Error: " + e.getMessage());
        }
    }

    private void updateGameConfig() {
        try {
            int waitTime = Integer.parseInt(waitTimeField.getText().trim());
            int roundTime = Integer.parseInt(roundTimeField.getText().trim());
            boolean success = gameService.setGameConfig(waitTime, roundTime);
            if (success) {
                log("Game configuration updated: wait=" + waitTime + "s, round=" + roundTime + "s");
                JOptionPane.showMessageDialog(this, "Game configuration updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                // Automatically reload the current configuration to confirm the update
                loadCurrentConfig();
            } else {
                log("Failed to update game configuration");
                JOptionPane.showMessageDialog(this, "Failed to update game configuration.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers for time values.", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            log("Config update error: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }    
    private void logout() {
        try {
            if (inGame) {
                // Call the server to quit the game first
                gameService.quitGame(username);
                inGame = false;
                // Stop status timer if running
                Timer timer = (Timer) gamePanel.getClientProperty("statusTimer");
                if (timer != null) {
                    timer.stop();
                }
            }
            
            if (isLoggedIn) {
                gameService.logout(username);
                log(userType.substring(0, 1).toUpperCase() + userType.substring(1) + " " + username + " logged out");
            }
            isLoggedIn = false;
            username = null;
            userType = null;
            gameId = null;
            
            // Switch back to login panel
            remove(mainPanel);
            add(loginPanel);
            
            // Clear login fields
            usernameField.setText("");
            passwordField.setText("");
            
            // Reset title
            setTitle("What's The Word - Game Client");
            
            revalidate();
            repaint();
        } catch (Exception e) {
            log("Logout error: " + e.getMessage());
        }
    }    
    private void loadCurrentConfig() {
        try {
            String config = gameService.getGameConfig();
            
            // Parse the configuration response to extract values
            String[] lines = config.split("\n");
            for (String line : lines) {
                if (line.contains("wait_time:")) {
                    String value = line.split(":")[1].trim().split(" ")[0];
                    waitTimeField.setText(value);
                } else if (line.contains("round_duration:")) {
                    String value = line.split(":")[1].trim().split(" ")[0];
                    roundTimeField.setText(value);
                }
            }
            
            log("Automatically loaded current game configuration from server");
        } catch (Exception e) {
            log("Error automatically loading configuration: " + e.getMessage());
            // Silent loading - no popup on error
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new java.util.Date() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // Matchmaking countdown methods
    private void startMatchmakingCountdown() {
        try {
            // Get wait time from server configuration
            String config = gameService.getGameConfig();
            int waitTime = 10; // Default fallback
            
            // Parse the configuration to get wait_time
            String[] lines = config.split("\n");
            for (String line : lines) {
                if (line.contains("wait_time:")) {
                    String value = line.split(":")[1].trim().split(" ")[0];
                    waitTime = Integer.parseInt(value);
                    break;
                }
            }
            
            startCountdownWithTime(waitTime);
            log("Starting matchmaking countdown: " + waitTime + " seconds");
        } catch (Exception e) {
            log("Error getting wait time from server, using default: " + e.getMessage());
            startDefaultCountdown();
        }
    }
    
    private void startDefaultCountdown() {
        startCountdownWithTime(10); // Default 10 seconds
        log("Starting default matchmaking countdown: 10 seconds");
    }
    
    private void startCountdownWithTime(int seconds) {
        remainingWaitTime = seconds;
        
        // Stop any existing countdown timer
        if (matchmakingTimer != null) {
            matchmakingTimer.stop();
        }
          // Create and start the countdown timer
        matchmakingTimer = new Timer(1000, e -> {
            remainingWaitTime--;
            
            // Only update display if still waiting for match
            if (isWaitingForMatch) {
                // Update the game status area with countdown
                SwingUtilities.invokeLater(() -> {
                    gameStatusArea.setText("Waiting for another player...\n" + "Time remaining: " + remainingWaitTime + " seconds\n" + "Please wait while we find you a match.");
                });
            }
            
            if (remainingWaitTime <= 0) {
                handleMatchmakingTimeout();
            }
        });
        
        matchmakingTimer.start();
    }
    
    private void handleMatchmakingTimeout() {
        // Stop the countdown timer
        if (matchmakingTimer != null) {
            matchmakingTimer.stop();
            matchmakingTimer = null;
        }
        isWaitingForMatch = false;
        inGame = false;
        gameId = null; // Clear game ID
        
        // Stop the status polling timer
        Timer statusTimer = (Timer) gamePanel.getClientProperty("statusTimer");
        if (statusTimer != null) {
            statusTimer.stop();
        }
        
        // Quit the game on the server side
        try {
            gameService.quitGame(username);
            log("Matchmaking timeout - quit game on server");
        } catch (Exception e) {
            log("Error quitting game after timeout: " + e.getMessage());
        }
        
        // Show "No match found" dialog and return to main menu
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                "No match found within the time limit.\nReturning to main menu.",
                "No Match Found",
                JOptionPane.INFORMATION_MESSAGE);
            
            // Reset UI and return to main menu
            cardLayout.show(contentPanel, "PLAYER_MENU");
            gameInfoLabel.setText("Game Status: Not in game");
            gameStatusArea.setText("");
            letterInputField.setText("");
        });
        log("No match found - returned to main menu");
    }    
    // Game start countdown methods
    private void showGameStartCountdown() {
        // Create a non-modal dialog for the countdown
        JDialog countdownDialog = new JDialog(this, "Match Found!", false);
        countdownDialog.setSize(300, 150);
        countdownDialog.setLocationRelativeTo(this);
        countdownDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        countdownDialog.setAlwaysOnTop(true);
        
        // Create countdown display
        JLabel countdownLabel = new JLabel("Game starting in 3 seconds...", JLabel.CENTER);
        countdownLabel.setFont(new Font("Arial", Font.BOLD, 16));
        countdownDialog.add(countdownLabel);
        
        // Show the dialog immediately
        countdownDialog.setVisible(true);
        
        // Start 3-second countdown
        final int[] countdown = {3};
        Timer countdownTimer = new Timer(1000, e -> {
            countdown[0]--;
            if (countdown[0] > 0) {
                countdownLabel.setText("Game starting in " + countdown[0] + " second" + (countdown[0] == 1 ? "" : "s") + "...");
            } else {                
                // Countdown finished
                ((Timer) e.getSource()).stop();
                countdownDialog.dispose();
                
                // Start game status polling only if not already running
                SwingUtilities.invokeLater(() -> {
                    log("Game countdown finished");
                    Timer existingTimer = (Timer) gamePanel.getClientProperty("statusTimer");
                    if (existingTimer == null || !existingTimer.isRunning()) {
                        log("Starting game status polling");
                        startGameStatusPolling();
                    } else {
                        log("Game status polling already running");
                    }
                });
            }
        });
        
        countdownTimer.start();    
    }    
    
    private void startSessionMonitoring() {
        // Check session status every 1 seconds
        sessionCheckTimer = new Timer(1000, e -> {
            try {
                if (isLoggedIn && sessionToken != null && username != null) {
                    String result = gameService.checkSessionStatus(username, sessionToken);
                    
                    if (result.startsWith("DISPLACED:") || result.startsWith("FORCE_LOGOUT:")) {
                        // Stop the timer
                        sessionCheckTimer.stop();
                        
                        // Extract message after the colon
                        String message = result.contains(":") ? result.substring(result.indexOf(":") + 1) : 
                                        "This account has been logged in from another client.";
                        
                        // Show message and logout
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this,
                                message,
                                "Session Terminated",
                                JOptionPane.WARNING_MESSAGE);
                            
                            // Reset session state
                            isLoggedIn = false;
                            sessionToken = null;
                            username = null;
                            userType = null;
                            
                            // Switch back to login panel
                            remove(mainPanel);
                            add(loginPanel);
                            usernameField.setText("");
                            passwordField.setText("");
                            setTitle("What's The Word - Game Client");
                            revalidate();
                            repaint();
                        });
                    }
                }
            } catch (Exception ex) {
                // If we can't check session status, assume connection is lost
                log("Session check failed: " + ex.getMessage());
                sessionCheckTimer.stop();
            }
        });
        
        sessionCheckTimer.start();
    }

    public static void main(String[] args) {
        try {
            // Initialize ORB with command line arguments (from batch file)
            ORB orb = ORB.init(args, null);
            
            SwingUtilities.invokeLater(() -> new GameClient(orb));
        } catch (Exception e) {
            System.err.println("Error initializing ORB: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
