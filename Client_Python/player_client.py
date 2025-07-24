import sys
import time
import os
import CORBA
import CosNaming
from GameApp import GameApp
import atexit
import signal

class PythonPlayerClient:    
    def __init__(self):
        self.orb = None
        self.game_service = None
        self.username = None
        self.session_token = None  # Store session token
        self.is_logged_in = False
        self.session_monitoring = False
        self.displaced = False  # Flag for session displacement
        
        # Register cleanup function for when the program exits
        atexit.register(self.cleanup_on_exit)
        # Handle Ctrl+C properly
        signal.signal(signal.SIGINT, self.signal_handler)
    
    def clear_screen(self):
        #Clear the terminal screen
        os.system('cls' if os.name == 'nt' else 'clear')

    def initialize_corba(self, host="localhost", port="1050"):
        # Initialize the CORBA ORB and connect to the naming service
        try:
            # Initialize the ORB with name service host and port (passed as parameters)
            args = ["-ORBInitRef", f"NameService=corbaname::{host}:{port}"]
            self.orb = CORBA.ORB_init(args, CORBA.ORB_ID)
            
            # Get the root naming context
            obj = self.orb.resolve_initial_references("NameService")
            root_context = obj._narrow(CosNaming.NamingContext)
            
            if root_context is None:
                print("ERROR: Failed to narrow the root naming context")
                return False
                # Resolve the game service
            name = [CosNaming.NameComponent("Game", "")]
            try:
                obj = root_context.resolve(name)
            except CosNaming.NamingContext.NotFound:
                print("ERROR: Game service not found in naming service")
                return False
                # Narrow to GameService
            self.game_service = obj._narrow(GameApp.GameService)
            
            if self.game_service is None:
                print("ERROR: Failed to narrow to GameService")
                return False
                
            print("Successfully connected to game service!")
            return True
        except CORBA.Exception as e:
            print(f"CORBA Exception during initialization: {e}")
            return False
        except Exception as e:
            print(f"Error during initialization: {e}")
            return False
    
    def login(self):
        # Handle user login
        username = input("Enter username: ").strip()
        password = input("Enter password: ").strip()
        
        if not username or not password:
            print("Username and password cannot be empty!")
            return
            
        try:
            result = self.game_service.login(username, password)

            if result.startswith("SUCCESS:"):
                # Split the login response to handle both normal and takeover formats
                login_parts = result.split(":")
                
                # Extract session token (always at index 1)
                token = login_parts[1]
                
                # Get user type from server
                user_type = self.game_service.getUserType(username)
                
                if user_type == "admin":
                    print("ERROR: Admin accounts cannot use the Python client!")
                    print("Please use the Java client for admin functions.")
                    self.game_service.logout(username)
                    return
                elif user_type == "player":
                    print(f"Welcome, {username}!")
                    self.username = username
                    self.session_token = token
                    self.is_logged_in = True
                    self.start_session_monitoring()
                    
                    # Check if this is a game takeover scenario
                    is_game_takeover = len(login_parts) >= 5 and login_parts[2] == "GAME_TAKEOVER"
                    
                    if is_game_takeover:
                        # Automatic game takeover - parse game details and load game interface
                        takeover_game_id = login_parts[3]
                        game_status = login_parts[4]
                        
                        print(f"\n*** GAME TAKEOVER DETECTED ***")
                        print(f"Automatically loading game {takeover_game_id} (status: {game_status})")
                        print("Your previous game session is being restored...")
                        
                        # Set up game state for takeover
                        if game_status == "waiting":
                            print("Resuming waiting state - looking for another player...")
                            # Set up timeout tracking for waiting state
                            self.wait_start_time = time.time()
                            self.wait_timeout = self.get_wait_timeout()
                        else:
                            print("Resuming active game...")
                        
                        # Automatically start the game loop without user interaction
                        print("Starting game interface...")
                        self.play_game_loop()
                else:
                    print(f"ERROR: Unknown user type: {user_type}")
                    self.game_service.logout(username)
                    return
            else:
                print(f"Login failed: {result}")
                
        except CORBA.Exception as e:
            print(f"CORBA Exception during login: {e}")
        except Exception as e:            
            print(f"Error during login: {e}")
    
    def logout(self):
        # Handle user logout
        if not self.is_logged_in:
            print("You are not logged in!")
            return
            
        try:
            self.stop_session_monitoring()
            self.game_service.logout(self.username)
            print(f"Goodbye, {self.username}!")
            self.username = None
            self.session_token = None
            self.is_logged_in = False
        except CORBA.Exception as e:
            print(f"CORBA Exception during logout: {e}")
        except Exception as e:
            print(f"Error during logout: {e}")    
            
    def start_session_monitoring(self):
        # Start monitoring session status in a separate thread
        import threading
        self.session_monitoring = True
        self.displaced = False 
        def monitor_session():
            while self.session_monitoring and self.is_logged_in:
                try:
                    if self.session_token and self.username:
                        result = self.game_service.checkSessionStatus(self.username, self.session_token)
                        
                        if result.startswith("DISPLACED:") or result.startswith("FORCE_LOGOUT:"):
                            self.displaced = True  # Set flag FIRST
                            self.session_monitoring = False
                            self.is_logged_in = False
                            
                            # Extract message after the colon
                            message = result.split(":", 1)[1] if ":" in result else "This account has been logged in from another client."
                            print("\n\n*** IMPORTANT ***")
                            print(message)
                            print("You have been logged out.")
                            print("Press Enter to continue...")
                            self.username = None
                            self.session_token = None
                            break
                    # Check every 1 second
                    time.sleep(1)
                        
                except Exception as e:
                    # If session check fails, connection might be lost
                    print(f"\nSession monitoring error: {e}")
                    break
        
        thread = threading.Thread(target=monitor_session, daemon=True)
        thread.start()

    def stop_session_monitoring(self):
        self.session_monitoring = False
    
    def get_wait_timeout(self):
        # Get the wait timeout from the server configuration
        try:
            config = self.game_service.getGameConfig()
            lines = config.split('\n')
            for line in lines:
                if 'wait_time:' in line:
                    # Extract the number before "seconds"
                    parts = line.split(':')[1].strip().split()
                    if parts:
                        return int(parts[0])
            # Default fallback
            return 10
        except Exception as e:
            print(f"Error getting wait timeout from server, using default: {e}")
            return 10
    def start_game(self):
        try:
            result = self.game_service.startGame(self.username)
            if len(result) >= 2:
                status = result[0]
                message = result[1]
                
                if status == "SUCCESS":
                    print(f"Game: {message}")
                    if len(result) >= 3:
                        game_id = result[2]
                        print(f"Game ID: {game_id}")
                    
                    # Check what type of game start this is
                    if "Waiting for another player" in message:
                        print("Creating new lobby - waiting for another player to join...")
                        # Start timeout countdown immediately when waiting
                        self.wait_start_time = time.time()
                        self.wait_timeout = self.get_wait_timeout()
                        print(f"Timeout set to {self.wait_timeout} seconds")
                    elif "Game started" in message:
                        print("Joining existing game - starting immediately!")
                    
                    # Enter the game loop
                    self.play_game_loop()
                else:
                    print(f"Failed to start game: {message}")            
                    
            else:
                print("Invalid response from server")
        except CORBA.Exception as e:
            print(f"CORBA Exception starting game: {e}")
    
    def play_game_loop(self):
        # Main game loop for playing the game
        print(f"\n=== Game Started - Player: {self.username} ===")
        print("Guess letters to complete the word! (Type 'quit' to leave game)")
        print("You need to win 3 rounds to win the game!")
        
        # Wait for game to be ready
        print("Waiting for game to be ready...")
        waiting_for_start = True
        
        while True:            # Check for displacement at start of loop
            if hasattr(self, 'displaced') and self.displaced:
                print(f"\n({self.username}): Game interrupted due to account displacement.")
                print("Returning to main menu...")
                # Clean up game state
                try:
                    if self.username and self.game_service:
                        self.game_service.quitGame(self.username)
                except:
                    pass  # Ignore errors since we're displaced anyway
                break
            # Check if session is still valid
            if not self.username or not self.is_logged_in:
                username_display = self.username if self.username else "Unknown"
                print(f"\n({username_display}): Session expired. Returning to main menu.")
                break
                
            try:
                # Get current game status
                status = self.game_service.getPartialWord(self.username)
                # Clear screen for clean output during waiting
                if waiting_for_start or any(keyword in status.lower() for keyword in ['waiting', 'starting']):
                    self.clear_screen()
                    print(f"\n=== Game Status - Player: {self.username} ===")
                
                # Debug: Show status
                if waiting_for_start:
                    print(f"Current status: '{status}'")
                # Check if user is not in a game
                if (("Not in a game" in status or "No active game" in status) and not waiting_for_start and not ("Waiting" in status or "waiting" in status) and not hasattr(self, 'wait_start_time')):
                    print("You are not currently in a game.")
                    break
                # Handle waiting for another player
                is_waiting_for_player = (
                    "Waiting for another player" in status or 
                    "waiting for another player" in status or
                    "Lobby created" in status or
                    "Game created" in status or
                    (waiting_for_start and "No active game" in status and hasattr(self, 'wait_start_time'))
                )
                
                if is_waiting_for_player:
                    if waiting_for_start:
                        # Check if timeout was already set in start_game
                        if hasattr(self, 'wait_start_time') and hasattr(self, 'wait_timeout'):
                            # Use the timeout values set in start_game
                            elapsed = time.time() - self.wait_start_time
                            remaining = max(0, self.wait_timeout - int(elapsed))
                        else:
                            # Fallback: set timeout now if not already set
                            self.wait_start_time = time.time()
                            self.wait_timeout = self.get_wait_timeout()
                            remaining = self.wait_timeout
                            print(f"WAITING: Another player needed to join... (Timeout in {self.wait_timeout} seconds)")
                        
                        if remaining <= 0:
                            print("\nTIMEOUT: No player joined within the time limit!")
                            print("Returning to main menu...")
                            try:
                                self.game_service.quitGame(self.username)
                            except:
                                pass
                            # Clean up timeout tracking
                            if hasattr(self, 'wait_start_time'):
                                delattr(self, 'wait_start_time')
                            if hasattr(self, 'wait_timeout'):
                                delattr(self, 'wait_timeout')
                            break
                        print(f"WAITING ({self.username}): Another player needed to join...")
                        print(f"COUNTDOWN ({self.username}): Time remaining: {remaining} seconds")
                        print(f"({self.username}): The game will start automatically)")
                        time.sleep(1)
                        continue
                # Handle empty or unexpected status when waiting (lobby creator edge case)
                if waiting_for_start and (status == "" or len(status.strip()) == 0):
                    print(f"WAITING ({self.username}): Game initializing...")
                    time.sleep(1)
                    continue                # Handle game starting countdown
                if any(keyword in status.lower() for keyword in ['starting soon', 'starting in', 'game will start in']):
                    print(f"GAME STARTING ({self.username}): {status}")
                    print(f"({self.username}): Game starting countdown...")
                    
                    # 3 second countdown
                    for i in range(3, 0, -1):
                        print(f"COUNTDOWN ({self.username}): Game starting in {i} seconds...")
                        time.sleep(1)
                    
                    print(f"COUNTDOWN ({self.username}): Game starting NOW!")
                    waiting_for_start = False
                    continue                
                if waiting_for_start and ("_" in status or "Word:" in status or "Round" in status):
                    waiting_for_start = False
                    # Clean up timeout tracking since game started
                    if hasattr(self, 'wait_start_time'):
                        delattr(self, 'wait_start_time')
                    if hasattr(self, 'wait_timeout'):
                        delattr(self, 'wait_timeout')
                    print(f"GAME STARTED ({self.username}): Game has started!")
                
                # Check if round is in countdown - auto-loop during transitions
                if any(keyword in status.lower() for keyword in ['starting next round', 'starting new round']):
                    print(f"ROUND TRANSITION ({self.username}): {status}")
                    time.sleep(1)  # Auto refresh every 1 second during round transitions
                    continue
                
                # Auto-refresh if user is still in waiting states
                if any(keyword in status.lower() for keyword in ['waiting', 'starting']):
                    print(f"STATUS ({self.username}): {status}")
                    time.sleep(1)
                    continue
                # Parse the status to show game info when game is ready
                if not waiting_for_start:
                    print(f"\nPlayer: {self.username} | Game Status: {status}")                # Check for game end conditions
                if "won the game" in status or "Game finished" in status:
                    print(f"\n({self.username}) GAME COMPLETE: Game completed!")
                    input("Press Enter to return to main menu...")
                    self.quit_game()
                    break
                # Get user input for letter guess only when the game is actually ready
                try:
                    guess = input(f"\n({self.username}) Enter a letter (or 'quit' to exit, Enter to refresh): ").strip().lower()
                except (EOFError, KeyboardInterrupt):
                    print(f"\n({self.username}): Leaving game...")
                    self.game_service.quitGame(self.username)
                    break
                if guess == 'quit':
                    print(f"({self.username}): Leaving game...")
                    self.game_service.quitGame(self.username)
                    break
                
                if guess == 'status':
                    continue
                if len(guess) != 1 or not guess.isalpha():
                    print(f"({self.username}): Please enter a single letter!")
                    continue
                # Make the guess
                result = self.game_service.guessLetter(self.username, guess[0])
                if result:
                    print(f"({self.username}) ACCEPTED: Letter '{guess}' accepted!")
                else:
                    print(f"({self.username}) REJECTED: Letter '{guess}' was not accepted (already guessed, wrong, or invalid).")
            except CORBA.Exception as e:
                print(f"CORBA Exception during game: {e}")
                break
            except Exception as e:
                print(f"Error during game: {e}")
                break
        
        # Clean up timeout tracking when leaving game loop
        if hasattr(self, 'wait_start_time'):
            delattr(self, 'wait_start_time')
        if hasattr(self, 'wait_timeout'):
            delattr(self, 'wait_timeout')
    
    def quit_game(self):
        # Handle quitting the game
        if not self.is_logged_in:
            print("You must be logged in!")
            return
            
        try:
            self.game_service.quitGame(self.username)
            print("You have left the current game.")
        except CORBA.Exception as e:
            print(f"CORBA Exception quitting game: {e}")
        except Exception as e:            
            print(f"Error quitting game: {e}")
    
    def view_leaderboard(self):
        try:
            leaderboard = self.game_service.getLeaderboard()
            print("\n=== LEADERBOARD ===")
            print(leaderboard)
        except CORBA.Exception as e:
            print(f"CORBA Exception getting leaderboard: {e}")
        except Exception as e:
            print(f"Error getting leaderboard: {e}")
    
    def show_menu(self):
        # Display the main menu
        print("\n=== What's The Word - Python Client ===")
        if self.is_logged_in:
            print(f"Logged in as: {self.username}")
            print("1. Start/Join Game")
            print("2. View Leaderboard")
            print("3. Logout")
        else:
            print("Please login to access game features")
            print("1. Login")
        print("0. Exit")
        print("=" * 40)
    def run(self, host="localhost", port="1050"):
        # Main client loop
        if not self.initialize_corba(host, port):
            print("Failed to initialize CORBA. Exiting.")
            return
        
        print("Welcome to What's The Word - Python Client!")
        while True:
            self.show_menu()
            choice = input("Enter your choice: ").strip()
            
            if choice == "0":
                if self.is_logged_in:
                    self.logout()
                print("Goodbye!")
                break            
            elif choice == "1":
                if self.is_logged_in:
                    self.start_game()
                else:
                    self.login()
            elif choice == "2":
                if self.is_logged_in:
                    self.view_leaderboard()
                else:
                    print("Please login first to view the leaderboard!")
            elif choice == "3" and self.is_logged_in:
                self.logout()
            else:
                print("Invalid choice! Please try again.")

    def cleanup_on_exit(self):
        # Exit cleanup
        self.stop_session_monitoring()
        if self.is_logged_in and self.username and self.game_service:
            try:
                print(f"\nLogging out {self.username} due to program exit...")
                self.game_service.logout(self.username)
            except Exception as e:
                print(f"Error during exit cleanup: {e}")
    
    def signal_handler(self, sig, frame):
        print("\nReceived interrupt signal, logging out...")
        self.cleanup_on_exit()
        sys.exit(0)

def main():
    import sys
    
    # Parse command line arguments for CORBA host and port
    host = "localhost"  # default
    port = "1050"       # default
    
    # Simple argument parsing for -ORBInitialHost and -ORBInitialPort
    for i, arg in enumerate(sys.argv):
        if arg == "-ORBInitialHost" and i + 1 < len(sys.argv):
            host = sys.argv[i + 1]
        elif arg == "-ORBInitialPort" and i + 1 < len(sys.argv):
            port = sys.argv[i + 1]
    
    client = PythonPlayerClient()
    try:
        client.run(host, port)
    except KeyboardInterrupt:
        print("\nClient interrupted by user")
    except Exception as e:
        print(f"Unexpected error: {e}")
    finally:
        if client.orb:
            client.orb.destroy()

if __name__ == "__main__":
    main()
