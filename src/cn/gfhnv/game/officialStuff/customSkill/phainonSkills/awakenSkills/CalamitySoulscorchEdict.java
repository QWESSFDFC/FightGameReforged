package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
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
                ((Phainon) user).setDamageAbsorbedPercent(user.getDamageAbsorbedPercent() + 0.75);
            ((Phainon) user).setAbsorbDamage(true);

        }
        willAct = new ArrayList<>(enemies);
        for (LivingThing e : enemies) {
            TurnManager.getNextTurnOf(e).setStartTime(TurnManager.getPresentTime());
            TurnManager.getNextTurnOf(e).setNeedTime(BigDecimal.ZERO);
            TurnManager.getNextTurnOf(e).getLastExecuteList().add((fight1, user1) -> {
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
