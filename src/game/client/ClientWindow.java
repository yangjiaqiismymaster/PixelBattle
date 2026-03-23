package game.client;

import game.common.Constants;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ClientWindow extends JFrame {
    private GamePanel panel;

    public ClientWindow(String host) {
        setTitle("像素对战 — 联机版");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        panel = new GamePanel(host);
        add(panel);
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(700, 600));

        // 用全局键盘监听，彻底解决焦点问题
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if(e.getID() == KeyEvent.KEY_PRESSED)  panel.keyPressed(e.getKeyCode());
            if(e.getID() == KeyEvent.KEY_RELEASED)  panel.keyReleased(e.getKeyCode());
            return false;
        });

        setFocusable(true);
        SwingUtilities.invokeLater(() -> { toFront(); requestFocusInWindow(); });
    }
}