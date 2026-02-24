package cn.gfhnv.game.entity.entityController;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PlayerController extends UniversalController {
    public PlayerController(List<Skill> skills, LivingThing owner) {
        super(skills, owner);
    }

    @Override
    public void act(Fight fight) {
        String input;
        if (fight.getEnemiesList().contains(getOwner())) {
            super.act(fight);
            return;
        }
        if (fight.getFighterList().contains(getOwner())) {
            Skill selectedSkill;
            List<LivingThing> attackTargets=new ArrayList<>();
            System.out.println(getOwner().getName()+"有以下技能.输入技能前方数字使用.后面数字代表可以攻击的目标数");
            Skill[] skills=getSkills().toArray(new Skill[0]);
            int i=0;
            for (Skill skill:skills){
                System.out.println(i+skill.getName());
                i++;
            }
            Scanner sc=new Scanner(System.in);
            while(true){
            input=sc.nextLine();
            try {
                selectedSkill= skills[Integer.parseInt(input)];
                break;
            } catch (Exception e){
                System.out.println("输入错误.重新输入");
            }}
            if (selectedSkill.getAims()==0){selectedSkill.comeToEffect(fight,getOwner());return;}
            System.out.println("选择目标.至少输入一个之后输入next可提前结束选择"+"\n可选目标数量"+selectedSkill.getAims());
            i=0;
            LivingThing[] attackableTargets=fight.getEnemiesList().toArray(new LivingThing[0]);
            for (LivingThing target:attackableTargets){
                System.out.println(i+target.getName());
                i++;
            }
            while(attackTargets.size()<selectedSkill.getAims()&&selectedSkill.getAims()!=0){
                input= sc.nextLine();
                if (input.equals("next")&& !attackTargets.isEmpty()){break;}
                try {
                    attackTargets.add(attackableTargets[Integer.parseInt(input)]);
                } catch (Exception e){
                    System.out.println("输入错误.重新输入");
                }
            }
            selectedSkill.comeToEffect(fight,getOwner(),attackTargets);
        }
    }
}
