package game.client;

import game.common.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class GamePanel extends JPanel implements Runnable, MouseListener, MouseMotionListener {

    private static final int W = Constants.GAME_W;
    private static final int H = Constants.GAME_H;

    // ── Network ────────────────────────────────────────────────────────────────
    private NetworkManager net;
    private String host;
    private int myId = -1;

    // ── State machine ──────────────────────────────────────────────────────────
    private enum Screen { CONNECT, LOBBY, SKILL_PICK, WAITING, GAME, GAME_OVER }
    private volatile Screen screen = Screen.CONNECT;

    // ── Lobby state ────────────────────────────────────────────────────────────
    private int myClass = -1;
    private int oppClass = -1;
    private boolean myReady = false;
    private boolean oppReady = false;
    private int hoverCard = -1;
    private int hoverSkill = -1;

    // ── Skill pick (pre-game) ──────────────────────────────────────────────────
    private int[] mySkillLevels = new int[4];
    private int skillPointsBudget = 6; // 6 points to distribute before game

    // ── In-game ────────────────────────────────────────────────────────────────
    private GameState gameState;
    private boolean[] keys = new boolean[512];
    private float mouseX, mouseY;
    private boolean mouseDown = false;
    private Renderer renderer;
    private int tick = 0;

    // ── Rendering ──────────────────────────────────────────────────────────────
    private BufferedImage buffer;
    private Graphics2D bufG;

    // ── UI helpers ─────────────────────────────────────────────────────────────
    private static final Color[] CLASS_COLOR = {
        new Color(55,138,221), new Color(127,119,221), new Color(226,75,74)
    };
    private static final String[] CLASS_NAME  = {"枪手","魔法师","剑士"};
    private static final String[] CLASS_DESC  = {
        "射速快 · 三连散弹\n技能: 弹幕风暴\nHP:350  MP:220\n\n克制: 近战接近\n被克: 魔法减速",
        "AoE + 控制\n技能: 冰霜/火焰/护盾\nHP:250  MP:320\n\n克制: 枪手风筝\n被克: 近战爆发",
        "高HP · 近战\n技能: 旋风/冲锋/反弹\nHP:500  MP:160\n\n克制: 魔法师\n被克: 枪手弹幕"
    };

    private String statusMsg = "正在连接服务器...";
    private int msgTimer = 0;

    public GamePanel(String host) {
        this.host = host;
        // 放大1.25倍，界面更清晰
        setPreferredSize(new Dimension((int)(W*1.25), (int)((H+150)*1.25)));
        setBackground(Color.BLACK);
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        buffer = new BufferedImage(W, H+150, BufferedImage.TYPE_INT_RGB);
        bufG = buffer.createGraphics();
        bufG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        renderer = new Renderer();
        Thread t = new Thread(this);
        t.setDaemon(true);
        t.start();
        connectToServer();
    }

    private void connectToServer() {
        new Thread(() -> {
            net = new NetworkManager(this::onPacket);
            boolean ok = net.connect(host, Constants.PORT);
            if(ok) { screen = Screen.LOBBY; statusMsg = "已连接，等待对方玩家..."; }
            else   { statusMsg = "连接失败！请确认服务器IP和端口。"; }
        }).start();
    }

    private void onPacket(Packet p) {
        switch(p.type) {
            case Constants.PKT_HELLO:
                // 只接受有效 playerId(1或2)，服务器广播的空包 intVal=0 忽略
                if(p.intVal == 1 || p.intVal == 2) {
                    if(myId == -1) myId = p.intVal;
                }
                // 确保连接成功后显示大厅
                if(screen == Screen.CONNECT) screen = Screen.LOBBY;
                break;
            case Constants.PKT_CLASS_PICK: {
                int who = (int)p.x, cls = p.intVal;
                if(who != myId) oppClass = cls;
                break;
            }
            case Constants.PKT_READY: {
                int who = p.intVal;
                if(who != myId) { oppReady = true; statusMsg = "对方已就绪！"; }
                break;
            }
            case Constants.PKT_GAME_START:
                // 不立即切屏，等第一帧 STATE 数据到了再切，避免 gameState 为 null
                statusMsg = "游戏即将开始...";
                break;
            case Constants.PKT_STATE:
                gameState = p.state;
                tick = p.state != null ? p.state.tick : tick;
                // 收到第一帧数据才真正进入游戏界面
                if(p.state != null && screen != Screen.GAME && screen != Screen.GAME_OVER) {
                    screen = Screen.GAME;
                }
                if(p.state != null && !p.state.message.isEmpty())
                    showMsg(p.state.message);
                if(p.state != null && p.state.gameOver)
                    screen = Screen.GAME_OVER;
                break;
            case Constants.PKT_REMATCH: {
                int who = p.intVal;
                if(who != myId) { showMsg("对方请求重赛！"); }
                // 重置大厅状态
                screen = Screen.LOBBY; myClass=-1; oppClass=-1;
                myReady=false; oppReady=false;
                mySkillLevels=new int[4]; skillPointsBudget=6; gameState=null;
                break;
            }
        }
    }

    private void showMsg(String msg){ statusMsg=msg; msgTimer=200; }

    // ── Game loop ──────────────────────────────────────────────────────────────
    @Override
    public void run() {
        long last=System.nanoTime();
        double ns=1_000_000_000.0/Constants.FPS;
        while(true){
            long now=System.nanoTime();
            if(now-last>=ns){
                last=now;
                if(screen==Screen.GAME) sendInput();
                if(msgTimer>0) msgTimer--;
                repaint();
            }
            try{Thread.sleep(1);}catch(InterruptedException ignored){}
        }
    }

    private void sendInput() {
        if(net==null||!net.isConnected()) return;
        Packet p=new Packet(Constants.PKT_INPUT);
        p.up    =keys[KeyEvent.VK_W]||keys[KeyEvent.VK_UP];
        p.down  =keys[KeyEvent.VK_S]||keys[KeyEvent.VK_DOWN];
        p.left  =keys[KeyEvent.VK_A]||keys[KeyEvent.VK_LEFT];
        p.right =keys[KeyEvent.VK_D]||keys[KeyEvent.VK_RIGHT];
        p.skill0=keys[KeyEvent.VK_J];
        p.skill1=keys[KeyEvent.VK_K];
        p.skill2=keys[KeyEvent.VK_L];
        p.skill3=keys[KeyEvent.VK_SEMICOLON]||keys[KeyEvent.VK_F];
        p.shield=keys[KeyEvent.VK_SHIFT];
        p.attacking=mouseDown;
        p.aimX=mouseX; p.aimY=mouseY;
        net.send(p);
    }

    // ── Paint ──────────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2=bufG;
        g2.setColor(new Color(8,7,20)); g2.fillRect(0,0,W,H+150);

        switch(screen) {
            case CONNECT:    drawConnect(g2); break;
            case LOBBY:      drawLobby(g2);   break;
            case SKILL_PICK: drawSkillPick(g2);break;
            case WAITING:    drawWaiting(g2); break;
            case GAME:       drawGame(g2);    break;
            case GAME_OVER:  drawGame(g2); drawGameOver(g2); break;
        }
        g.drawImage(buffer,0,0,getWidth(),getHeight(),null);
    }

    // ─── Screens ───────────────────────────────────────────────────────────────
    private void drawConnect(Graphics2D g) {
        drawStars(g);
        g.setFont(new Font("Monospaced",Font.BOLD,28));
        g.setColor(new Color(245,196,117));
        drawCentered(g,"像素对战 联机版",W/2,180);
        g.setFont(new Font("Monospaced",Font.PLAIN,14));
        g.setColor(new Color(150,148,180));
        drawCentered(g,statusMsg,W/2,230);
        // Spinner
        int sp=(int)(System.currentTimeMillis()/200%4);
        String[] sps={"|","/","-","\\"};
        g.setColor(new Color(93,202,165));
        g.drawString(sps[sp],W/2+120,230);
    }

    private void drawLobby(Graphics2D g) {
        drawStars(g);
        g.setFont(new Font("Monospaced",Font.BOLD,22));
        g.setColor(new Color(245,196,117));
        drawCentered(g,"选择职业",W/2,55);

        int cardW=180,cardH=260,gap=28;
        int totalW=3*cardW+2*gap, sx=W/2-totalW/2;
        for(int i=0;i<3;i++) drawClassCard(g,sx+i*(cardW+gap),80,cardW,cardH,i);

        // Status
        String myStatus = myClass>=0?(myReady?"✓ 已就绪":"已选: "+CLASS_NAME[myClass]):"请选择职业";
        String opStatus = oppClass>=0?(oppReady?"✓ 已就绪":"已选职业"):"等待对方选择...";
        g.setFont(new Font("Monospaced",Font.PLAIN,12));
        g.setColor(new Color(93,202,165));
        drawCentered(g,"你: "+myStatus,W/4,362);
        g.setColor(new Color(240,153,123));
        drawCentered(g,"对方: "+opStatus,3*W/4,362);

        // Ready button
        if(myClass>=0&&!myReady){
            boolean hover=hoverCard==99;
            g.setColor(hover?new Color(93,202,165):new Color(30,58,45));
            g.fillRoundRect(W/2-70,375,140,36,8,8);
            g.setColor(hover?new Color(8,7,20):new Color(93,202,165));
            g.setFont(new Font("Monospaced",Font.BOLD,14));
            drawCentered(g,"进入技能配置",W/2,399);
        }
        if(myReady){
            g.setColor(new Color(245,196,117));
            g.setFont(new Font("Monospaced",Font.BOLD,13));
            drawCentered(g,"等待对方就绪...",W/2,393);
        }

        // Controls hint
        g.setFont(new Font("Monospaced",Font.PLAIN,10));
        g.setColor(new Color(80,78,110));
        drawCentered(g,"WASD移动 · 鼠标攻击 · J/K/L/F技能 · Shift护盾",W/2,430);
    }

    private void drawClassCard(Graphics2D g,int x,int y,int w,int h,int i){
        boolean hover=(hoverCard==i);
        boolean selected=(myClass==i);
        boolean oppSel=(oppClass==i);
        Color ac=CLASS_COLOR[i];
        // Shadow
        g.setColor(new Color(0,0,0,80)); g.fillRoundRect(x+3,y+3,w,h,10,10);
        // Bg
        g.setColor(selected?new Color(28,26,58):hover?new Color(22,20,45):new Color(14,13,30));
        g.fillRoundRect(x,y,w,h,10,10);
        // Border
        g.setColor(selected?ac:hover?ac.darker():new Color(45,42,75));
        g.setStroke(new BasicStroke(selected?2.5f:1f));
        g.drawRoundRect(x,y,w,h,10,10);
        g.setStroke(new BasicStroke(1));
        // Top strip
        g.setColor(ac); g.fillRoundRect(x+1,y+1,w-2,4,8,8);

        // Hero sprite
        drawHeroSprite(g,x+w/2,y+65,i);

        // Name
        g.setFont(new Font("Monospaced",Font.BOLD,16));
        g.setColor(selected?ac:new Color(220,218,240));
        drawCentered(g,CLASS_NAME[i],x+w/2,y+108);

        g.setColor(new Color(45,42,75)); g.drawLine(x+10,y+115,x+w-10,y+115);

        // Desc
        g.setFont(new Font("Monospaced",Font.PLAIN,9));
        g.setColor(new Color(150,148,180));
        String[] lines=CLASS_DESC[i].split("\n");
        for(int l=0;l<lines.length;l++){
            String line=lines[l];
            if(line.startsWith("克制:")||line.startsWith("被克:"))
                g.setColor(line.startsWith("克制:")?new Color(93,202,165):new Color(240,100,100));
            else g.setColor(new Color(150,148,180));
            drawCentered(g,line,x+w/2,y+130+l*14);
        }

        if(oppSel){
            g.setColor(new Color(240,153,123,160));
            g.setFont(new Font("Monospaced",Font.BOLD,9));
            drawCentered(g,"对方已选",x+w/2,y+h-10);
        }
    }

    private void drawSkillPick(Graphics2D g) {
        drawStars(g);
        g.setFont(new Font("Monospaced",Font.BOLD,20));
        g.setColor(new Color(245,196,117));
        drawCentered(g,"技能配置  (剩余点数: "+skillPointsBudget+")",W/2,48);
        g.setFont(new Font("Monospaced",Font.PLAIN,11));
        g.setColor(new Color(120,118,150));
        drawCentered(g,"点击技能卡片分配技能点，每个技能最多3级",W/2,68);

        if(myClass<0) return;
        SkillDef[] skills=SkillDef.getSkills(myClass);
        int cardW=170,cardH=270,gap=14;
        int totalW=4*cardW+3*gap, sx=W/2-totalW/2;
        for(int i=0;i<4;i++) drawSkillCard(g,sx+i*(cardW+gap),84,cardW,cardH,skills[i],mySkillLevels[i],i);

        // Confirm button
        boolean hover=hoverCard==98;
        g.setColor(hover?new Color(245,196,117):new Color(60,50,20));
        g.fillRoundRect(W/2-80,H-60,160,38,8,8);
        g.setColor(hover?new Color(8,7,20):new Color(245,196,117));
        g.setFont(new Font("Monospaced",Font.BOLD,14));
        drawCentered(g,"确认配置",W/2,H-34);
    }

    private void drawSkillCard(Graphics2D g,int x,int y,int w,int h,SkillDef sk,int lvl,int idx){
        boolean hover=(hoverSkill==idx);
        boolean maxed=(lvl>=sk.maxLevel);
        Color ac=CLASS_COLOR[myClass];
        g.setColor(hover?new Color(22,20,48):new Color(14,13,30));
        g.fillRoundRect(x,y,w,h,8,8);
        g.setColor(lvl>0?ac:hover?new Color(60,58,90):new Color(40,38,70));
        g.setStroke(new BasicStroke(lvl>0?2f:1f));
        g.drawRoundRect(x,y,w,h,8,8);
        g.setStroke(new BasicStroke(1));
        // Icon
        g.setFont(new Font("Monospaced",Font.PLAIN,26));
        g.setColor(lvl>0?ac:new Color(70,68,100));
        drawCentered(g,sk.icon,x+w/2,y+44);
        // Name
        g.setFont(new Font("Monospaced",Font.BOLD,12));
        g.setColor(lvl>0?new Color(220,218,240):new Color(120,118,150));
        drawCentered(g,sk.name,x+w/2,y+62);
        // Type badge
        String[] types={"攻击","防御","机动","特殊"};
        Color[] tColors={new Color(226,75,74),new Color(55,138,221),new Color(93,202,165),new Color(245,196,117)};
        g.setColor(tColors[sk.type]);
        g.fillRoundRect(x+w/2-22,y+66,44,14,4,4);
        g.setColor(new Color(8,7,20));
        g.setFont(new Font("Monospaced",Font.BOLD,8));
        drawCentered(g,types[sk.type],x+w/2,y+76);
        // Level pips
        for(int l=0;l<sk.maxLevel;l++){
            g.setColor(l<lvl?ac:new Color(35,33,60));
            g.fillRect(x+w/2-sk.maxLevel*12+l*26+2,y+86,22,8);
        }
        g.setFont(new Font("Monospaced",Font.PLAIN,9));
        g.setColor(new Color(130,128,160));
        drawCentered(g,"Lv."+lvl+"/"+sk.maxLevel,x+w/2,y+108);
        // Current level desc
        if(lvl>0 && lvl<=sk.levelDesc.length){
            g.setFont(new Font("Monospaced",Font.PLAIN,8));
            g.setColor(new Color(160,158,200));
            drawWrapped(g,sk.levelDesc[lvl-1],x+6,y+120,w-12,10);
        } else if(lvl==0){
            g.setFont(new Font("Monospaced",Font.PLAIN,8));
            g.setColor(new Color(80,78,110));
            drawWrapped(g,"点击分配技能点",x+6,y+120,w-12,10);
        }
        // Counter note
        g.setFont(new Font("Monospaced",Font.PLAIN,7));
        g.setColor(new Color(100,98,130));
        g.drawLine(x+8,y+150,x+w-8,y+150);
        drawWrapped(g,sk.counterNote,x+6,y+157,w-12,9);
        // Add/remove buttons
        if(!maxed&&skillPointsBudget>0){
            boolean bHover=hoverCard==idx*10;
            g.setColor(bHover?ac:new Color(30,28,55));
            g.fillRoundRect(x+w/2-30,y+h-38,60,24,6,6);
            g.setColor(bHover?new Color(8,7,20):ac);
            g.setFont(new Font("Monospaced",Font.BOLD,12));
            drawCentered(g,"+ 升级",x+w/2,y+h-20);
        } else if(maxed){
            g.setColor(new Color(93,202,165));
            g.setFont(new Font("Monospaced",Font.BOLD,11));
            drawCentered(g,"★ 满级",x+w/2,y+h-20);
        }
    }

    private void drawWaiting(Graphics2D g) {
        drawStars(g);
        g.setFont(new Font("Monospaced",Font.BOLD,18));
        g.setColor(new Color(245,196,117));
        drawCentered(g,"等待对方完成技能配置...",W/2,H/2-20);
        int sp=(int)(System.currentTimeMillis()/250%4);
        String[] sps={"|","/","-","\\"};
        g.setColor(new Color(93,202,165));
        g.setFont(new Font("Monospaced",Font.BOLD,22));
        drawCentered(g,sps[sp],W/2,H/2+20);
    }

    private void drawGame(Graphics2D g) {
        if(gameState==null){
            g.setColor(new Color(20,18,40)); g.fillRect(0,0,W,H);
            g.setFont(new Font("Monospaced",Font.BOLD,16));
            g.setColor(new Color(245,196,117));
            drawCentered(g,"等待游戏数据...",W/2,H/2);
            return;
        }
        renderer.render(g,gameState,myId,tick);
        // Skill panel below game area
        GameState.PlayerState myP=(myId==1)?gameState.p1:gameState.p2;
        renderer.drawSkillPanel(g,myP,true,0,H+2);
        // Status message
        if(msgTimer>0&&!statusMsg.isEmpty()){
            g.setFont(new Font("Monospaced",Font.BOLD,13));
            g.setColor(new Color(245,196,117,Math.min(255,msgTimer*3)));
            drawCentered(g,statusMsg,W/2,H-14);
        }
    }

    private void drawGameOver(Graphics2D g) {
        g.setColor(new Color(0,0,0,170)); g.fillRect(0,0,W,H);
        int winnerId=gameState!=null?gameState.winnerId:0;
        boolean iWon=(winnerId==myId);
        g.setFont(new Font("Monospaced",Font.BOLD,36));
        g.setColor(iWon?new Color(245,196,117):new Color(226,75,74));
        drawCentered(g,iWon?"胜利！":"失败...",W/2,H/2-50);
        if(gameState!=null){
            GameState.PlayerState me=(myId==1)?gameState.p1:gameState.p2;
            GameState.PlayerState opp=(myId==1)?gameState.p2:gameState.p1;
            g.setFont(new Font("Monospaced",Font.PLAIN,13));
            g.setColor(new Color(180,178,210));
            drawCentered(g,"击杀: "+me.kills,W/2,H/2);
        }
        boolean hover=hoverCard==97;
        g.setColor(hover?new Color(245,196,117):new Color(55,45,15));
        g.fillRoundRect(W/2-75,H/2+30,150,36,8,8);
        g.setColor(hover?new Color(8,7,20):new Color(245,196,117));
        g.setFont(new Font("Monospaced",Font.BOLD,14));
        drawCentered(g,"再来一局",W/2,H/2+54);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────
    private void drawCentered(Graphics2D g,String s,int cx,int y){
        FontMetrics fm=g.getFontMetrics();
        g.drawString(s,cx-fm.stringWidth(s)/2,y);
    }

    private void drawWrapped(Graphics2D g,String text,int x,int y,int maxW,int lineH){
        if(text==null) return;
        String[] words=text.split(" ");
        StringBuilder line=new StringBuilder();
        int cy=y;
        for(String w:words){
            String test=line+(line.length()>0?" ":"")+w;
            if(g.getFontMetrics().stringWidth(test)>maxW&&line.length()>0){
                g.drawString(line.toString(),x,cy); cy+=lineH; line=new StringBuilder(w);
                if(cy>y+lineH*5) return;
            } else line=new StringBuilder(test);
        }
        if(line.length()>0) g.drawString(line.toString(),x,cy);
    }

    private void drawHeroSprite(Graphics2D g,int cx,int cy,int classIndex){
        Color[] bcs={new Color(24,95,165),new Color(83,74,183),new Color(153,60,29)};
        Color[] hcs={new Color(55,138,221),new Color(127,119,221),new Color(226,75,74)};
        int bounce=(int)(Math.sin(System.currentTimeMillis()*0.006)*3);
        cy+=bounce;
        g.setColor(new Color(0,0,0,50)); g.fillOval(cx-12,cy+19,24,8);
        g.setColor(bcs[classIndex].darker()); g.fillRect(cx-7,cy+7,6,10); g.fillRect(cx+2,cy+7,6,10);
        g.setColor(bcs[classIndex]); g.fillRect(cx-8,cy-4,16,12);
        g.setColor(hcs[classIndex]); g.fillOval(cx-9,cy-18,18,18);
        g.setColor(Color.WHITE); g.fillRect(cx-6,cy-15,4,5); g.fillRect(cx+2,cy-15,4,5);
        g.setColor(new Color(10,10,30)); g.fillRect(cx-5,cy-14,2,3); g.fillRect(cx+3,cy-14,2,3);
        switch(classIndex){
            case 0:g.setColor(new Color(100,100,120));g.fillRect(cx+8,cy-1,18,4);break;
            case 1:g.setColor(new Color(175,153,236));g.fillRect(cx-5,cy+6,3,14);
                   g.setColor(new Color(245,196,117));g.fillOval(cx-7,cy+18,8,8);break;
            case 2:g.setColor(new Color(180,178,169));g.fillRect(cx+8,cy-18,4,24);
                   g.setColor(new Color(220,200,100));g.fillRect(cx+5,cy-22,10,7);break;
        }
    }

    private void drawStars(Graphics2D g){
        long t=System.currentTimeMillis();
        java.util.Random r=new java.util.Random(42);
        for(int i=0;i<80;i++){
            int sx=r.nextInt(W),sy=r.nextInt(H);
            int bright=80+r.nextInt(120);
            float twinkle=(float)(0.7+0.3*Math.sin(t*0.002+i));
            g.setColor(new Color(bright,bright,bright+(i%3==0?40:0),(int)(180*twinkle)));
            g.fillRect(sx,sy,r.nextInt(3)==0?2:1,r.nextInt(3)==0?2:1);
        }
    }

    // ─── Key input ─────────────────────────────────────────────────────────────
    public void keyPressed(int code) {
        if(code<keys.length) keys[code]=true;
        if(screen==Screen.GAME||screen==Screen.GAME_OVER) {
            // Skill upgrade during game
            int[] skillKeys={KeyEvent.VK_1,KeyEvent.VK_2,KeyEvent.VK_3,KeyEvent.VK_4};
            for(int i=0;i<4;i++) if(code==skillKeys[i]&&net!=null) net.send(Packet.skillUp(i));
        }
    }
    public void keyReleased(int code){ if(code<keys.length) keys[code]=false; }

    // ─── Mouse input ───────────────────────────────────────────────────────────
    @Override public void mouseMoved(MouseEvent e){ updateMouse(e); }
    @Override public void mouseDragged(MouseEvent e){ updateMouse(e); }

    private void updateMouse(MouseEvent e){
        double sx=(double)W/getWidth(), sy=(double)(H+150)/getHeight();
        mouseX=(float)(e.getX()*sx); mouseY=(float)(e.getY()*sy);
        // Hover detection
        hoverCard=-1; hoverSkill=-1;
        switch(screen){
            case LOBBY: {
                int cardW=180,cardH=260,gap=28,totalW=3*cardW+2*gap,startX=W/2-totalW/2,cardY=80;
                for(int i=0;i<3;i++){int cx=startX+i*(cardW+gap);if(inRect(e,cx,cardY,cardW,cardH))hoverCard=i;}
                if(myClass>=0&&!myReady&&inRect(e,W/2-70,375,140,36)) hoverCard=99;
            } break;
            case SKILL_PICK: {
                SkillDef[] skills=myClass>=0?SkillDef.getSkills(myClass):null;
                if(skills==null) break;
                int cardW=170,cardH=270,gap=14,totalW=4*cardW+3*gap,sx2=W/2-totalW/2;
                for(int i=0;i<4;i++){
                    int cx=sx2+i*(cardW+gap);
                    if(inRect(e,cx,84,cardW,cardH)){hoverSkill=i;}
                    if(inRect(e,cx+cardW/2-30,84+cardH-38,60,24)) hoverCard=i*10;
                }
                if(inRect(e,W/2-80,H-60,160,38)) hoverCard=98;
            } break;
            case GAME_OVER: if(inRect(e,W/2-75,H/2+30,150,36)) hoverCard=97; break;
        }
    }

    private boolean inRect(MouseEvent e,int x,int y,int w,int h){
        double sx=(double)W/getWidth(),sy=(double)(H+150)/getHeight();
        float mx=(float)(e.getX()*sx),my=(float)(e.getY()*sy);
        return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
    }

    @Override
    public void mousePressed(MouseEvent e){
        mouseDown=(e.getButton()==MouseEvent.BUTTON1);
        handleClick(e);
    }
    @Override public void mouseReleased(MouseEvent e){ if(e.getButton()==MouseEvent.BUTTON1) mouseDown=false; }
    @Override public void mouseClicked(MouseEvent e){}
    @Override public void mouseEntered(MouseEvent e){}
    @Override public void mouseExited(MouseEvent e){}

    private void handleClick(MouseEvent e){
        if(e.getButton()!=MouseEvent.BUTTON1) return;
        switch(screen){
            case LOBBY: {
                int cardW=180,cardH=260,gap=28,totalW=3*cardW+2*gap,startX=W/2-totalW/2,cardY=80;
                for(int i=0;i<3;i++){
                    int cx=startX+i*(cardW+gap);
                    if(inRect(e,cx,cardY,cardW,cardH)){myClass=i;net.send(Packet.classPick(i));return;}
                }
                if(myClass>=0&&!myReady&&inRect(e,W/2-70,375,140,36)){
                    screen=Screen.SKILL_PICK;
                }
            } break;
            case SKILL_PICK: {
                if(myClass<0) break;
                SkillDef[] skills=SkillDef.getSkills(myClass);
                int cardW=170,cardH=270,gap=14,totalW=4*cardW+3*gap,sx=W/2-totalW/2;
                for(int i=0;i<4;i++){
                    int cx=sx+i*(cardW+gap);
                    if(inRect(e,cx+cardW/2-30,84+cardH-38,60,24)){
                        if(skillPointsBudget>0&&mySkillLevels[i]<skills[i].maxLevel){
                            mySkillLevels[i]++; skillPointsBudget--;
                            net.send(Packet.skillUp(i));
                        }
                        return;
                    }
                }
                if(inRect(e,W/2-80,H-60,160,38)){
                    myReady=true; net.send(Packet.ready());
                    screen=Screen.WAITING;
                }
            } break;
            case GAME_OVER: {
                if(inRect(e,W/2-75,H/2+30,150,36)){
                    net.send(Packet.rematch());
                    showMsg("已发送再来一局请求...");
                }
            } break;
        }
    }
}
