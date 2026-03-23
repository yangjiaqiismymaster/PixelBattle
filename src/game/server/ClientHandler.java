package game.server;

import game.common.*;
import java.io.*;
import java.net.*;

public class ClientHandler extends Thread {
    private Socket socket;
    private int playerId;
    private GameServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket, int playerId, GameServer server) throws IOException {
        this.socket = socket;
        this.playerId = playerId;
        this.server = server;
        this.setDaemon(true);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
    }

    public void close() {
        try { socket.close(); } catch(Exception ignored) {}
    }

    public void send(Packet p) {
        try {
            synchronized(out) {
                out.writeObject(p);
                out.flush();
                out.reset(); // prevent memory leak in long sessions
            }
        } catch(IOException e) { /* client gone */ }
    }

    @Override
    public void run() {
        try {
            in = new ObjectInputStream(socket.getInputStream());
            while(true) {
                Packet p = (Packet) in.readObject();
                handlePacket(p);
            }
        } catch(Exception e) {
            server.onDisconnect(playerId);
        }
    }

    private void handlePacket(Packet p) {
        switch(p.type) {
            case Constants.PKT_CLASS_PICK: server.onClassPick(playerId, p.intVal); break;
            case Constants.PKT_READY:      server.onReady(playerId);               break;
            case Constants.PKT_SKILL_UP:   server.onSkillUp(playerId, p.intVal);   break;
            case Constants.PKT_INPUT:      server.onInput(playerId, p);            break;
            case Constants.PKT_REMATCH:    server.onRematch(playerId);             break;
        }
    }
}