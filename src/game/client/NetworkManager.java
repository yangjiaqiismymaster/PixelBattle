package game.client;

import game.common.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class NetworkManager {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Consumer<Packet> onPacket;
    private boolean connected = false;

    public NetworkManager(Consumer<Packet> onPacket) {
        this.onPacket = onPacket;
    }

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());
            connected = true;
            // Start receiver thread
            Thread t = new Thread(this::receiveLoop);
            t.setDaemon(true);
            t.start();
            return true;
        } catch(Exception e) {
            System.out.println("连接失败: " + e.getMessage());
            return false;
        }
    }

    private void receiveLoop() {
        try {
            while(connected) {
                Packet p = (Packet) in.readObject();
                onPacket.accept(p);
            }
        } catch(Exception e) {
            connected = false;
            System.out.println("与服务器断开连接");
        }
    }

    public void send(Packet p) {
        if(!connected) return;
        try {
            synchronized(out) {
                out.writeObject(p);
                out.flush();
                out.reset();
            }
        } catch(IOException e) { connected = false; }
    }

    public boolean isConnected(){ return connected; }
}
