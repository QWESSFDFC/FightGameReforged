package cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customEntity.players.ActorLiXiaoYan;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.mana.Mana;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.List;

public class UltimateAttack extends Skill {

    public UltimateAttack() {
        super("过载·白炽化", "对3个敌方造成等同于李晓焰 最大生命值 400% 的火属性伤害。\n" +
                "锁定当前生命值：施放终结技时，李晓焰的当前生命值比例将被“铭记”（例如施放时为 20% 生命值）。在接下来的 2 回合 内，她的生命值不会因任何原因（包括自己的技能消耗、敌方攻击、持续伤害）降至该比例以下。\n" +
                "在此期间，每受到一次攻击，获得 1 层【燃点】。", 4, 0, 0, 3);
        this.setCoolDown(4);
        this.setConsumedMana(new Mana(300, ElementSort.FIRE));
        this.getTags().put(TagType.ATTACK, new Tag(5));
    }


    @Override
    public Skill copy() {
        return new UltimateAttack();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        // 加算与减算必须用「同一时刻」的燃点判断（这里用 wasHigh 快照），
        // 否则会出现「加过却按未加成扣除」或「没加也扣」的配平错误。
        boolean wasHigh = false;
        if (user instanceof ActorLiXiaoYan li) {
            li.setMemorizedRate((double) user.getHp() / user.getHpMax());
            if (li.getIgnition() >= ActorLiXiaoYan.HIGH_IGNITION) {
                setExtraDamage((long) (getExtraDamage() + user.getHpMax() * ActorLiXiaoYan.HIGH_IGNITION_BONUS_RATE));
                wasHigh = true;
            }
        }
        for (LivingThing livingThing : enemies) {
            user.makeDamage(livingThing, this);
        }

        applyMemorizedHp(user);
        System.out.println("生命值锁定生效中");

        if (user instanceof ActorLiXiaoYan li) {
            // 攒到上限为止（见 CommonAttack 的注释）：封顶交给 setIgnition 的夹取
            li.setIgnition(li.getIgnition() + 1);
        }
        // 只扣回「确实加过」的那一次。
        // 旧代码用一个无条件置真的 enhanced 标记来判"该不该扣"，燃点恰好 7 层时
        // 会出现「没加也扣」——这一击净减 0.5 × 生命上限 的额外伤害，而且会一直留在技能实例上。
        if (wasHigh) {
            setExtraDamage((long) (getExtraDamage() - user.getHpMax() * ActorLiXiaoYan.HIGH_IGNITION_BONUS_RATE));
        }
    }

    /**
     * 给施放者挂上【生命值锁定】（{@link MemorizedHp}），并保证<b>只注册一个</b>受击监听器。
     * <p>
     * <b>为什么不能每次都注册</b>：重复开大时 {@code LivingThing#addEffect} 会把
     * 同 {@code id + origin} 的旧效果"合并刷新"并<b>丢弃新实例</b>，而旧代码每次都
     * {@code new DamageEventListener()} 注册一遍 —— 被丢掉的那个监听器再也没有人注销
     * （它绑定的效果已经不在身上了），于是"每受到一次攻击 +1 层【燃点】"变成 +N 层
     * （2026-09 复核发现，N = 重复开大的次数）。
     * <p>
     * 现在：身上已有锁定效果时只刷新时长，不再注册新的监听器；
     * 监听器自身还会在 {@code DamageEventListener} 里核对"我的效果还在不在"，双重保险。
     *
     * @param user 施放者
     */
    private void applyMemorizedHp(LivingThing user) {
        boolean alreadyLocked = false;
        for (Effect effect : user.getEntityEffectList()) {
            if (effect instanceof MemorizedHp) {
                alreadyLocked = true;
                break;
            }
        }
        MemorizedHp memorizedHp = new MemorizedHp();
        if (!alreadyLocked) {
            // 监听器由 MemorizedHp 自己创建、并绑定到它自己那个实例（见 MemorizedHp / DamageEventListener）
            EventBus.register(memorizedHp.getLiXiaoYanEventListener());
        }
        // origin 记施加者实体的 UUID（而不是字面量 "self"）：同一个人重复放大招时，
        // Effect#equals（id + origin）会认出"还是同一条"，合并刷新而不是叠第二条
        user.addEffect(memorizedHp.setOrigin(user.getUUID()));
    }
}
