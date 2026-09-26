package cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

/**
 * 【灼血泵动】—— 李晓焰的战技：烧自己的血换【燃点】。
 * <p>
 * 机制：获得 2 层【燃点】→ 按当前生命值的 {@value #HP_COST_RATE} 自伤 →
 * 对单体造成 200% 生命上限 的伤害；施放时生命值低于 {@value #LOW_HP_THRESHOLD}
 * 则这一击的额外伤害 ×{@value #LOW_HP_MULTIPLIER}。
 * <p>
 * <b>2026-09 改了两处</b>：
 * <ul>
 *     <li><b>额外伤害改成"本次算出来、伤害算完清零"</b>：旧写法是
 *     {@code setExtraDamage(getExtraDamage() * 1.5)} 直接累乘在技能实例上，而
 *     {@code Skill#extraDamage} 全项目<b>没有任何重置点</b>（{@code Skill.java:46} 的注释
 *     说"伤害计算后重置为零"，但没人实现）—— 基数一旦不为 0 就会 1.5ⁿ 膨胀；
 *     反过来基数为 0 时这个 ×1.5 又完全不起作用；</li>
 *     <li>【燃点】攒到上限为止（不再"涨一层扣一层"卡在 8 层）、自伤 0.2 → {@value #HP_COST_RATE}。</li>
 * </ul>
 *
 * @author AI（DeepSeek）生成
 */
public class PyrohemicPumping extends Skill {

    /**
     * 施放时消耗自身<b>当前</b>生命的比例（不会把生命值降到 1 以下）。
     * <p>
     * 2026-09 加强：{@code 0.2 → 0.15}。
     */
    private static final double HP_COST_RATE = 0.15;

    /**
     * 施放时生命值比例低于这个值，则本次额外伤害 ×{@link #LOW_HP_MULTIPLIER}。
     */
    private static final double LOW_HP_THRESHOLD = 0.5;

    /**
     * 低血时这一击额外伤害的倍率。
     */
    private static final double LOW_HP_MULTIPLIER = 1.5;

    public PyrohemicPumping() {
        super("灼血泵动", "获得 2 层【燃点】。消耗李晓焰 当前生命值 15%（此消耗不会使生命值降至 1 以下）", 2, 0, 0, 1);
        this.setCoolDown(1);
        this.setConsumedMana(new Mana(20, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(3));
    }

    @Override
    public Skill copy() {
        return new PyrohemicPumping();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof ActorLiXiaoYan li) {
            // 攒到上限为止（见 CommonAttack 的注释）：封顶交给 setIgnition 的夹取
            li.setIgnition(li.getIgnition() + 2);
        }

        // 低血判据取"自伤之前"的生命比例（与官方描述一致）
        boolean lowHp = (double) user.getHp() / user.getHpMax() <= LOW_HP_THRESHOLD;
        if (user.getHp() - user.getHp() * HP_COST_RATE > 1 && user.getHp() != 1) {
            user.setHp((long) (user.getHp() - user.getHp() * HP_COST_RATE));
        }

        // 本次施放的额外伤害 = 高燃点加成（≥HIGH_IGNITION）× 低血倍率，用完立刻清零。
        // 注意：清零之后就**不能**再靠"上一回合的残留"来放大低血收益了 ——
        // 所以低血的 ×1.5 是乘在"高燃点加成"上的；没到高燃点线时它是 0 × 1.5 = 0。
        long bonus = 0;
        if (user instanceof ActorLiXiaoYan li && li.getIgnition() >= ActorLiXiaoYan.HIGH_IGNITION) {
            bonus += (long) (user.getHpMax() * ActorLiXiaoYan.HIGH_IGNITION_BONUS_RATE);
        }
        if (lowHp) {
            bonus = (long) (bonus * LOW_HP_MULTIPLIER);
        }
        setExtraDamage(bonus);

        for (LivingThing livingThing : enemies) {
            user.makeDamage(livingThing, this);
        }

        // 额外伤害不允许跨回合残留（原实现漏了这一步，见类注释）
        setExtraDamage(0);
    }
}
