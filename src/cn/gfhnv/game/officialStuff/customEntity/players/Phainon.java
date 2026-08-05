package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.AwakenEndEvent;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.SelectEventListener;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.NormalSkill;
import cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Phainon extends Player {
   private boolean isListenerRegister=false;
   private int pyroheart =0;
   private int pyroheart_max =15;
   private int ruin=0;

    public int getRuin_max() {
        return ruin_max;
    }

    public void setRuin_max(int ruin_max) {
        this.ruin_max = ruin_max;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills;
    }

    public int getRuin() {
        return ruin;
    }

    public void setRuin(int ruin) {
        this.ruin = ruin;
    }

    private int ruin_max=7;
  private boolean isAwaken=false;
  private List<Skill> skills;

    public List<Skill> getSkills() {
        return skills;
    }

    public void setAwaken(boolean awaken) {
        isAwaken = awaken;
    }

    public SelectEventListener getSelectEventListener() {
        return selectEventListener;
    }

    public int getPyroheart() {
        return pyroheart;
    }

    public void setPyroheart(int pyroheart) {
        this.pyroheart = pyroheart;
    }

    public int getPyroheart_max() {
        return pyroheart_max;
    }

    public void setPyroheart_max(int pyroheart_max) {
        this.pyroheart_max = pyroheart_max;
    }

    private final SelectEventListener selectEventListener=new SelectEventListener();
    public boolean isListenerRegister() {
        return isListenerRegister;
    }

    public void setListenerRegister(boolean listenerRegister) {
        isListenerRegister = listenerRegister;
    }

    @Override
    public void whenFightEnds() {
        super.whenFightEnds();
        if (this.isAwaken){
            EventBus.post(new AwakenEndEvent(this));
        }
        this.isListenerRegister=false;
        this.isAwaken=false;
        EventBus.unregister(this.selectEventListener);
        this.setShowSpecialMes(null);
        this.getController().setActionSignal(ActionSignal.NORMAL);
        for (Skill skill:getController().getSkills()){
            if (skill instanceof cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack){
                EventBus.unregister(((UltimateAttack) skill).getAwakeEndListener());
                break;
            }
        }
    }

    @Override
    public void whenFightStart(Fight fight) {
        super.whenFightStart(fight);
        EventBus.register(this.selectEventListener);
        this.isListenerRegister=true;
    }

    @Override
    public void updateSelf() {
        super.updateSelf();

    }

    public Phainon(long l) {
        super("白厄", "phainon", 0.7, 0, 0, 0,0, 120, l, "player", 29, 40, 25, ElementSort.FIRE);
        List<Skill> skillList=new ArrayList<>();
        skillList.add(new CommonAttack(0.0,1.0,0.0,1));
        skillList.getFirst().setName("普通攻击:逐火救世,行则将至");
        skillList.add(new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack());
        this.setMass(BigDecimal.valueOf(60));
        this.setDescription("这是白厄.");
        skillList.add(new NormalSkill());
        this.getInventory().addSlot(63);
        this.setController(new PlayerController(skillList, this));
        this.skills=skillList;
        this.setShowSpecialMes(user -> {
            if (user instanceof Phainon){
                System.out.println("当前火种数量:"+((Phainon) user).getPyroheart());
            }
        });

    }

    @Override
    public LivingThing copy() {
        return new Phainon(this.getLevel());
    }

    public boolean isAwaken() {
        return isAwaken;
    }
}
