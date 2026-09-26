package cn.gfhnv.game.officialStuff.customSkill.flameReaverSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.summons.BrokenContainer;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.ArrayList;
import java.util.List;

/**
 * 盗火行者技能族的公共基类。
 * <p>
 * 只做两件事：
 * <ol>
 *     <li>把 <b>法力消耗设为 {@code null}</b> —— 这样 {@code Skill#canUse} 永远通过法力那一关，
 *     技能的"能不能放"完全交给各自的 {@link #canUse} 重写（官方这套招式没有能量成本，
 *     而本项目默认的五行法力在 BOSS 身上没有设计意义）；</li>
 *     <li>提供 {@link #attackAllTargets}，统一走 {@code makeDamage} ——
 *     它会 post {@code DamageEvent} 并记下攻击方，容器的"被谁击杀"就靠这条链。</li>
 * </ol>
 * 官方原文一律写「物理属性伤害」，本项目<b>映射为火属性</b>（没有物理属性）。
 *
 * @author AI（DeepSeek）生成
 */
public abstract class FlameReaverSkill extends Skill {

    /**
     * 构造一个盗火行者技能。
     *
     * @param name             技能名
     * @param description      技能说明
     * @param atkMagnification 攻击力倍率
     * @param aims             目标数：0=自身；-1=全体；正数=指定数量
     */
    protected FlameReaverSkill(String name, String description, double atkMagnification, int aims) {
        super(name, description, 0, atkMagnification, 0, aims);
        this.setCoolDown(0);
        // 不消耗法力：canUse 的判定交给子类
        this.setConsumedMana(null);
        this.getTags().put(TagType.ATTACK, new Tag(3));
    }

    /**
     * 复制构造器。
     *
     * @param other 被复制的技能
     */
    protected FlameReaverSkill(FlameReaverSkill other) {
        super(other);
    }

    /**
     * 取出 {@code user} 应该打的那一方。
     * <p>
     * <b>框架语义（实测确认，别再靠方法名猜）</b>：
     * <pre>
     * fight.getOpponentList(entity) → entity <b>对面</b>的实体列表（BOSS 用 = 玩家队伍）
     * fight.getOwnList(entity)      → entity <b>自己一侧</b>的实体列表（BOSS 用 = 含它自己的召唤物）
     * </pre>
     * 所以：<b>取目标用 {@code getOpponentList}</b>；
     * 遍历"自己召唤出来的容器"用 {@code getOwnList} + 类型过滤
     * （见 {@code FlameReaver#getAliveContainers}）。
     * <p>
     * 实测诊断输出（自测 {@code testFlameReaverFactions} 会打印）：
     * <pre>
     * enemiesList           = [至黑之剑，盗火行者, 残破容器]
     * fighterList           = [测试玩家]
     * getOpponentList(BOSS) = [测试玩家]              ← 对面
     * getOwnList(BOSS)      = [至黑之剑…, 残破容器]    ← 自己一侧
     * </pre>
     *
     * @param fight 当前战斗
     * @param user  技能使用者
     * @return user 的敌对阵营（BOSS 使用时 = 玩家队伍）
     */
    protected static List<LivingThing> fightingSideOf(Fight fight, LivingThing user) {
        if (fight == null || user == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(fight.getOpponentList(user));
    }

    /**
     * 共祭那一轮的<b>共同目标</b>：容器正在"与 BOSS 一同攻击"时，用 BOSS 指定的那批；
     * 其余情况（容器自己回合里正常出手）用控制器给的目标。
     * <p>
     * 为什么要有这条：容器的招式是"用自己的控制器出手"的，目标也就由控制器随机挑 ——
     * 于是同一轮共祭里几只容器各打各的（用户 2026-09 实测指出："一同发起攻击时应该攻击相同目标"）。
     *
     * @param given 控制器给的目标列表
     * @param user  技能使用者
     * @return 实际要打的目标列表
     */
    protected static List<LivingThing> jointTargetsOr(List<LivingThing> given, LivingThing user) {
        if (user instanceof BrokenContainer container) {
            List<LivingThing> joint = container.getJointTargets();
            if (joint != null && !joint.isEmpty()) {
                return joint;
            }
        }
        return given;
    }

    /**
     * 对每个目标各打一次。
     * <p>
     * <b>只在"目标里混进了自己人"时才提示</b>：盗火行者会召唤同阵营的【残破容器】，
     * 一旦取目标的写法出错，容器就会打到 BOSS 身上（实测踩过）。
     * 正常情况不打印，免得刷屏。
     *
     * @param user    技能使用者
     * @param targets 目标列表
     */
    protected void attackAllTargets(LivingThing user, List<LivingThing> targets) {
        if (targets == null) {
            return;
        }
        warnIfHittingOwnSide(user, targets);
        for (LivingThing target : targets) {
            if (target == null || !target.isAlive()) {
                continue;
            }
            user.makeDamage(target, this);
        }
    }

    /**
     * 目标里如果混进了"与使用者同一侧"的单位，打印一行警告（正常战斗不会出现）。
     * <p>
     * 用 {@link LivingThing#getParticipateFight()} 拿到当前战斗，再问
     * {@link Fight#getOwnList(LivingThing)}（"user 自己一侧"）判断 —— 不要拿
     * {@code getType()} 之类的字符串去猜阵营，那只是显示用的分类。
     *
     * @param user    技能使用者
     * @param targets 本次的目标列表
     */
    private void warnIfHittingOwnSide(LivingThing user, List<LivingThing> targets) {
        if (user == null) {
            return;
        }
        Fight fight = user.getParticipateFight();
        if (fight == null) {
            return;
        }
        List<LivingThing> ownSide = fight.getOwnList(user);
        for (LivingThing target : targets) {
            if (target == null || target == user) {
                continue;
            }
            if (ownSide.contains(target)) {
                System.out.println("  [警告] " + user.getName() + " 的目标里混进了同阵营的 "
                        + target.getName() + "（取目标的写法有问题）");
                return;
            }
        }
    }

    /**
     * 把一组生物的名字拼成一行（诊断用）。
     * <p>
     * 参数写成 {@code List<? extends LivingThing>}：既能接 {@code List<BrokenContainer>}
     * （泛型不协变，写死 {@code List<LivingThing>} 会编译不过），又能调用 {@link LivingThing#getName()}。
     *
     * @param things 生物列表
     * @return 形如 {@code [甲, 乙]} 的字符串
     */
    protected static String describeNames(List<? extends LivingThing> things) {
        if (things == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < things.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            LivingThing each = things.get(i);
            builder.append(each == null ? "null" : each.getName());
        }
        return builder.append(']').toString();
    }
}
