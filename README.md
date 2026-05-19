# Grace Period

**Grace Period** is a Java-based multiplayer maze survival game where players race through a **UPLB campus-inspired maze**, collect abilities, avoid traps, and reach the goal before time runs out.

Built using **Java** with support for both **single-player** and **multiplayer** gameplay.

---

## Game Specifications

| Specification | Details |
|---|---|
| Genre | Maze / Survival |
| Platform | Desktop |
| Language | Java |
| GUI Framework | Java Swing |
| Multiplayer | UDP Networking |
| Default Port | `9877` |
| Java Version | Java SDK 25 or newer |

---

## Features

- Single-player mode
- Multiplayer LAN/IP mode
- Randomly generated maze
- Character selection
- Dash mechanic
- Abilities and traps
- Countdown timer
- Multiplayer lobby and chat
- Fullscreen gameplay

---

## Controls

| Key | Action |
|---|---|
| `WASD` / Arrow Keys | Move |
| `Space` | Dash |
| `Enter` | Confirm / Send Chat |
| `Esc` | Back / Exit |
| `T` | Open Chat |
| `Q / E` | Change Character |

---

## How to Play

### Goal

Reach the end of the maze before the timer runs out while avoiding traps and obstacles. In multiplayer mode, players compete to finish the maze first.

### Single Player

1. Launch the game
2. Select **1 Player**
3. Choose your character
4. Navigate through the maze
5. Collect abilities and avoid traps
6. Reach the goal before time expires

### Multiplayer

1. Launch the game
2. Select **Multiplayer**
3. Host or join a server
4. Wait for all players to join
5. Race through the maze and reach the finish first

---

## Project Structure

```text
grace_period/
├── src/
│   ├── Main.java
│   ├── GamePanel.java
│   ├── GameServer.java
│   ├── NetworkClient.java
│   ├── Player.java
│   ├── Ability.java
│   ├── AbilityManager.java
│   ├── MapManager.java
│   └── MazeGenerator.java
├── res/
└── README.md
```

> Keep the `res` folder in the project root to ensure assets load properly.

---

## Installation

### 1. Install Java

Install **Java SDK 25** or a newer compatible version.

Check if Java is installed:

```bash
java -version
javac -version
```

---

### 2. Clone or Download the Project

```bash
git clone <repository-url>
cd grace_period
```

---

## Compile and Run

### Windows

Compile the game:

```bash
mkdir build

javac -d build src/Main.java src/GamePanel.java src/GameServer.java src/NetworkClient.java src/Player.java src/MapManager.java src/MazeGenerator.java src/AbilityManager.java src/Ability.java
```

Create the JAR file:

```bash
jar cfe GracePeriod.jar Main -C build .
```

Run the game:

```bash
java -jar GracePeriod.jar
```

---

### macOS / Linux

Compile:

```bash
mkdir -p build

javac -d build src/*.java
```

Run:

```bash
java -cp build Main
```

---

## Multiplayer Setup

### Host Server

1. Launch the game
2. Select **Multiplayer**
3. Choose **Create Server**
4. Add your name
5. Share your IP address with other players
6. Start the match when everyone joins

### Join Server

1. Launch the game
2. Select **Multiplayer**
3. Choose **Join Server**
4. Add your name
5. Enter the host IP address
6. Wait for the host to start the game

### Multiplayer Notes

- Default UDP Port: `9877`
- Players should be on the same network or accessible through IP
- Firewall settings may need adjustment

---

## Screenshots

**Main Menu**
<img width="1919" height="1073" alt="Screenshot 2026-05-19 091924" src="https://github.com/user-attachments/assets/b56607db-4870-4ef8-ad1e-0fa71a347c3e" />

**Character Selection**
<img width="1919" height="1079" alt="Screenshot 2026-05-19 092034" src="https://github.com/user-attachments/assets/a0d5fb1a-5c20-4000-aabe-914e6a9ee6b2" />

**Gameplay**
https://github.com/user-attachments/assets/9bb00e87-382e-4b1b-89cb-770990e513fe
<img width="1262" height="628" alt="image" src="https://github.com/user-attachments/assets/cba749ce-6959-4568-9f0f-c5b081a57178" />


**Multiplayer Lobby**
<img width="1919" height="1077" alt="Screenshot 2026-05-19 092352" src="https://github.com/user-attachments/assets/32f04a05-bf23-4623-91f0-49a491640d68" />



**Win/Game Over Screen**
<img width="1152" height="608" alt="image" src="https://github.com/user-attachments/assets/bc1cbdb1-272a-410f-9960-49b3eeb51890" />



## Troubleshooting

### Assets Not Loading

Make sure:
- The `res` folder is in the project root
- You are running the game from the root directory

### Multiplayer Connection Issues

Check:
- Correct IP address
- Port `9877` is not blocked
- Host already created the server

---

## Credits

Developed as a project for CMSC 137.

Developed by:
- Esquivel, Yzza Veah
- Reyes, Timothy Josef
- Villa, Charles Henrico

- CMSC 137 - B1L
