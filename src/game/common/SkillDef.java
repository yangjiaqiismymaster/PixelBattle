package game.common;

import java.io.Serializable;

/**
 * Skill tree definition for all three classes.
 *
 * Counter design:
 *   Gunner  : High burst + piercing — countered by Warrior shield + close-gap
 *   Mage    : AoE + status effects (freeze/burn) — countered by Gunner mobility + fast kill
 *   Warrior : Tanky melee + reflect — countered by Mage DoT + kiting
 */
public class SkillDef implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String[] levelDesc;   // description per level (max 3)
    public int maxLevel;
    public int mpCost;
    public int cooldownBase;     // ticks at level 0
    public int type;             // 0=attack 1=defense 2=mobility 3=special
    public String icon;          // text emoji for UI
    public String counterNote;   // what this counters / is countered by

    public SkillDef(String name, int maxLevel, int mpCost, int cooldown,
                    int type, String icon, String counterNote, String... descs) {
        this.name = name; this.maxLevel = maxLevel; this.mpCost = mpCost;
        this.cooldownBase = cooldown; this.type = type; this.icon = icon;
        this.counterNote = counterNote; this.levelDesc = descs;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  GUNNER  skills: index 0-3
    // ──────────────────────────────────────────────────────────────────────────
    public static final SkillDef[] GUNNER_SKILLS = {
        new SkillDef("三连散射", 3, 10, 8, 0, "🔫",
            "输出稳定 | 被魔法师冰冻克制 | 克制近战接近",
            "3颗子弹扇形射出，伤害×1.0", "子弹+1颗，扩散角-10°，伤害×1.2", "再+1颗，穿透敌人，伤害×1.5"),
        new SkillDef("弹幕风暴", 3, 30, 180, 3, "💥",
            "高爆发 | 被护盾反弹克制 | 克制低机动目标",
            "向四周发射12颗子弹", "数量18颗+追踪效果", "数量24颗+穿透+燃烧DoT"),
        new SkillDef("闪避滚动", 3, 15, 90, 2, "⚡",
            "高机动 | 无明显克制 | 帮助规避魔法AoE",
            "向移动方向翻滚，0.5s无敌", "距离+50%，冷却-20%", "可连续翻滚2次，速度+20%"),
        new SkillDef("钢筋防弹衣", 3, 0, 0, 1, "🛡",
            "被动防御 | 被燃烧DoT绕过 | 对抗魔法高爆发",
            "被动: 受到伤害-10%", "减伤-20%，格挡魔法弹15%", "减伤-30%，有几率完全免疫魔法弹")
    };

    // ──────────────────────────────────────────────────────────────────────────
    //  MAGE  skills: index 0-3
    // ──────────────────────────────────────────────────────────────────────────
    public static final SkillDef[] MAGE_SKILLS = {
        new SkillDef("冰霜射线", 3, 12, 10, 0, "❄",
            "减速+控制 | 被高机动翻滚绕过 | 克制近战贴脸",
            "发射冰霜弹，命中减速3s", "寒冷冻结1.5s（无法移动）", "冰霜范围扩大+冻结2.5s+碎冰飞溅"),
        new SkillDef("火焰爆破", 3, 25, 150, 3, "🔥",
            "持续伤害 | 被远程频繁打断施法 | 克制高防近战",
            "爆炸范围80px，燃烧DoT 3s", "范围120px，燃烧5s，点燃地面", "范围160px，燃烧8s，爆炸触发连锁"),
        new SkillDef("奥术护盾", 3, 20, 200, 1, "🔮",
            "反弹防御 | 持续时间短可规避 | 克制枪手弹幕",
            "持续2s护盾，吸收30%伤害", "吸收60%，反弹20%伤害给攻击者", "吸收80%，反弹50%+护盾爆炸"),
        new SkillDef("冰封领域", 3, 40, 300, 3, "🌀",
            "大范围控制 | 范围外不受影响 | 克制群体近战",
            "以自身为中心冰封半径100px", "半径150px，冰封2s", "半径200px，冰封3s+冰刺伤害")
    };

    // ──────────────────────────────────────────────────────────────────────────
    //  WARRIOR  skills: index 0-3
    // ──────────────────────────────────────────────────────────────────────────
    public static final SkillDef[] WARRIOR_SKILLS = {
        new SkillDef("旋风斩", 3, 15, 20, 0, "⚔",
            "近战霸体 | 被冰冻减速克制 | 克制枪手近距离",
            "周围80px斩击，伤害×1.0", "范围120px，击退效果", "范围150px，霸体+击倒0.5s"),
        new SkillDef("冲锋突刺", 3, 20, 120, 2, "🏃",
            "快速接近 | 被冰冻打断 | 克制魔法师远程风筝",
            "向前冲锋200px，途中造成伤害", "距离280px+穿透障碍物", "距离350px+穿透+冲锋后旋风斩"),
        new SkillDef("钢铁意志", 3, 0, 0, 1, "💪",
            "被动韧性 | 被燃烧持续消耗 | 对抗爆发输出",
            "被动: 最大HP+15%，格挡近战10%", "HP+25%，格挡近战20%，受DoT-30%", "HP+40%，格挡近战30%，受DoT-60%，死亡后短暂无敌"),
        new SkillDef("反击护盾", 3, 25, 180, 1, "🔰",
            "主动反弹 | 反弹窗口窄可绕过 | 完克枪手弹幕",
            "0.8s护盾窗口，反弹所有子弹", "1.2s窗口，反弹+伤害×1.5", "1.5s窗口，反弹+主动反击波")
    };

    public static SkillDef[] getSkills(int classId) {
        switch (classId) {
            case 0: return GUNNER_SKILLS;
            case 1: return MAGE_SKILLS;
            case 2: return WARRIOR_SKILLS;
            default: return GUNNER_SKILLS;
        }
    }
}
