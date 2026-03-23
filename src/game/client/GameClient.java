package game.client;

import javax.swing.*;

public class GameClient {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        SwingUtilities.invokeLater(() -> {
            ClientWindow win = new ClientWindow(host);
            win.setVisible(true);
        });
    }
}

