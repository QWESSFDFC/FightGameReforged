package cn.gfhnv.game.officialStuff.customEntity.players;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entityController.PlayerController;
import cn.gfhnv.game.event.Event;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.officialStuff.customEvent.phainonEvents.SelectEventListener;
import cn.gfhnv.game.officialStuff.customSkill.actorLiXiaoYanSkills.UltimateAttack;
import cn.gfhnv.game.officialStuff.customSkill.universalSkill.CommonAttack;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Phainon extends Player {
   private boolean isListenerRegister=false;
   private int spark=0;
   private int spark_max=15;
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

    public int getSpark() {
        return spark;
    }

    public void setSpark(int spark) {
        this.spark = spark;
    }

    public int getSpark_max() {
        return spark_max;
    }

    public void setSpark_max(int spark_max) {
        this.spark_max = spark_max;
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
        this.isListenerRegister=false;
        this.isAwaken=false;
        EventBus.unregister(this.selectEventListener);
        this.setShowSpecialMes(null);
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
        if (!isListenerRegister) EventBus.register(this.selectEventListener);
    }

    public Phainon(long l) {
        super("白厄", "phainon", 0.7, 0, 0, 0,0, 120, l, "player", 29, 40, 25, ElementSort.FIRE);
        List<Skill> skillList=new ArrayList<>();
        skillList.add(new CommonAttack(0.0,1.0,0.0,1));
        skillList.add(new cn.gfhnv.game.officialStuff.customSkill.phainonSkills.normalSkills.UltimateAttack());
        this.setMass(BigDecimal.valueOf(60));
        this.setDescription("这是白厄.");
        this.getInventory().addSlot(63);
        this.setController(new PlayerController(skillList, this));
        this.skills=skillList;

    }

    @Override
    public LivingThing copy() {
        return new Phainon(this.getLevel());
    }

    public boolean isAwaken() {
        return isAwaken;
    }
}
