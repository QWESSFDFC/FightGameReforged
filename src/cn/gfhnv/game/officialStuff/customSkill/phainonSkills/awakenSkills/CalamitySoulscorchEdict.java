package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class CalamitySoulscorchEdict extends Skill {
    private List<LivingThing> willAct = new ArrayList<>();

    public CalamitySoulscorchEdict() {
        super("灾厄-弑魂焚诏", "获得等同于敌方全体数量的【毁伤】和1层【弑魂之炽】，随后使敌方全体立即行动。", 0, 0, 0, -1);
        this.setCoolDown(0);
        this.getTags().put(TagType.ATTACK, new Tag(1));
    }

    public CalamitySoulscorchEdict(CalamitySoulscorchEdict attack) {
        super(attack);
    }

    public List<LivingThing> getWillAct() {
        return willAct;
    }

    public void setWillAct(List<LivingThing> willAct) {
        this.willAct = willAct;
    }

    @Override
    public Skill copy() {
        return new CalamitySoulscorchEdict(this);
    }


    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon) {
            ((Phainon) user).setScourge(((Phainon) user).getScourge() + enemies.size());
            ((Phainon) user).setSoulscorch(((Phainon) user).getSoulscorch() + 1);
            if (!((Phainon) user).isAbsorbDamage())
                // 按来源键加 75% 减伤：乘算叠加，且同一来源重复触发不会翻倍
                user.addDamageReduction(Phainon.SOULSCORCH_DAMAGE_REDUCTION, 0.75);
            ((Phainon) user).setAbsorbDamage(true);

        }
        willAct = new ArrayList<>(enemies);
        for (LivingThing e : enemies) {
            // 取「下一次【正常】行动」：getNextTurnOf 会跳过额外回合条目，
            // 所以不会把对方手里的奖励回合（击杀完整容器 / 变身连击那类 needTime=0 的）顶掉。
            TurnEntry nextTurn = TurnManager.getNextTurnOf(e);
            if (nextTurn == null) {
                // 时间轴上没有它的条目（战斗中途新召唤的，或它正在自己的回合里）：
                // 现排一条 needTime=0 的，才真的做得到"立即行动"。
                // 旧写法在这里对可能为 null 的返回值直接解引用 —— NPE 会把整局游戏带崩。
                nextTurn = new TurnEntry(e, BigDecimal.ZERO, TurnManager.getPresentTime());
                TurnManager.getTurns().add(nextTurn);
            } else {
                nextTurn.setStartTime(TurnManager.getPresentTime());
                nextTurn.setNeedTime(BigDecimal.ZERO);
            }
            nextTurn.getLastExecuteList().add((fight1, user1) -> {
                List<LivingThing> opponent = fight1.getOpponentList(user1);

                for (LivingThing livingThing : opponent) {
                    if (livingThing instanceof Phainon phainon) {
                        if (phainon.isAwaken() && phainon.getSoulscorch() > 0) {
                            for (Skill skill : livingThing.getController().getSkills()) {
                                if (skill instanceof CalamitySoulscorchEdict calamitySoulscorchEdict && calamitySoulscorchEdict.willAct.contains(user1)) {
                                    phainon.setSoulscorch(phainon.getSoulscorch() + 1);
                                    calamitySoulscorchEdict.getWillAct().remove(user1);
                                    if (calamitySoulscorchEdict.getWillAct().isEmpty()) {
                                        new Counterattack().comeToEffect(fight1, livingThing, fight1.getOwnList(user1));
                                    }

                                }
                            }
                        }
                    }
                }

            });
        }
        TurnManager.sort();
    }
}
