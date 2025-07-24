CREATE DATABASE IF NOT EXISTS game_db;
USE game_db;

-- Drop table if exists to recreate with correct structure
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    user_type ENUM('player', 'admin') DEFAULT 'player',
    wins INT DEFAULT 0,
    session_token VARCHAR(4) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Drop game configuration table if exists to recreate
DROP TABLE IF EXISTS game_config;

-- Create game configuration table
CREATE TABLE game_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(50) UNIQUE NOT NULL,
    config_value INT NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Insert default game configuration values
INSERT INTO game_config (config_name, config_value, description) VALUES
('wait_time', 10, 'Wait time for players to join a game (seconds)'),
('round_duration', 30, 'Duration of each game round (seconds)');

-- Insert some default users for testing
INSERT INTO users (username, password, user_type, wins) VALUES
('admin', 'admin123', 'admin', 0),
('player1', 'pass123', 'player', 0),
('player2', 'pass123', 'player', 0),
('player3', 'pass123', 'player', 0),
('player4', 'pass123', 'player', 0),
('player5', 'pass123', 'player', 0);

-- Display the created tables
SELECT * FROM users;
SELECT * FROM game_config;
