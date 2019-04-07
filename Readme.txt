1. Compile source code. Navigate to src folder and javac *.java
2. Open 12 terminals
3. Start all server with command
    java Server <serverId>
4. Start all Clients with command
    java Client <clientId> <delay between request in ms>
5. use TRIGGER command in Server 0 terminal to start auto request.
6. In case of deadlock - User server 0 terminal
    RESTART
    RESTART TRIGGER
7. To Change the delay between requests in client. Using client terminal.
    SET_ELAPSE
    <Change delay in ms>
8. Once all the simulation are run - In server 0 terminal you'll see
    ** ALL SIMULATIONS COMPLETED **