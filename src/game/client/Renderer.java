package game.client;

import game.common.*;
import java.awt.*;

public class Renderer {
    private static final int TILE = Constants.TILE;
    private static final int W = Constants.GAME_W;
    private static final int H = Constants.GAME_H;

    private static final String FONT    = "微软雅黑";
    private static final String FONT_EN = "Arial";

    private static final Color[] CLASS_COLOR = {
        new Color(55,138,221), new Color(127,119,221), new Color(226,75,74)
    };
    private static final String[] CLASS_NAME = {"枪手","魔法师","剑士"};

    private boolean[] wallMap;
    private static final int COLS = W/TILE+1;
    private static final int ROWS = H/TILE+1;

    public Renderer() { buildWalls(); }

    private void buildWalls() {
        wallMap = new boolean[COLS*ROWS];
        for(int c=0;c<COLS;c++){wallMap[c]=true;wallMap[(ROWS-1)*COLS+c]=true;}
        for(int r=0;r<ROWS;r++){wallMap[r*COLS]=true;wallMap[r*COLS+COLS-1]=true;}
        int[][] blocks={{3,3},{3,ROWS-4},{COLS/2,3},{COLS/2,ROWS-4},{COLS-4,3},{COLS-4,ROWS-4},
                        {4,ROWS/2},{COLS-5,ROWS/2},{COLS/2-1,ROWS/2-1},{COLS/2-1,ROWS/2},{COLS/2,ROWS/2-1},{COLS/2,ROWS/2}};
        for(int[] b:blocks) if(b[0]>=0&&b[0]<COLS&&b[1]>=0&&b[1]<ROWS) wallMap[b[1]*COLS+b[0]]=true;
    }

    private void hint(Graphics2D g){
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
    }

    public void render(Graphics2D g, GameState gs, int myId, int tick){
        hint(g);
        drawMap(g);
        for(GameState.EffectState ef:gs.effects) drawEffect(g,ef,tick);
        for(GameState.BulletState b:gs.bullets)  drawBullet(g,b,tick);
        drawPlayer(g,gs.p1,tick);
        drawPlayer(g,gs.p2,tick);
        drawHUD(g,gs,myId,tick);
    }

    // ─── 地图 ─────────────────────────────────────────────────────────────────
    private void drawMap(Graphics2D g){
        for(int r=0;r<ROWS;r++) for(int c=0;c<COLS;c++){
            int px=c*TILE,py=r*TILE;
            if(wallMap[r*COLS+c]) drawWall(g,px,py);
            else drawFloor(g,px,py);
        }
    }
    private void drawFloor(Graphics2D g,int x,int y){
        g.setColor(new Color(18,17,36)); g.fillRect(x,y,TILE,TILE);
        g.setColor(new Color(24,22,48)); g.fillRect(x+1,y+1,TILE-2,TILE-2);
        g.setColor(new Color(28,26,55)); g.drawRect(x,y,TILE,TILE);
    }
    private void drawWall(Graphics2D g,int x,int y){
        g.setColor(new Color(45,42,78)); g.fillRect(x,y,TILE,TILE);
        g.setColor(new Color(68,64,112)); g.fillRect(x+1,y+1,TILE-2,5);
        g.setColor(new Color(72,68,118)); g.fillRect(x+1,y+1,3,TILE-2);
        g.setColor(new Color(22,20,40)); g.fillRect(x+TILE-3,y+1,2,TILE-2);
        g.setColor(new Color(12,10,22)); g.drawRect(x,y,TILE-1,TILE-1);
    }

    // ─── 玩家 ─────────────────────────────────────────────────────────────────
    private void drawPlayer(Graphics2D g, GameState.PlayerState p, int tick){
        if(!p.alive) return;
        if(p.invincible>0&&(p.invincible/4)%2==0) return;
        int x=(int)p.x, y=(int)p.y;
        Color ac=CLASS_COLOR[p.classId];

        if(p.frozenTicks>0){g.setColor(new Color(93,202,165,80));g.fillOval(x-18,y-18,36,36);}
        if(p.burnTicks>0&&tick%6<3){g.setColor(new Color(226,75,74,60));g.fillOval(x-16,y-16,32,32);}

        g.setColor(new Color(0,0,0,55)); g.fillOval(x-11,y+8,22,8);

        double ang=Math.atan2(p.facingY,p.facingX);
        Graphics2D gp=(Graphics2D)g.create();
        hint(gp);
        gp.translate(x,y); gp.rotate(ang);

        int legOff=p.moving?(int)(Math.sin(tick*0.4)*3):0;
        gp.setColor(ac.darker().darker());
        gp.fillRect(-6,6,5,8+legOff); gp.fillRect(2,6,5,8-legOff);
        gp.setColor(ac.darker()); gp.fillRect(-8,-5,16,12);
        gp.setColor(ac); gp.fillRect(-9,-8,7,6); gp.fillRect(3,-8,7,6);
        gp.setColor(new Color(255,220,180)); gp.fillOval(-8,-18,16,16);
        gp.setColor(Color.WHITE); gp.fillRect(-5,-15,4,5); gp.fillRect(2,-15,4,5);
        gp.setColor(new Color(10,10,30)); gp.fillRect(-4,-14,2,3); gp.fillRect(3,-14,2,3);
        gp.setColor(ac.darker()); gp.fillRect(-9,-20,18,7); gp.fillRect(-6,-24,12,5);
        drawWeapon(gp,p.classId,p.shielding);
        if(p.shielding){
            gp.setColor(new Color(ac.getRed(),ac.getGreen(),ac.getBlue(),100));
            gp.setStroke(new BasicStroke(3));
            gp.drawOval(-22,-22,44,44);
            gp.setStroke(new BasicStroke(1));
        }
        gp.dispose();

        // 名字标签
        g.setFont(new Font(FONT,Font.BOLD,12));
        FontMetrics fm=g.getFontMetrics();
        String tag="P"+p.id+" "+CLASS_NAME[p.classId];
        int tw=fm.stringWidth(tag);
        g.setColor(new Color(0,0,0,140));
        g.fillRoundRect(x-tw/2-4,y-44,tw+8,17,4,4);
        g.setColor(ac);
        g.drawString(tag,x-tw/2,y-30);

        // 状态
        g.setFont(new Font(FONT,Font.PLAIN,11));
        int iconX=x+14;
        if(p.frozenTicks>0){g.setColor(new Color(93,202,165));g.drawString("冻",iconX,y-30);iconX+=16;}
        if(p.burnTicks>0)  {g.setColor(new Color(240,100,30)); g.drawString("燃",iconX,y-30);iconX+=16;}
        if(p.stunTicks>0)  {g.setColor(Color.YELLOW);          g.drawString("晕",iconX,y-30);}
    }

    private void drawWeapon(Graphics2D g,int classId,boolean shielding){
        switch(classId){
            case 0:
                g.setColor(new Color(90,90,110)); g.fillRect(9,-3,22,5);
                g.setColor(new Color(130,130,150)); g.fillRect(9,-5,14,2);
                g.setColor(new Color(60,60,80)); g.fillRect(27,-4,6,7);
                break;
            case 1:
                g.setColor(new Color(160,140,220)); g.fillRect(-4,4,4,20);
                g.setColor(new Color(245,196,117)); g.fillOval(-7,22,10,10);
                g.setColor(new Color(180,160,240,140)); g.fillOval(-9,20,14,14);
                break;
            case 2:
                g.setColor(new Color(180,178,169)); g.fillRect(8,-22,5,26);
                g.setColor(new Color(220,200,100)); g.fillRect(5,-26,11,8);
                if(shielding){g.setColor(new Color(60,80,160));g.fillRect(-14,-12,10,20);
                              g.setColor(new Color(100,130,220));g.fillRect(-13,-11,8,18);}
                break;
        }
    }

    // ─── 子弹 ─────────────────────────────────────────────────────────────────
    private void drawBullet(Graphics2D g,GameState.BulletState b,int tick){
        Color c;
        switch(b.type){
            case 1: c=new Color(93,202,165);  break;
            case 5: c=new Color(240,120,30);  break;
            case 4: c=new Color(245,196,117); break;
            default:c=(b.ownerId==1)?new Color(100,180,255):new Color(255,140,100); break;
        }
        double ang=Math.atan2(b.vy,b.vx);
        Graphics2D gb=(Graphics2D)g.create();
        hint(gb);
        gb.translate((int)b.x,(int)b.y); gb.rotate(ang);
        int sz=(b.type==1)?7:5;
        gb.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),90));
        gb.fillOval(-sz-3,-sz-3,(sz+3)*2,(sz+3)*2);
        gb.setColor(c); gb.fillOval(-sz,-sz/2,sz*3,sz);
        gb.setColor(Color.WHITE); gb.fillOval(-sz/2,-sz/4,sz,sz/2);
        gb.dispose();
    }

    // ─── 特效 ─────────────────────────────────────────────────────────────────
    private void drawEffect(Graphics2D g,GameState.EffectState ef,int tick){
        float alpha=(float)ef.life/ef.maxLife;
        switch(ef.type){
            case 0:{
                int r=(int)((1-alpha)*50+15);
                g.setColor(new Color(245,196,117,(int)(120*alpha))); g.fillOval((int)ef.x-r,(int)ef.y-r,r*2,r*2);
                g.setColor(new Color(240,120,30,(int)(180*alpha))); g.fillOval((int)ef.x-r/2,(int)ef.y-r/2,r,r);
            } break;
            case 1:{
                int r=(int)((1-alpha)*60+20);
                g.setColor(new Color(181,212,244,(int)(80*alpha))); g.fillOval((int)ef.x-r,(int)ef.y-r,r*2,r*2);
                g.setColor(new Color(93,202,165,(int)(150*alpha))); g.setStroke(new BasicStroke(2f));
                g.drawOval((int)ef.x-r,(int)ef.y-r,r*2,r*2); g.setStroke(new BasicStroke(1));
            } break;
            case 2:{
                g.setColor(new Color(240,153,123,(int)(220*alpha))); g.setStroke(new BasicStroke(3));
                g.drawLine((int)ef.x-25,(int)ef.y-25,(int)ef.x+25,(int)ef.y+25);
                g.drawLine((int)ef.x+25,(int)ef.y-25,(int)ef.x-25,(int)ef.y+25);
                g.setStroke(new BasicStroke(1));
            } break;
            case 3:{
                Color sc=CLASS_COLOR[ef.ownerId==1?0:2];
                g.setColor(new Color(sc.getRed(),sc.getGreen(),sc.getBlue(),(int)(160*alpha)));
                g.setStroke(new BasicStroke(3)); g.drawOval((int)ef.x-28,(int)ef.y-28,56,56); g.setStroke(new BasicStroke(1));
            } break;
            case 4:{
                g.setColor(new Color(255,255,255,(int)(100*alpha))); g.fillOval((int)ef.x-10,(int)ef.y-10,20,20);
            } break;
        }
    }

    // ─── 顶部HUD ──────────────────────────────────────────────────────────────
    public void drawHUD(Graphics2D g, GameState gs, int myId, int tick){
        hint(g);
        g.setColor(new Color(0,0,0,200)); g.fillRect(0,0,W,52);

        drawPlayerHUD(g,gs.p1,8,myId);
        drawPlayerHUD(g,gs.p2,W-235,myId);

        g.setFont(new Font(FONT_EN,Font.BOLD,18));
        g.setColor(new Color(245,196,117));
        drawCentered(g,"VS",W/2,34);

        if(gs.message!=null&&!gs.message.isEmpty()){
            g.setFont(new Font(FONT,Font.BOLD,14));
            FontMetrics fm=g.getFontMetrics();
            int mw=fm.stringWidth(gs.message);
            g.setColor(new Color(0,0,0,160));
            g.fillRoundRect(W/2-mw/2-10,H-48,mw+20,28,6,6);
            g.setColor(new Color(245,196,117));
            g.drawString(gs.message,W/2-mw/2,H-28);
        }
    }

    private void drawPlayerHUD(Graphics2D g, GameState.PlayerState p, int x, int myId){
        Color ac=CLASS_COLOR[p.classId];
        boolean isMe=(p.id==myId);

        g.setFont(new Font(FONT,Font.BOLD,13));
        g.setColor(isMe?ac:new Color(180,178,200));
        g.drawString("P"+p.id+" "+CLASS_NAME[p.classId]+(isMe?" (你)":""),x,14);

        drawBar(g,x,17,175,10,(int)p.hp,(int)p.maxHp,new Color(226,75,74),new Color(70,20,20));
        g.setFont(new Font(FONT_EN,Font.PLAIN,9));
        g.setColor(Color.WHITE);
        g.drawString((int)p.hp+"/"+(int)p.maxHp,x+2,27);

        drawBar(g,x,30,130,8,(int)p.mp,(int)p.maxMp,new Color(55,138,221),new Color(15,40,80));
        g.setFont(new Font(FONT_EN,Font.PLAIN,9));
        g.setColor(Color.WHITE);
        g.drawString((int)p.mp+"/"+(int)p.maxMp,x+2,38);

        // 技能冷却小条
        SkillDef[] skills=SkillDef.getSkills(p.classId);
        String[] keyLabels={"鼠标","J","K","L/F"};
        for(int i=0;i<4;i++){
            int sx=x+i*58, sy=40;
            int lvl=p.skillLevels[i];
            boolean ready=(p.skillCooldowns[i]==0&&lvl>0);
            g.setColor(lvl>0?(ready?ac:new Color(40,38,68)):new Color(25,23,45));
            g.fillRoundRect(sx,sy,52,8,3,3);
            if(lvl>0&&p.skillCooldowns[i]>0){
                float pct=1f-(float)p.skillCooldowns[i]/skills[i].cooldownBase;
                g.setColor(ac); g.fillRoundRect(sx,sy,(int)(52*pct),8,3,3);
            }
            g.setFont(new Font(FONT_EN,Font.BOLD,8));
            g.setColor(new Color(200,198,230));
            g.drawString(keyLabels[i]+" Lv"+lvl,sx+2,sy+7);
        }

        if(p.skillPoints>0&&isMe){
            g.setFont(new Font(FONT,Font.BOLD,11));
            g.setColor(new Color(245,196,117));
            g.drawString("★技能点×"+p.skillPoints+" 按1234升级",x,H-10);
        }
    }

    private void drawBar(Graphics2D g,int x,int y,int w,int h,int val,int max,Color fill,Color bg){
        g.setColor(bg); g.fillRoundRect(x,y,w,h,3,3);
        int fw=max>0?(int)((float)val/max*w):0;
        if(fw>0){g.setColor(fill);g.fillRoundRect(x,y,fw,h,3,3);}
        g.setColor(new Color(255,255,255,30)); g.drawRoundRect(x,y,w,h,3,3);
    }

    // ─── 底部技能面板 ──────────────────────────────────────────────────────────
    public void drawSkillPanel(Graphics2D g, GameState.PlayerState p, boolean isMe, int panelX, int panelY){
        if(!isMe) return;
        hint(g);
        SkillDef[] skills=SkillDef.getSkills(p.classId);
        Color ac=CLASS_COLOR[p.classId];
        int panW=W, panH=148;

        g.setColor(new Color(10,9,24)); g.fillRect(panelX,panelY,panW,panH);
        g.setColor(new Color(55,52,90)); g.drawLine(panelX,panelY,panelX+panW,panelY);

        // 顶部说明行
        g.setFont(new Font(FONT,Font.BOLD,12));
        g.setColor(new Color(210,208,240));
        g.drawString("技能配置  |  剩余技能点: "+p.skillPoints+"  |  按 1/2/3/4 键升级对应技能",panelX+8,panelY+16);

        g.setFont(new Font(FONT,Font.PLAIN,11));
        g.setColor(new Color(110,108,145));
        g.drawString("操作: WASD移动  鼠标左键攻击  J/K/L/F 使用技能  Shift 护盾",panelX+8,panelY+30);

        // 四个技能卡
        int cardW=168, cardH=110, gap=(panW-8-4*cardW)/3;
        for(int i=0;i<4;i++){
            int cx=panelX+4+i*(cardW+gap);
            int cy=panelY+36;
            int lvl=p.skillLevels[i];
            boolean ready=p.skillCooldowns[i]==0&&lvl>0;
            boolean canUp=p.skillPoints>0&&lvl<skills[i].maxLevel;

            // 卡片
            g.setColor(ready&&lvl>0?new Color(22,20,52):new Color(13,12,30));
            g.fillRoundRect(cx,cy,cardW,cardH,8,8);
            g.setColor(lvl>0?(canUp?new Color(245,196,117):ac):new Color(38,36,65));
            g.setStroke(new BasicStroke(canUp?2f:lvl>0?1.5f:0.8f));
            g.drawRoundRect(cx,cy,cardW,cardH,8,8);
            g.setStroke(new BasicStroke(1));

            // 顶条
            if(lvl>0){g.setColor(ac);g.fillRoundRect(cx+1,cy+1,cardW-2,3,6,6);}

            // 技能名
            g.setFont(new Font(FONT,Font.BOLD,13));
            g.setColor(lvl>0?new Color(230,228,255):new Color(90,88,120));
            g.drawString(skills[i].name,cx+6,cy+18);

            // 按键标签
            String[] kl={"鼠标","[J]","[K]","[L/F]"};
            g.setFont(new Font(FONT_EN,Font.BOLD,10));
            g.setColor(ready&&lvl>0?ac:new Color(70,68,100));
            FontMetrics fm=g.getFontMetrics();
            g.drawString(kl[i],cx+cardW-fm.stringWidth(kl[i])-4,cy+18);

            // 等级圆点
            for(int l=0;l<skills[i].maxLevel;l++){
                g.setColor(l<lvl?ac:new Color(30,28,55));
                g.fillRoundRect(cx+6+l*20,cy+22,16,6,3,3);
            }
            g.setFont(new Font(FONT_EN,Font.BOLD,9));
            g.setColor(new Color(160,158,200));
            g.drawString("Lv."+lvl+"/"+skills[i].maxLevel,cx+6+skills[i].maxLevel*20+4,cy+28);

            // 当前效果描述
            g.setFont(new Font(FONT,Font.PLAIN,11));
            if(lvl>0&&lvl<=skills[i].levelDesc.length){
                g.setColor(new Color(200,198,230));
                drawWrapped(g,skills[i].levelDesc[lvl-1],cx+6,cy+44,cardW-10,13);
            } else if(lvl==0){
                g.setColor(new Color(65,63,95));
                g.drawString("按 ["+(i+1)+"] 分配技能点解锁",cx+6,cy+44);
            }

            // 冷却/就绪条
            if(p.skillCooldowns[i]>0){
                g.setColor(new Color(20,18,45)); g.fillRoundRect(cx+5,cy+cardH-17,cardW-10,9,3,3);
                float pct=1f-(float)p.skillCooldowns[i]/skills[i].cooldownBase;
                g.setColor(ac.darker()); g.fillRoundRect(cx+5,cy+cardH-17,(int)((cardW-10)*pct),9,3,3);
                g.setFont(new Font(FONT,Font.PLAIN,9));
                g.setColor(new Color(150,148,185));
                g.drawString("冷却中...",cx+8,cy+cardH-7);
            } else if(lvl>0){
                g.setColor(ac); g.fillRoundRect(cx+5,cy+cardH-17,cardW-10,9,3,3);
                g.setFont(new Font(FONT,Font.BOLD,9));
                g.setColor(new Color(8,7,20));
                drawCentered(g,"就绪",cx+cardW/2,cy+cardH-7);
            }

            // 升级提示
            if(canUp){
                g.setFont(new Font(FONT,Font.BOLD,10));
                g.setColor(new Color(245,196,117));
                String upTip="▲ 按["+(i+1)+"]升级";
                g.drawString(upTip,cx+cardW-fm.stringWidth(upTip)-4,cy+cardH-5);
            }
        }
    }

    private void drawCentered(Graphics2D g,String s,int cx,int y){
        FontMetrics fm=g.getFontMetrics();
        g.drawString(s,cx-fm.stringWidth(s)/2,y);
    }

    private void drawWrapped(Graphics2D g,String text,int x,int y,int maxW,int lineH){
        if(text==null||text.isEmpty()) return;
        String[] chars=text.split("");
        StringBuilder line=new StringBuilder();
        int cy=y;
        for(String ch:chars){
            String test=line+ch;
            if(g.getFontMetrics().stringWidth(test)>maxW&&line.length()>0){
                g.drawString(line.toString(),x,cy);
                cy+=lineH; line=new StringBuilder(ch);
                if(cy>y+lineH*3) return;
            } else line.append(ch);
        }
        if(line.length()>0) g.drawString(line.toString(),x,cy);
    }
}