**Grace Period**

**Installation**
You must have the Java Development Kit (JDK) 21 or higher installed on your
computer.

**How to Compile**
Open your terminal or command prompt, navigate to the main project folder (where
the src and res folders are), and run the following command:

javac -d out src/*.java

**How to Run the Game
**
After compiling, run the game using the command for your operating system:

Mac / Linux:

java -cp out:res Main

Windows:

java -cp "out;res" Main

**How to Run the Server (For Multiplayer)**

To play multiplayer, one person must run the server in a separate terminal
window before launching the game. Run this command:

java -cp out GameServer
