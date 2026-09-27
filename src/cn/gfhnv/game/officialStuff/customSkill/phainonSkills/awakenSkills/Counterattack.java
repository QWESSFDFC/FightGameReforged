package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Counterattack extends Skill {
    public Counterattack() {
        super("灾厄-弑魂焚诏的反击", "灾厄-弑魂焚诏的反击", 0, 1, 0, -1);
        this.setCoolDown(0);
        this.getTags().put(TagType.ATTACK, new Tag(1));
    }

    @Override
    public Skill copy() {
        return new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.Counterattack();
    }

    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        int soulscorch = 0;
        user.setHp((long) (user.getHp() + user.getHpMax() * 0.2));
        double randomMag = 0.3;
        if (user instanceof Phainon phainon) {
            soulscorch = phainon.getSoulscorch();
            phainon.setSoulscorch(0);
            if (phainon.isAbsorbDamage()) {
                // 移除「灾厄」那一份减伤（按来源键，幂等）
                phainon.removeDamageReduction(Phainon.SOULSCORCH_DAMAGE_REDUCTION);
                phainon.setAbsorbDamage(false);
            }
            for (Skill skill : user.getController().getSkills()) {
                if (skill instanceof CalamitySoulscorchEdict) ((CalamitySoulscorchEdict) skill).getWillAct().clear();

            }
        }

        this.setAtkMagnification(this.getAtkMagnification() * (1 + soulscorch * 0.2));
        randomMag = randomMag * (1 + soulscorch * 0.2);
        // 只打【还活着】的目标。死亡结算要等回合循环走到开头才做（先把尸体移出阵营列表，
        // 再走 whenLeaveFight），所以本回合内 enemies 里可能还躺着 0 血的尸体 ——
        // 不过滤就会刷出一串「攻击了 X  -0  → HP 0/…  【灾厄-弑魂焚诏的反击】」，日志尾巴全是打尸体。
        // 顺带抽自己的副本：旧写法 {@code Collections.shuffle(enemies)} 是就地打乱<b>调用方</b>的列表。
        List<LivingThing> aliveEnemies = aliveOf(enemies);
        for (LivingThing livingThing : aliveEnemies) {
            user.makeDamage(livingThing, this);
        }
        this.setAtkMagnification(randomMag);
        for (int i = 0; i <= 5; i++) {
            // 上一刀可能已经把目标打死了，抽之前重新过一遍
            aliveEnemies.removeIf(target -> !target.isAlive());
            if (aliveEnemies.isEmpty()) {
                // 旧写法无条件 enemies.getFirst()，敌方全倒下（或一开始就是空表）时抛
                // NoSuchElementException，整局游戏跟着崩
                break;
            }
            Collections.shuffle(aliveEnemies);
            user.makeDamage(aliveEnemies.getFirst(), this);
        }

        this.setAtkMagnification(1);

    }

    /**
     * 过滤出还活着的目标。
     *
     * @param things 候选目标（允许为 {@code null}）
     * @return 其中 {@link LivingThing#isAlive()} 为真的那些；<b>新列表</b>，不改动入参
     */
    private static List<LivingThing> aliveOf(List<LivingThing> things) {
        List<LivingThing> alive = new ArrayList<>();
        if (things == null) {
            return alive;
        }
        for (LivingThing thing : things) {
            if (thing != null && thing.isAlive()) {
                alive.add(thing);
            }
        }
        return alive;
    }

}
