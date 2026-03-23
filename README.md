# 像素对战 — 双人联机版

## 项目结构
```
PixelBattle/
├── src/
│   └── game/
│       ├── common/         ← 共用代码（常量、数据包、游戏状态、技能定义）
│       │   ├── Constants.java
│       │   ├── GameState.java
│       │   ├── Packet.java
│       │   └── SkillDef.java
│       ├── server/         ← 服务器（权威游戏逻辑）
│       │   ├── GameServer.java
│       │   ├── ClientHandler.java
│       │   └── GameEngine.java
│       └── client/         ← 客户端（界面、渲染、输入）
│           ├── GameClient.java
│           ├── ClientWindow.java
│           ├── GamePanel.java
│           ├── NetworkManager.java
│           └── Renderer.java
├── run.bat     ← Windows 编译运行脚本
├── run.sh      ← Mac/Linux 编译运行脚本
└── README.md
```

---

## 在 IDEA 中运行

### 第一步：导入项目
1. 打开 IntelliJ IDEA
2. `File → New → Project from Existing Sources`
3. 选择 `PixelBattle` 文件夹
4. 选择 **"Create project from existing sources"** → 一路 Next → Finish
5. 右键 `src` 文件夹 → `Mark Directory as → Sources Root`

### 第二步：启动服务器
1. 找到 `src/game/server/GameServer.java`
2. 右键 → `Run 'GameServer.main()'`
3. 控制台出现 `等待玩家连接...` 即为成功

### 第三步：启动两个客户端（同一台电脑测试）
1. 找到 `src/game/client/GameClient.java`
2. 右键 → `Run 'GameClient.main()'`   → 第一个客户端
3. IDEA 右上角 → 点击运行配置下拉框 → `Edit Configurations`
4. 复制 GameClient 配置，Program Arguments 填 `localhost`
5. 启动第二个配置 → 第二个客户端

### 局域网联机（两台电脑）
- 一台电脑运行服务器：`GameServer.main()`
- 查看服务器电脑的局域网IP（`ipconfig` 或 `ifconfig`），例如 `192.168.1.5`
- 另一台电脑运行客户端，Program Arguments 填 `192.168.1.5`
- 两台电脑都运行客户端即可联机

---

## 游戏操作

| 操作 | 按键 |
|------|------|
| 移动 | WASD 或 方向键 |
| 瞄准 | 鼠标移动 |
| 攻击 | 鼠标左键 |
| 技能1（基础攻击） | J |
| 技能2（大技能） | K |
| 技能3（机动/范围） | L |
| 技能4（防御/特殊） | F |
| 护盾 | Shift |
| 升级技能（游戏中） | 1 / 2 / 3 / 4 |

---

## 三职业反制关系

```
        ┌──────────────────────────────────────┐
        │                                      │
    枪手 ──克制──► 剑士（远程弹幕压制近战）        │
        │                                      │
    魔法师 ──克制──► 枪手（冰冻减速+AoE）         │
        │                                      │
    剑士 ──克制──► 魔法师（高速冲锋+反弹盾）       │
        └──────────────────────────────────────┘
```

### 枪手技能树
| # | 技能 | 类型 | 升级效果 |
|---|------|------|--------|
| J | 三连散射 | 攻击 | Lv2加穿透，Lv3多弹 |
| K | 弹幕风暴 | 特殊 | Lv2追踪，Lv3燃烧DoT |
| L | 闪避滚动 | 机动 | Lv2连滚，Lv3加速 |
| F | 防弹衣 | 防御（被动） | Lv3减伤30%+格挡魔法 |

### 魔法师技能树
| # | 技能 | 类型 | 升级效果 |
|---|------|------|--------|
| J | 冰霜射线 | 攻击 | Lv2冻结，Lv3冰碎溅射 |
| K | 火焰爆破 | 特殊 | Lv2点燃地面，Lv3连锁 |
| L | 奥术护盾 | 防御 | Lv2反弹20%，Lv3反弹50%+爆炸 |
| F | 冰封领域 | 特殊 | Lv2范围150px，Lv3200px |

### 剑士技能树
| # | 技能 | 类型 | 升级效果 |
|---|------|------|--------|
| J | 旋风斩 | 攻击 | Lv2击退，Lv3击倒0.5s |
| K | 冲锋突刺 | 机动 | Lv2穿墙，Lv3接旋风斩 |
| L | 钢铁意志 | 防御（被动） | Lv3 HP+40%，DoT减少60% |
| F | 反击护盾 | 防御 | Lv2反弹伤害×1.5，Lv3反击波 |

---

## 技能点系统
- **配置阶段**：开局分配 6 个技能点，每个技能最多3级
- **游戏中**：每10秒自动获得1技能点，按 1/2/3/4 键升级

---

## 要求
- Java 8+
- 无需任何第三方库
