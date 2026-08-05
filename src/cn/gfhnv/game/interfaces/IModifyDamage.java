package cn.gfhnv.game.interfaces;

import cn.gfhnv.game.event.DamageEvent;

public interface IModifyDamage {
    IModifyDamage DEFAULT = new IModifyDamage() {

    };

    default long damageModify(long newHp, DamageEvent da) {
        return newHp;
    }
}
