package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.officialStuff.customEffect.actorLiXiaoYanEffects.MemorizedHp;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.CommonAttack;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.PyrohemicPumping;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.UltimateAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;

import java.util.ArrayList;
import java.util.List;

public class ActorLiXiaoYan extends LivingThing {
    private int ignition = 3;
    private int ignitionMax = 10;
    private int lastIgnition = 3;
    private double memorizedRate = -1;

    public ActorLiXiaoYan(ActorLiXiaoYan other) {
        super(other);
        this.ignition = other.ignition;
        this.ignitionMax = other.ignitionMax;
        this.lastIgnition = other.lastIgnition;
        this.memorizedRate = other.memorizedRate;
        this.setIndividualMultipleArea(other.getIndividualMultipleArea());
    }

    public ActorLiXiaoYan(long l) {
        super("李晓焰", "actorLiXiaoYan", 0.4, 0.0, 0.0, 0.0, 0.0, 120, l, "player", 58, 22, 3, ElementSort.FIRE);
        this.setMass(60);
        this.setDescription("这是李晓焰.");
        this.getInventory().addSlot(63);
        List<Skill> skills = new ArrayList<>();
        skills.add(new CommonAttack());
        skills.add(new PyrohemicPumping());
        skills.add(new UltimateAttack());
        this.setController(new PlayerController(skills, this));
        this.setIndividualMultipleArea(1.0 + 0.04 * ignition);
        this.lastIgnition = ignition;
        this.setModifyDamage(new IModifyDamage() {
            @Override
            public long damageModify(long newHp, DamageEvent da) {
                long correctedHp = newHp;
                if (da.getAttackedEntity() instanceof ActorLiXiaoYan) {
                    if (((ActorLiXiaoYan) da.getAttackedEntity()).hasMemorizedHpEffect() && ((ActorLiXiaoYan) da.getAttackedEntity()).getMemorizedRate() > 0) {
                        long minHp = (long) (((ActorLiXiaoYan) da.getAttackedEntity()).getHpMax() * ((ActorLiXiaoYan) da.getAttackedEntity()).getMemorizedRate());
                        if (correctedHp < minHp) {
                            correctedHp = minHp;
                        }
                    }
                    if (((ActorLiXiaoYan) da.getAttackedEntity()).getIgnition() >= 10 && correctedHp <= 0) {
                        correctedHp = (long) (getHpMax() * 0.3);
                        ((ActorLiXiaoYan) da.getAttackedEntity()).setIgnition(ignition - 10);
                    }
                }
                return correctedHp;
            }
        });
        this.setShowSpecialMes(user -> {
            if (user instanceof ActorLiXiaoYan) {
                System.out.println("燃点层数:" + getIgnition() + "/上限:" + ignitionMax);
            }
        });
    }

    public int getLastIgnition() {
        return lastIgnition;
    }

    @Override
    public LivingThing copy() {
        return new ActorLiXiaoYan(this);
    }

    @Override
    public void whenFightEnds() {
        super.whenFightEnds();
        this.setIgnition(3);
    }

    public int getIgnition() {
        return ignition;
    }

    public void setIgnition(int ignition) {
        int max = ignitionMax;
        if (getHp() < getHpMax() * 0.5) {
            max = 15;
        }
        this.ignition = Math.min(ignition, max);
    }

    public int getIgnitionMax() {
        return ignitionMax;
    }

    public double getMemorizedRate() {
        return memorizedRate;
    }

    public void setMemorizedRate(double memorizedRate) {
        this.memorizedRate = memorizedRate;
    }

    private boolean hasMemorizedHpEffect() {
        for (Effect e : getEntityEffectList()) {
            if (e instanceof MemorizedHp) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateSelf() {
        if (lastIgnition != ignition) {
            setIndividualMultipleArea(getIndividualMultipleArea() + 0.04 * ignition - 0.04 * lastIgnition);
            lastIgnition = ignition;
        }
    }


}