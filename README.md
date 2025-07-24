# CORBA "What's The Word" Game

A distributed multiplayer word guessing game built with CORBA technology featuring Java server, Java/Python clients, and MySQL database.

## System Requirements

### Software Configuration (Tested)
- **Java**: OpenJDK 1.8.0_452 (Zulu 8.86.0.25)
- **Python**: 3.10.11
- **omniORB**: 4.3.0
- **MySQL**: XAMPP (recommended)
- **OS**: Windows 10/11

### Required Dependencies
- **Java**: Included with JDK
- **Python**: omniORB (use `Python_Installer/requirements.txt`)
- **Database**: XAMPP MySQL server

## Quick Setup

### 1. Database Setup (XAMPP)
1. Start XAMPP Control Panel
2. Start **Apache** and **MySQL** services
3. Open phpMyAdmin: http://localhost/phpmyadmin
4. Import database: `mysql_schema.sql`

### 2. Test Accounts
- **Admin**: `admin` / `admin123`
- **Players**: `player1` to `player5` / `pass123`

## Running the System

### Step 1: Start the CORBA Name Service
Run the name service first (this must be running for the server and clients to work):
```cmd
start_nameservice.bat
```
This starts the CORBA naming service on port 1050.

### Step 2: Start the Game Server
Ensure MySQL is running and the database is set up, then:
```cmd
run_server.bat
```
The server will:
- Connect to the MySQL database
- Register itself with the naming service
- Start accepting client connections

### Step 3: Run Clients

#### Java Client (Player & Admin)
```cmd
run_client.bat
```
*This client provides both player and admin functionality in one interface*

#### Python Player Client
```cmd
cd Client_Python
run_client.bat
```

## Compilation (If Needed)

### Compile All Components
```cmd
compile_all.bat
```

### Compile Python CORBA Stubs
```cmd
cd Client_Python
omniidl -bpython -Wbpackage=GameApp ..\CORBA_IDLs\Game.idl
```

## Game Features
- **Player Login**: Login authentication system
- **Word Guessing**: Multiplayer hangman-style gameplay
- **Leaderboards**: Track player wins and rankings
- **Admin Panel**: User management and game configuration
- **Multiple Clients**: Java console, Java GUI, and Python clients

## Session Management & Game Takeover

### Login Override Feature
- **Multiple Login Support**: Users can log in from different clients simultaneously
- **Automatic Session Displacement**: New login automatically displaces existing session
- **Graceful Logout**: Displaced user receives notification and returns to login screen

### Automatic Game Takeover
When a user logs in while already playing a game from another client:
- **Seamless Transition**: New client automatically inherits the ongoing game
- **State Preservation**: All game progress is maintained (guessed letters, scores, timers)
- **No Manual Action**: User doesn't need to click "Start Game" - automatically enters game interface

### Example Scenarios
1. **Player in Lobby**: User waiting for another player → New login takes over the waiting state
2. **Active Game**: User mid-game with opponent → New login continues the same game seamlessly
3. **Cross-Client**: Works between any client types (Java ↔ Python)

### User Experience
- **Displaced User**: "This account has been logged in from another client" → Returns to login
- **New Session**: Automatically shows game interface with current game state
- **Opponent**: Unaware of the takeover - game continues normally

This feature enables users to switch between devices/clients without losing game progress.

## Troubleshooting
1. **"Name Service not found"**: Run `start_nameservice.bat` first
2. **"Database connection failed"**: Check XAMPP MySQL is running
3. **"GameService not found"**: Ensure server started successfully
4. **Python import errors**: Recompile CORBA stubs for Python

## Project Structure
```
├── CORBA_IDLs/Game.idl          # Interface definition
├── Server_Java/                 # Java server implementation
├── Client_Java/                 # Java client implementations  
├── Client_Python/               # Python client implementation
├── Python_Installer/            # Python dependencies
├── mysql_schema.sql             # Database setup
└── *.bat                        # Startup scripts
```
