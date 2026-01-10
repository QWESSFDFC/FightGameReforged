package cn.gfhnv.game.entity;
import cn.gfhnv.game.system.ElementSort;
public class Player extends LivingThing{
    public Player(String name,  String id, double fireResistance, double waterResistance, double metalResistance, double woodResistance, double dirtResistance, long speed, LivingThing fightEntity, long l, String type, double hp, double atk, double dfk, ElementSort yu, double hpMagnification, double atkMagnification, double dfkMagnification) {
        super(name,id, fireResistance, waterResistance, metalResistance, woodResistance, dirtResistance, speed, fightEntity, l, type, hp, atk, dfk, yu, hpMagnification, atkMagnification, dfkMagnification);
    }
    public Player(String name,String id, long l,ElementSort u) {
        super(name, id, l, u);
    }
}
