package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.event.DamageEvent;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.eventListener.FightTurnPastListener;
import cn.gfhnv.game.interfaces.IModifyDamage;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.FightStartAndSelectEventListener;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.awakenSkills.LastAttack;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.NormalSkill;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class Phainon extends Player {
    private final FightStartAndSelectEventListener fightStartAndSelectEventListener = new FightStartAndSelectEventListener();
    private boolean isListenerRegister = false;
    private int coreflame = 0;
    private int coreflame_max = 15;
    private int soulscorch;
    private int scourge = 0;
    private int scourge_max = 7;
    private boolean isAwaken = false;
    private int extraAbilityTier = 0;
    private int formerExtraAbilityTier = 0;
    private List<Skill> skills;
    private boolean absorbDamage = false;
    private boolean pendingLastAttack = false;
    private int extraTurns = 0;

    public Phainon(long l) {
        super("白厄", "phainon", 0.7, 0, 0, 0, 0, 120, l, "player", 29, 40, 25, ElementSort.FIRE);
        List<Skill> skillList = new ArrayList<>();
        skillList.add(new CommonAttack(0.0, 1.0, 0.0, 1));
        skillList.getFirst().setName("普通攻击:逐火救世,行则将至");
        this.setMass(60);
        this.setDescription("这是白厄.");
        coreflame = 15;
        skillList.add(new NormalSkill());
        skillList.add(new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack());
        this.getInventory().addSlot(63);
        this.setController(new PlayerController(skillList, this));
        this.skills = skillList;
        this.setModifyDamage(
                new IModifyDamage() {
                    @Override
                    public long damageModify(long newHp, DamageEvent da) {
                        if (da.getAttackedEntity() instanceof Phainon phainon) {
                            if (((Phainon) da.getAttackedEntity()).isAwaken && newHp <= 0) {
                                if (((Phainon) da.getAttackedEntity()).isPendingLastAttack()) {
                                    return 1;
                                }
                                newHp = 1;
                                ((Phainon) da.getAttackedEntity()).setPendingLastAttack(true);
                                FightTurnPastListener.getPresentTurn().getLastExecuteList().add((fight, user) -> {
                                    List<LivingThing> availableTargets;
                                    if (fight.getEnemiesList().contains(phainon))
                                        availableTargets = new ArrayList<>(fight.getFighterList());
                                    else {
                                        availableTargets = new ArrayList<>(fight.getEnemiesList());
                                    }

                                    if (!availableTargets.isEmpty())
                                        new LastAttack().comeToEffect(fight, phainon, availableTargets);
                                });

                            }
                        }
                        return newHp;
                    }
                }

        );
        this.setShowSpecialMes(user -> {
            if (user instanceof Phainon phainon) {
                if (phainon.isAwaken)
                    System.out.println("毁伤数量" + phainon.getScourge() + "|||剩余额外回合数" + phainon.getExtraTurns());
                else System.out.println("当前火种数" + phainon.getCoreflame());
            }
        });

    }

    public int getSoulscorch() {
        return soulscorch;
    }

    public void setSoulscorch(int soulscorch) {
        this.soulscorch = soulscorch;
    }

    public boolean isAbsorbDamage() {
        return absorbDamage;
    }

    public void setAbsorbDamage(boolean absorbDamage) {
        this.absorbDamage = absorbDamage;
    }

    public void addScourge(int i) {
        this.setScourge(Math.min(scourge + i, scourge_max));
    }

    public void removeScourge(int i) {
        this.setScourge(Math.max(0, scourge - i));
    }

    public int getExtraTurns() {
        return extraTurns;
    }

    public void setExtraTurns(int extraTurns) {
        this.extraTurns = extraTurns;
    }

    public boolean isPendingLastAttack() {
        return pendingLastAttack;
    }

    public void setPendingLastAttack(boolean pendingLastAttack) {
        this.pendingLastAttack = pendingLastAttack;
    }

    public int getScourge_max() {
        return scourge_max;
    }

    public void setScourge_max(int scourge_max) {
        this.scourge_max = scourge_max;
    }

    public int getScourge() {
        return scourge;
    }

    public void setScourge(int scourge) {
        this.scourge = Math.min(scourge_max, scourge);
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills;
    }

    public FightStartAndSelectEventListener getSelectEventListener() {
        return fightStartAndSelectEventListener;
    }

    public int getCoreflame() {
        return coreflame;
    }

    public void setCoreflame(int coreflame) {
        this.coreflame = Math.min(coreflame_max, coreflame);
    }

    public int getCoreflame_max() {
        return coreflame_max;
    }

    public void setCoreflame_max(int coreflame_max) {
        this.coreflame_max = coreflame_max;
    }

    public boolean isListenerRegister() {
        return isListenerRegister;
    }

    public void setListenerRegister(boolean listenerRegister) {
        isListenerRegister = listenerRegister;
    }

    @Override
    public void whenFightEnds() {
        super.whenFightEnds();
        this.setAttackEnhancePercent(this.getAttackEnhancePercent() - extraAbilityTier * 0.5 + formerExtraAbilityTier * 0.5);
        extraAbilityTier = 0;
        formerExtraAbilityTier = 0;
        coreflame = 0;
        scourge = 0;
        soulscorch = 0;
        pendingLastAttack = false;
        this.absorbDamage = false;
        if (this.isAwaken) {
            EventBus.post(new AwakenEndEvent(this));
        }
        this.isListenerRegister = false;
        this.isAwaken = false;
        EventBus.unregister(this.fightStartAndSelectEventListener);
        this.setShowSpecialMes(user -> {
            if (user instanceof Phainon phainon) {
                if (phainon.isAwaken())
                    System.out.println("毁伤数量" + phainon.getScourge() + "|||剩余额外回合数" + phainon.getExtraTurns());
                else System.out.println("当前火种数" + phainon.getCoreflame());
            }
        });
        this.getController().setActionSignal(ActionSignal.NORMAL);
        for (Skill skill : getController().getSkills()) {
            if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack) {
                EventBus.unregister(((UltimateAttack) skill).getAwakeEndListener());
                break;
            }
        }
    }

    @Override
    public void whenFightStart(Fight fight) {
        super.whenFightStart(fight);
        EventBus.register(this.fightStartAndSelectEventListener);

        this.isListenerRegister = true;
    }

    @Override
    public void updateSelf() {
        super.updateSelf();
        if (isAwaken) {
            ListIterator<Effect> listedIterator = this.getEntityEffectList().listIterator();
            while (listedIterator.hasNext()) {
                Effect effect = listedIterator.next();
                if (effect.isNegative()) {
                    effect.whenLastTimeEnd(this);
                    listedIterator.remove();
                }
            }

        }
        this.setAttackEnhancePercent(this.getAttackEnhancePercent() + extraAbilityTier * 0.5 - formerExtraAbilityTier * 0.5);
        formerExtraAbilityTier = extraAbilityTier;


    }

    public int getExtraAbilityTier() {
        return extraAbilityTier;
    }

    public void setExtraAbilityTier(int extraAbilityTier) {
        this.extraAbilityTier = extraAbilityTier;
    }


    @Override
    public LivingThing copy() {
        return new Phainon(this.getLevel());
    }

    public boolean isAwaken() {
        return isAwaken;
    }

    public void setAwaken(boolean awaken) {
        isAwaken = awaken;
    }
}
