package cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.officialStuff.customEntity.players.Phainon;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
//获得等同于敌方全体数量的【毁伤】和1层【弑魂之炽】，随后使敌方全体立即行动。
//【弑魂之炽】状态下，卡厄斯兰那受到的伤害降低75%，敌方目标攻击或行动后叠加1层【弑魂之炽】。上述敌方目标行动完毕后，立即发动反击，对敌方全体造成等同于卡厄斯兰那【20%/40%】攻击力的物理属性伤害，并额外造成4次伤害，每次对敌方随机单体造成等同于卡厄斯兰那【15%/30%】攻击力的物理属性伤害，随后解除【弑魂之炽】。每层【弑魂之炽】使本次反击的伤害倍率提高原倍率的20%。
//通过此技能造成伤害时，被视为造成了战技伤害。若卡厄斯兰那的额外回合开始时仍持有【弑魂之炽】，立即发动反击。
public class CalamitySoulscorchEdict extends Skill {
    public CalamitySoulscorchEdict() {
        super("灾厄•弑魂焚诏", "获得等同于敌方全体数量的【毁伤】和1层【弑魂之炽】，随后使敌方全体立即行动。",0, 0, 0, -1);
        this.setCoolDown(0);
    }

    public List<LivingThing> getWillAct() {
        return willAct;
    }

    public void setWillAct(List<LivingThing> willAct) {
        this.willAct = willAct;
    }

    private List<LivingThing> willAct=new ArrayList<>();
    public CalamitySoulscorchEdict(CalamitySoulscorchEdict attack) {
        super(attack);
    }

    @Override
    public Skill copy() {
        return new CalamitySoulscorchEdict(this);
    }


    @Override
    public void comeToEffect(Fight fight, LivingThing user, List<LivingThing> enemies) {
        if (user instanceof Phainon){
            ((Phainon) user).setScourge(((Phainon) user).getScourge()+enemies.size());
            ((Phainon) user).setSoulscorch(((Phainon) user).getSoulscorch()+1);
            if (!((Phainon) user).isAbsorbDamage()) ((Phainon) user).setDamageAbsorbedPercent(user.getDamageAbsorbedPercent()+0.75);
            ((Phainon) user).setAbsorbDamage(true);

        }
     willAct=new ArrayList<>(enemies) ;
       for (LivingThing e:enemies){
           TurnManager.getNextTurnOf(e).setStartTime(TurnManager.getPresentTime());
           TurnManager.getNextTurnOf(e).setNeedTime(BigDecimal.ZERO);
           TurnManager.getNextTurnOf(e).getFirstExecuteList().add((fight1, user1) -> {
                if (fight1.getEnemiesList().contains(user1)){
                    for (LivingThing livingThing : fight1.getFighterList()){
                        if (livingThing instanceof Phainon){
                            if (((Phainon) livingThing).isAwaken()){
                                ((Phainon) livingThing).setSoulscorch(((Phainon) livingThing).getSoulscorch()+1);
                                for (Skill skill:livingThing.getController().getSkills()){
                                    if (skill instanceof CalamitySoulscorchEdict){
                                        ((CalamitySoulscorchEdict) skill).getWillAct().remove(user1);
                                        if (((CalamitySoulscorchEdict) skill).getWillAct().isEmpty()){
                                            new Counterattack().comeToEffect(fight1,livingThing,fight1.getEnemiesList());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
               if (fight1.getFighterList().contains(user1)){
                   for (LivingThing livingThing : fight1.getEnemiesList()){
                       if (livingThing instanceof Phainon){
                           if (((Phainon) livingThing).isAwaken()){
                               ((Phainon) livingThing).setSoulscorch(((Phainon) livingThing).getSoulscorch()+1);
                               for (Skill skill:livingThing.getController().getSkills()){
                                   if (skill instanceof CalamitySoulscorchEdict){
                                       ((CalamitySoulscorchEdict) skill).getWillAct().remove(user1);
                                       if (((CalamitySoulscorchEdict) skill).getWillAct().isEmpty()){
                                           new Counterattack().comeToEffect(fight1,livingThing,fight1.getFighterList());
                                       }
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
