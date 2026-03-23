package game.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Sent from server to both clients every tick */
public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    public PlayerState p1 = new PlayerState();
    public PlayerState p2 = new PlayerState();
    public List<BulletState> bullets = new ArrayList<>();
    public List<EffectState> effects = new ArrayList<>();
    public int tick;
    public boolean gameOver;
    public int winnerId;   // 1 or 2
    public String message = "";

    public static class PlayerState implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public float x, y;
        public float hp, maxHp;
        public float mp, maxMp;
        public int classId;     // 0=Gunner 1=Mage 2=Warrior
        public float facingX = 1, facingY;
        public boolean moving;
        public int invincible;
        public boolean shielding;
        public int[] skillLevels = new int[4];  // per-skill upgrade level
        public int skillPoints;
        public int[] skillCooldowns = new int[4];
        public int kills;
        public boolean alive = true;
        // Status effects
        public int frozenTicks;
        public int slowTicks;
        public int burnTicks;
        public int stunTicks;
    }

    public static class BulletState implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id, ownerId;
        public float x, y, vx, vy;
        public int type;   // 0=normal 1=magic 2=blade 3=special
        public int dmg;
        public boolean alive = true;
    }

    public static class EffectState implements Serializable {
        private static final long serialVersionUID = 1L;
        public float x, y;
        public int type; // 0=explosion 1=ice 2=slash 3=shield 4=buff
        public int life, maxLife;
        public int ownerId;
    }
}
