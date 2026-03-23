package game.common;

import java.io.Serializable;

public class Packet implements Serializable {
    private static final long serialVersionUID = 1L;

    public int type;
    public int intVal;
    public float floatVal;
    public float x, y;
    public boolean boolVal;
    public String strVal = "";
    public GameState state;
    // Input snapshot
    public boolean up, down, left, right;
    public boolean skill0, skill1, skill2, skill3;
    public boolean shield;
    public float aimX, aimY;
    public boolean attacking;

    public Packet(int type) { this.type = type; }

    public static Packet hello(int playerId) {
        Packet p = new Packet(Constants.PKT_HELLO);
        p.intVal = playerId; return p;
    }
    public static Packet classPick(int classId) {
        Packet p = new Packet(Constants.PKT_CLASS_PICK);
        p.intVal = classId; return p;
    }
    public static Packet ready() { return new Packet(Constants.PKT_READY); }
    public static Packet skillUp(int skillIndex) {
        Packet p = new Packet(Constants.PKT_SKILL_UP);
        p.intVal = skillIndex; return p;
    }
    public static Packet chat(String msg) {
        Packet p = new Packet(Constants.PKT_CHAT);
        p.strVal = msg; return p;
    }
    public static Packet rematch() { return new Packet(Constants.PKT_REMATCH); }
}
