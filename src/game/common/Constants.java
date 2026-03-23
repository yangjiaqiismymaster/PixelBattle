package game.common;

public class Constants {
    public static final int PORT        = 9527;
    public static final int GAME_W      = 800;
    public static final int GAME_H      = 560;
    public static final int TILE        = 32;
    public static final int FPS         = 60;
    public static final int MAX_SKILLS  = 4;

    // Packet types
    public static final int PKT_HELLO      = 1;
    public static final int PKT_CLASS_PICK = 2;
    public static final int PKT_READY      = 3;
    public static final int PKT_GAME_START = 4;
    public static final int PKT_INPUT      = 5;
    public static final int PKT_STATE      = 6;
    public static final int PKT_SKILL_UP   = 7;
    public static final int PKT_CHAT       = 8;
    public static final int PKT_GAME_OVER  = 9;
    public static final int PKT_REMATCH    = 10;
}
