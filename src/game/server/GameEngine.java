package game.server;

import game.common.*;
import java.util.*;

/**
 * Authoritative server-side simulation.
 * Runs at 60 ticks/sec, owns all game state.
 */
public class GameEngine {

    private GameState state = new GameState();
    private Packet inputP1 = new Packet(Constants.PKT_INPUT);
    private Packet inputP2 = new Packet(Constants.PKT_INPUT);
    private int bulletIdCounter = 0;
    private Random rng = new Random();
    private boolean[] wallMap; // simple grid
    private static final int COLS = Constants.GAME_W / Constants.TILE + 1;
    private static final int ROWS = Constants.GAME_H / Constants.TILE + 1;
    private long[] lastSkillUse = new long[8]; // [pid*4 + skillIdx]

    public GameEngine(int class1, int class2, int[] skillLevels1, int[] skillLevels2) {
        buildWalls();
        // 修复BUG：将两边玩家的出生点向外稍微平移（由 150 改为 90），防止与生成的方块墙壁重叠
        initPlayer(state.p1, 1, class1, 90, Constants.GAME_H/2f, skillLevels1);
        initPlayer(state.p2, 2, class2, Constants.GAME_W - 90, Constants.GAME_H/2f, skillLevels2);
    }

    private void initPlayer(GameState.PlayerState p, int id, int classId, float x, float y, int[] levels) {
        p.id = id; p.classId = classId; p.x = x; p.y = y; p.alive = true;
        switch (classId) {
            case 0: p.maxHp=350; p.maxMp=220; break; // Gunner
            case 1: p.maxHp=250; p.maxMp=320; break; // Mage
            case 2: p.maxHp=500; p.maxMp=160; break; // Warrior
        }
        // Apply passive skill bonuses
        applyPassives(p, levels);
        p.hp = p.maxHp; p.mp = p.maxMp;
        p.skillLevels = Arrays.copyOf(levels, 4);
        p.skillPoints = 0;
        Arrays.fill(p.skillCooldowns, 0);
    }

    private void applyPassives(GameState.PlayerState p, int[] levels) {
        if (p.classId == 0) { // Gunner passive: armor
            float dr = new float[]{0f, 0.10f, 0.20f, 0.30f}[levels[3]];
            p.maxHp *= (1 + dr * 0.3f);
        } else if (p.classId == 2) { // Warrior passive: steel will
            float bonus = new float[]{0f, 0.15f, 0.25f, 0.40f}[levels[2]];
            p.maxHp *= (1 + bonus);
        }
    }

    private void buildWalls() {
        wallMap = new boolean[COLS * ROWS];
        // Border
        for (int c=0;c<COLS;c++) { wallMap[c]=true; wallMap[(ROWS-1)*COLS+c]=true; }
        for (int r=0;r<ROWS;r++) { wallMap[r*COLS]=true; wallMap[r*COLS+COLS-1]=true; }
        // Interior blocks (symmetric)
        int[][] blocks = {{3,3},{3,ROWS-4},{COLS/2,3},{COLS/2,ROWS-4},{COLS-4,3},{COLS-4,ROWS-4},
                {4,ROWS/2},{COLS-5,ROWS/2},{COLS/2-1,ROWS/2-1},{COLS/2-1,ROWS/2},{COLS/2,ROWS/2-1},{COLS/2,ROWS/2}};
        for (int[] b : blocks) {
            if(b[0]>=0&&b[0]<COLS&&b[1]>=0&&b[1]<ROWS) wallMap[b[1]*COLS+b[0]]=true;
        }
    }

    public boolean isWall(float x, float y) {
        int c=(int)(x/Constants.TILE), r=(int)(y/Constants.TILE);
        if(c<0||c>=COLS||r<0||r>=ROWS) return true;
        return wallMap[r*COLS+c];
    }

    public boolean isWallCircle(float cx, float cy, float radius) {
        int x1=(int)((cx-radius)/Constants.TILE), x2=(int)((cx+radius)/Constants.TILE);
        int y1=(int)((cy-radius)/Constants.TILE), y2=(int)((cy+radius)/Constants.TILE);
        for(int r=y1;r<=y2;r++) for(int c=x1;c<=x2;c++){
            if(r<0||r>=ROWS||c<0||c>=COLS||!wallMap[r*COLS+c]) continue;
            float wx=c*Constants.TILE, wy=r*Constants.TILE;
            float nx=Math.max(wx,Math.min(cx,wx+Constants.TILE));
            float ny=Math.max(wy,Math.min(cy,wy+Constants.TILE));
            float dx=nx-cx,dy=ny-cy;
            if(dx*dx+dy*dy<radius*radius) return true;
        }
        return false;
    }

    public void applyInput(int playerId, Packet input) {
        if (playerId == 1) inputP1 = input;
        else inputP2 = input;
    }

    public void tick() {
        state.tick++;
        updatePlayer(state.p1, inputP1, state.p2);
        updatePlayer(state.p2, inputP2, state.p1);
        updateBullets();
        updateEffects();
        regenMp();
        updateSkillCooldowns();
        checkGameOver();
    }

    private void updatePlayer(GameState.PlayerState p, Packet input, GameState.PlayerState opp) {
        if (!p.alive) return;
        if (p.stunTicks > 0) { p.stunTicks--; return; }
        if (p.frozenTicks > 0) { p.frozenTicks--; return; }

        float spd = baseSpeed(p.classId);
        if (p.slowTicks > 0) { spd *= 0.45f; p.slowTicks--; }

        float dx=0,dy=0;
        if(input.left) dx=-1; if(input.right) dx=1;
        if(input.up)   dy=-1; if(input.down)  dy=1;
        if(dx!=0&&dy!=0){dx*=0.707f;dy*=0.707f;}

        float nx=p.x+dx*spd, ny=p.y+dy*spd;
        float r=12;
        if(!isWallCircle(nx,p.y,r)) p.x=Math.max(r,Math.min(Constants.GAME_W-r,nx));
        if(!isWallCircle(p.x,ny,r)) p.y=Math.max(r,Math.min(Constants.GAME_H-r,ny));
        p.moving=(dx!=0||dy!=0);

        // Aim direction
        float adx=input.aimX-p.x, ady=input.aimY-p.y;
        float alen=(float)Math.sqrt(adx*adx+ady*ady);
        if(alen>5){p.facingX=adx/alen;p.facingY=ady/alen;}

        if(p.invincible>0) p.invincible--;
        if(p.burnTicks>0){
            p.burnTicks--;
            if(state.tick%20==0) applyDamage(p, 8, false);
        }

        // Shield (warrior / mage reflect)
        p.shielding = input.shield && p.mp > 0 && p.skillCooldowns[3] == 0;

        // Skills
        if(input.skill0) tryUseSkill(p, opp, 0);
        if(input.skill1) tryUseSkill(p, opp, 1);
        if(input.skill2) tryUseSkill(p, opp, 2);
        if(input.skill3) tryUseSkill(p, opp, 3);

        // Auto-attack (basic)
        if(input.attacking && canAutoAttack(p)) {
            doAutoAttack(p);
        }
    }

    private boolean canAutoAttack(GameState.PlayerState p) {
        int key = (p.id-1)*4;
        return p.skillCooldowns[0] == 0;
    }

    private void doAutoAttack(GameState.PlayerState p) {
        int lvl = p.skillLevels[0];
        switch(p.classId) {
            case 0: fireGunnerShot(p, lvl); break;
            case 1: fireMageRay(p, lvl);    break;
            case 2: doWarriorSlash(p, lvl); break;
        }
        p.skillCooldowns[0] = getCooldown(p.classId, 0, lvl);
    }

    // ─── GUNNER attacks ────────────────────────────────────────────────────────
    private void fireGunnerShot(GameState.PlayerState p, int lvl) {
        int count = 3 + lvl; // 3,4,5
        float spreadBase = (float)Math.toRadians(lvl==2 ? 25 : 35);
        boolean pierce = lvl >= 2;
        float baseAngle = (float)Math.atan2(p.facingY, p.facingX);
        for(int i=0;i<count;i++){
            float a = baseAngle + spreadBase*(i-(count-1)/2f)/(count-1);
            GameState.BulletState b = new GameState.BulletState();
            b.id=bulletIdCounter++; b.ownerId=p.id;
            b.x=p.x+p.facingX*15; b.y=p.y+p.facingY*15;
            b.vx=(float)(Math.cos(a)*9); b.vy=(float)(Math.sin(a)*9);
            b.type=0; b.dmg=20+lvl*6; b.alive=true;
            if(pierce) b.type=4; // piercing
            state.bullets.add(b);
        }
        p.mp -= 5;
    }

    // ─── MAGE attacks ──────────────────────────────────────────────────────────
    private void fireMageRay(GameState.PlayerState p, int lvl) {
        GameState.BulletState b = new GameState.BulletState();
        b.id=bulletIdCounter++; b.ownerId=p.id;
        b.x=p.x+p.facingX*16; b.y=p.y+p.facingY*16;
        b.vx=p.facingX*6; b.vy=p.facingY*6;
        b.type=1; b.dmg=30+lvl*12; b.alive=true;
        state.bullets.add(b);
        p.mp -= 8;
    }

    // ─── WARRIOR attacks ───────────────────────────────────────────────────────
    private void doWarriorSlash(GameState.PlayerState p, int lvl) {
        float range = 80+lvl*35f;
        GameState.PlayerState opp = (p.id==1)?state.p2:state.p1;
        float dx=opp.x-p.x, dy=opp.y-p.y;
        float dist=(float)Math.sqrt(dx*dx+dy*dy);
        if(dist<range && opp.alive) {
            float dmg = 40+lvl*18f;
            // Apply passive block
            if(opp.classId==2) dmg*=(1-getWarriorBlock(opp));
            if(opp.classId==0) dmg*=(1-getGunnerArmor(opp));
            boolean knockback = lvl>=1;
            if(knockback){ opp.x+=dx/dist*40; opp.y+=dy/dist*40; }
            boolean knockdown = lvl>=2;
            if(knockdown) opp.stunTicks=30;
            applyDamage(opp,(int)dmg,false);
        }
        // Slash effect
        GameState.EffectState ef = new GameState.EffectState();
        ef.x=p.x+p.facingX*50; ef.y=p.y+p.facingY*50;
        ef.type=2; ef.life=ef.maxLife=20; ef.ownerId=p.id;
        state.effects.add(ef);
        p.mp -= 6;
        p.skillCooldowns[0] = getCooldown(2,0,lvl);
    }

    // ─── SKILL usage ───────────────────────────────────────────────────────────
    private void tryUseSkill(GameState.PlayerState p, GameState.PlayerState opp, int skillIdx) {
        if(p.skillCooldowns[skillIdx]>0) return;
        SkillDef def = SkillDef.getSkills(p.classId)[skillIdx];
        int lvl = p.skillLevels[skillIdx];
        if(lvl==0) return;
        if(p.mp < def.mpCost) return;
        p.mp -= def.mpCost;
        p.skillCooldowns[skillIdx] = getCooldown(p.classId, skillIdx, lvl);
        executeSkill(p, opp, skillIdx, lvl);
    }

    private void executeSkill(GameState.PlayerState p, GameState.PlayerState opp, int idx, int lvl) {
        switch(p.classId) {
            case 0: executeGunnerSkill(p, opp, idx, lvl); break;
            case 1: executeMageSkill(p, opp, idx, lvl);   break;
            case 2: executeWarriorSkill(p, opp, idx, lvl);break;
        }
    }

    // ─── Gunner skills ─────────────────────────────────────────────────────────
    private void executeGunnerSkill(GameState.PlayerState p, GameState.PlayerState opp, int idx, int lvl) {
        switch(idx) {
            case 0: fireGunnerShot(p, lvl); break; // handled via auto-attack path too
            case 1: { // Barrage
                int count = 12+lvl*6;
                for(int i=0;i<count;i++){
                    double a=(double)i/count*Math.PI*2;
                    GameState.BulletState b=new GameState.BulletState();
                    b.id=bulletIdCounter++; b.ownerId=p.id;
                    b.x=p.x; b.y=p.y;
                    float spd = lvl>=2?9:7;
                    b.vx=(float)(Math.cos(a)*spd); b.vy=(float)(Math.sin(a)*spd);
                    b.type=lvl>=2?3:0; b.dmg=25+lvl*8; b.alive=true;
                    if(lvl>=2) b.type=5; // burn-on-hit
                    state.bullets.add(b);
                }
                addEffect(p.x,p.y,0,40,p.id);
            } break;
            case 2: { // Roll dodge
                float rdist=80+lvl*40;
                float nx=p.x+p.facingX*rdist, ny=p.y+p.facingY*rdist;
                if(!isWallCircle(nx,ny,12)){p.x=nx;p.y=ny;}
                p.invincible=20+lvl*10;
                addEffect(p.x,p.y,4,20,p.id);
            } break;
            case 3: break; // passive
        }
    }

    // ─── Mage skills ───────────────────────────────────────────────────────────
    private void executeMageSkill(GameState.PlayerState p, GameState.PlayerState opp, int idx, int lvl) {
        switch(idx) {
            case 0: fireMageRay(p,lvl); break; // handled as auto too
            case 1: { // Fire blast
                float range=80+lvl*40;
                float dx=opp.x-p.x,dy=opp.y-p.y;
                if(Math.sqrt(dx*dx+dy*dy)<range+50) {
                    // AoE at aim position
                    float ax=p.x+p.facingX*140, ay=p.y+p.facingY*140;
                    float ddx=opp.x-ax,ddy=opp.y-ay;
                    if(Math.sqrt(ddx*ddx+ddy*ddy)<range) {
                        applyDamage(opp,(int)(40+lvl*20),false);
                        opp.burnTicks=60+lvl*60;
                    }
                    addEffect(ax,ay,0,35,p.id);
                } else {
                    // Fire anyway at aim
                    float ax=p.x+p.facingX*140, ay=p.y+p.facingY*140;
                    addEffect(ax,ay,0,35,p.id);
                }
            } break;
            case 2: { // Arcane shield — handled in bullet-hit check
                p.shielding=true;
                addEffect(p.x,p.y,3,30+lvl*20,p.id);
                // store shield duration in stunTicks (reuse field for shield end tick)
                // Actually use mp drain to simulate limited duration
                p.mp=Math.max(0,p.mp-5);
            } break;
            case 3: { // Frozen domain
                float range=100+lvl*50;
                float dx=opp.x-p.x,dy=opp.y-p.y;
                if(Math.sqrt(dx*dx+dy*dy)<range) {
                    opp.frozenTicks=60+lvl*60;
                    opp.slowTicks=120+lvl*60;
                    applyDamage(opp,(int)(20+lvl*15),false);
                }
                addEffect(p.x,p.y,1,60,p.id);
            } break;
        }
    }

    // ─── Warrior skills ────────────────────────────────────────────────────────
    private void executeWarriorSkill(GameState.PlayerState p, GameState.PlayerState opp, int idx, int lvl) {
        switch(idx) {
            case 0: doWarriorSlash(p,lvl); break;
            case 1: { // Charge
                float dist=200+lvl*80;
                // Move toward opponent
                float dx=opp.x-p.x,dy=opp.y-p.y;
                float d=(float)Math.sqrt(dx*dx+dy*dy);
                float nx=p.x+dx/d*Math.min(dist,d-20);
                float ny=p.y+dy/d*Math.min(dist,d-20);
                if(!isWallCircle(nx,ny,14)||lvl>=1){p.x=nx;p.y=ny;}
                // Damage on arrival if close
                float ndx=opp.x-p.x,ndy=opp.y-p.y;
                if(Math.sqrt(ndx*ndx+ndy*ndy)<50){
                    applyDamage(opp,(int)(35+lvl*15),false);
                    opp.stunTicks=20+lvl*15;
                }
                addEffect(p.x,p.y,2,25,p.id);
                if(lvl>=2) executeWarriorSkill(p,opp,0,p.skillLevels[0]); // follow with slash
            } break;
            case 2: break; // passive
            case 3: { // Counter shield
                p.shielding=true;
                addEffect(p.x,p.y,3,20+lvl*15,p.id);
                p.skillCooldowns[3]=getCooldown(2,3,lvl);
            } break;
        }
    }

    // ─── Bullet update ─────────────────────────────────────────────────────────
    private void updateBullets() {
        Iterator<GameState.BulletState> it = state.bullets.iterator();
        while(it.hasNext()){
            GameState.BulletState b=it.next();
            if(!b.alive){it.remove();continue;}
            b.x+=b.vx; b.y+=b.vy;
            // Out of bounds / wall
            if(b.x<0||b.x>Constants.GAME_W||b.y<0||b.y>Constants.GAME_H||isWall(b.x,b.y)){
                addEffect(b.x,b.y,0,12,b.ownerId);
                it.remove(); continue;
            }
            // Hit player
            GameState.PlayerState target=(b.ownerId==1)?state.p2:state.p1;
            if(target.alive && target.invincible<=0) {
                float dx=b.x-target.x,dy=b.y-target.y;
                if(Math.sqrt(dx*dx+dy*dy)<16) {
                    boolean deflected = handleBulletHit(b, target);
                    if(!deflected){
                        it.remove();
                    } else {
                        // Reverse bullet owner for reflected
                        b.ownerId = target.id;
                        b.vx=-b.vx*1.2f; b.vy=-b.vy*1.2f;
                    }
                }
            }
        }
    }

    private boolean handleBulletHit(GameState.BulletState b, GameState.PlayerState target) {
        // Check shield/counter
        if(target.shielding) {
            if(target.classId==2) { // Warrior counter-shield → reflect
                int reflectLvl=target.skillLevels[3];
                if(reflectLvl>=1){
                    addEffect(target.x,target.y,3,20,target.id);
                    b.dmg=(int)(b.dmg*(1.5f+reflectLvl*0.5f));
                    return true; // reflect
                }
            }
            if(target.classId==1) { // Mage arcane shield
                int shieldLvl=target.skillLevels[2];
                float absorb=new float[]{0,0.3f,0.6f,0.8f}[shieldLvl];
                int finalDmg=(int)(b.dmg*(1-absorb));
                applyDamage(target,finalDmg,false);
                if(shieldLvl>=2) { // reflect portion
                    b.dmg=(int)(b.dmg*0.5f); return true;
                }
                b.alive=false; return false;
            }
        }
        // Apply damage reductions
        float dmg=b.dmg;
        if(target.classId==0) dmg*=(1-getGunnerArmor(target));
        if(target.classId==2) dmg*=(1-getWarriorBlock(target));
        applyDamage(target,(int)dmg,false);
        // Status effects
        if(b.type==1) { // Mage ice ray → slow/freeze
            int lvl=getShooterSkillLevel(b.ownerId,0);
            if(lvl>=1) target.slowTicks=90;
            if(lvl>=2) target.frozenTicks=90;
            addEffect(target.x,target.y,1,25,b.ownerId);
        }
        if(b.type==5) { // Gunner barrage burn
            target.burnTicks=60;
            addEffect(target.x,target.y,0,20,b.ownerId);
        }
        return false;
    }

    private int getShooterSkillLevel(int ownerId, int skillIdx){
        return (ownerId==1)?state.p1.skillLevels[skillIdx]:state.p2.skillLevels[skillIdx];
    }

    private float getGunnerArmor(GameState.PlayerState p){
        return new float[]{0,0.10f,0.20f,0.30f}[p.skillLevels[3]];
    }
    private float getWarriorBlock(GameState.PlayerState p){
        return new float[]{0,0.10f,0.20f,0.30f}[p.skillLevels[2]];
    }

    private void applyDamage(GameState.PlayerState p, int dmg, boolean trueDmg) {
        p.hp = Math.max(0, p.hp - dmg);
        p.invincible = 10;
        if(p.hp<=0) p.alive=false;
    }

    private void updateEffects() {
        state.effects.removeIf(e -> { e.life--; return e.life<=0; });
    }

    private void regenMp() {
        if(state.tick%4==0){
            regen(state.p1); regen(state.p2);
        }
    }
    private void regen(GameState.PlayerState p){ p.mp=Math.min(p.maxMp,p.mp+1); }

    private void updateSkillCooldowns() {
        for(int i=0;i<4;i++){
            if(state.p1.skillCooldowns[i]>0) state.p1.skillCooldowns[i]--;
            if(state.p2.skillCooldowns[i]>0) state.p2.skillCooldowns[i]--;
        }
    }

    private void checkGameOver() {
        if(!state.p1.alive||!state.p2.alive){
            state.gameOver=true;
            state.winnerId=state.p1.alive?1:2;
        }
    }

    private void addEffect(float x,float y,int type,int life,int owner){
        GameState.EffectState ef=new GameState.EffectState();
        ef.x=x;ef.y=y;ef.type=type;ef.life=ef.maxLife=life;ef.ownerId=owner;
        state.effects.add(ef);
    }

    private float baseSpeed(int classId){
        switch(classId){case 0:return 3.2f;case 1:return 2.8f;default:return 2.4f;}
    }

    private int getCooldown(int classId, int skillIdx, int lvl) {
        int base = SkillDef.getSkills(classId)[skillIdx].cooldownBase;
        return Math.max(5, base - lvl*10);
    }

    public void awardSkillPoint(int playerId){
        GameState.PlayerState p = (playerId==1)?state.p1:state.p2;
        p.skillPoints++;
    }

    public void upgradeSkill(int playerId, int skillIdx){
        GameState.PlayerState p=(playerId==1)?state.p1:state.p2;
        SkillDef def=SkillDef.getSkills(p.classId)[skillIdx];
        if(p.skillPoints>0&&p.skillLevels[skillIdx]<def.maxLevel){
            p.skillPoints--;
            p.skillLevels[skillIdx]++;
        }
    }

    public GameState getState(){ return state; }
}