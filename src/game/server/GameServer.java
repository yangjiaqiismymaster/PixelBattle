package game.server;

import game.common.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.Arrays;

public class GameServer {

    private ServerSocket serverSocket;
    private ClientHandler[] clients = new ClientHandler[2];
    private volatile int connectedCount = 0;

    // Lobby state
    private int[] chosenClass = {-1, -1};
    private int[][] skillLevels = {new int[4], new int[4]};
    private boolean[] ready = {false, false};
    private boolean[] rematch = {false, false};

    // In-game
    private GameEngine engine;
    private volatile boolean gameRunning = false;
    private ScheduledExecutorService ticker;

    public static void main(String[] args) throws Exception {
        int port = Constants.PORT;
        if(args.length>0) port=Integer.parseInt(args[0]);
        System.out.println("=================================================");
        System.out.println("  像素对战服务器 启动中... 端口: " + port);
        System.out.println("=================================================");
        new GameServer(port).start();
    }

    public GameServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("等待玩家连接...");
    }

    public void start() {
        while (true) {
            try {
                reset();
                acceptPlayers();
                runLobby();
                // 等待游戏结束，不能让主循环立刻 reset
                waitForGameOver();
            } catch (Exception e) {
                System.out.println("会话结束，错误信息: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void waitForGameOver() throws InterruptedException {
        // 等到游戏结束或玩家断开
        while (gameRunning) {
            Thread.sleep(200);
        }
        // 再等2秒让客户端收到游戏结束数据
        Thread.sleep(2000);
        System.out.println("游戏会话结束，准备重置...");
    }

    private void reset() {
        chosenClass = new int[]{-1,-1};
        skillLevels = new int[][]{new int[4],new int[4]};
        ready = new boolean[]{false,false};
        rematch = new boolean[]{false,false};
        gameRunning = false;
        connectedCount = 0;
        engine = null;
        if(ticker!=null&&!ticker.isShutdown()) ticker.shutdownNow();
        // 关闭旧的客户端连接，避免残留
        for(int i=0;i<clients.length;i++){
            if(clients[i]!=null){
                clients[i].close();
                clients[i]=null;
            }
        }
        System.out.println("服务器已重置，等待新玩家连接...");
    }

    private void acceptPlayers() throws Exception {
        for(int i=0;i<2;i++){
            System.out.println("等待玩家 " + (i+1) + " 连接...");
            Socket s = serverSocket.accept();
            System.out.println("玩家 " + (i+1) + " 已连接: " + s.getInetAddress());
            clients[i] = new ClientHandler(s, i+1, this);
            clients[i].start();
            connectedCount++;
            clients[i].send(Packet.hello(i+1));
        }
        System.out.println("两位玩家均已连接，进入大厅...");
        broadcast(new Packet(Constants.PKT_HELLO)); // signal lobby open
    }

    private void runLobby() throws InterruptedException {
        // wait until both pick class and are ready
        while(!(ready[0]&&ready[1])) Thread.sleep(50);
        System.out.println("双方已就绪，开始游戏！");
        startGame();
    }

    synchronized void onClassPick(int playerId, int classId) {
        chosenClass[playerId-1] = classId;
        System.out.println("玩家"+playerId+" 选择职业: "+classId);
        // broadcast to other player
        Packet p = new Packet(Constants.PKT_CLASS_PICK);
        p.intVal = classId; p.x = playerId;
        broadcast(p);
    }

    synchronized void onReady(int playerId) {
        ready[playerId-1] = true;
        System.out.println("玩家"+playerId+" 已就绪");
        Packet p = new Packet(Constants.PKT_READY);
        p.intVal = playerId;
        broadcast(p);
    }

    synchronized void onSkillUp(int playerId, int skillIdx) {
        if(!gameRunning&&engine==null){
            // Pre-game skill selection
            if(skillLevels[playerId-1][skillIdx]<3){
                int total=0;
                for(int l:skillLevels[playerId-1]) total+=l;
                if(total<6) skillLevels[playerId-1][skillIdx]++;
            }
        } else if(engine!=null){
            engine.upgradeSkill(playerId, skillIdx);
        }
    }

    synchronized void onInput(int playerId, Packet input) {
        if(engine!=null) engine.applyInput(playerId, input);
    }

    synchronized void onRematch(int playerId) {
        rematch[playerId-1] = true;
        Packet p = new Packet(Constants.PKT_REMATCH);
        p.intVal = playerId;
        broadcast(p);
        if(rematch[0]&&rematch[1]){
            gameRunning=false;
            if(ticker!=null) ticker.shutdownNow();
            Arrays.fill(chosenClass,-1);
            Arrays.fill(ready,false);
            Arrays.fill(rematch,false);
            skillLevels=new int[][]{new int[4],new int[4]};
            engine=null;
            Packet start=new Packet(Constants.PKT_HELLO);
            broadcast(start);
        }
    }

    private void startGame() {
        engine = new GameEngine(chosenClass[0], chosenClass[1], skillLevels[0], skillLevels[1]);
        gameRunning = true;
        // Award initial skill points for pre-game picks (already applied in engine init)
        Packet startPkt = new Packet(Constants.PKT_GAME_START);
        startPkt.intVal = chosenClass[0];
        startPkt.floatVal = chosenClass[1];
        broadcast(startPkt);

        ticker = Executors.newSingleThreadScheduledExecutor();
        ticker.scheduleAtFixedRate(this::gameTick, 0, 1000/Constants.FPS, TimeUnit.MILLISECONDS);
    }

    private void gameTick() {
        try {
            if(!gameRunning||engine==null) return;
            engine.tick();
            GameState gs = engine.getState();
            // Award skill points every 600 ticks (10s)
            if(gs.tick%600==0&&gs.tick>0){
                engine.awardSkillPoint(1);
                engine.awardSkillPoint(2);
                gs.message="获得技能点！按 1/2/3/4 升级技能";
            }
            Packet pkt = new Packet(Constants.PKT_STATE);
            pkt.state = gs;
            broadcast(pkt);
            if(gs.gameOver){
                gameRunning=false;
                ticker.shutdown();
                System.out.println("游戏结束！胜者: 玩家"+gs.winnerId);
            }
        } catch(Exception e) {
            System.out.println("gameTick 异常: " + e.getMessage());
            e.printStackTrace();
            gameRunning=false;
        }
    }

    void broadcast(Packet p) {
        for(ClientHandler c:clients) if(c!=null) c.send(p);
    }

    void onDisconnect(int playerId) {
        System.out.println("玩家"+playerId+" 断开连接");
        gameRunning=false;
        if(ticker!=null) ticker.shutdownNow();
    }
}