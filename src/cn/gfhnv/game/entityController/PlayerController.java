package cn.gfhnv.game.entityController;

import cn.gfhnv.game.GameMain;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.entity.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerController extends UniversalController {
    public PlayerController(List<Skill> skills, LivingThing owner) {
        super(skills, owner);
    }
    public PlayerController(PlayerController playerController,LivingThing owner) {super(playerController,owner);}
    @Override
    public void act(Fight fight) {
        String input;
        if (fight.getEnemiesList().contains(getOwner())) {
            super.act(fight);
            return;
        }
        if (!fight.getFighterList().contains(getOwner())) {
            return;
        }
        System.out.println(getOwner().getName() + "有以下技能，输入前方数字使用：");
        Skill[] skills = getSkills().toArray(new Skill[0]);
        for (int i = 0; i < skills.length; i++) {
            System.out.println(i + " " + skills[i].getName() + " 剩余冷却：" + skills[i].getNowCoolDown());
        }
        Skill selectedSkill;
        while (true) {
            input = GameMain.SCANNER.nextLine();
            try {
                int idx = Integer.parseInt(input);
                selectedSkill = skills[idx];
                if (selectedSkill.canUse(fight, getOwner()) && selectedSkill.canUse(fight, getOwner(), null)) {
                    break;
                } else {
                    System.out.println("技能释放条件不满足，请重新选择");
                }
            } catch (Exception e) {
                System.out.println("输入错误，请输入技能编号");
            }
        }
        if (selectedSkill.getAims() == 0) {
            selectedSkill.use(fight, getOwner());
            return;
        }
        Set<LivingThing> attacking = new HashSet<>();
        boolean targetIsEnemy = selectedSkill.isForEnemies();
        List<LivingThing> availableList = targetIsEnemy
                ? new ArrayList<>(fight.getEnemiesList())
                : new ArrayList<>(fight.getFighterList());

        System.out.println("需要选择 " + selectedSkill.getAims() + " 个不同的目标。输入数字选择，输入 'next' 结束（至少选1个）");

        while (attacking.size() < selectedSkill.getAims()) {
            System.out.println("当前可选目标：");
            for (int i = 0; i < availableList.size(); i++) {
                LivingThing t = availableList.get(i);
                if (!attacking.contains(t)) {
                    System.out.println(i + " " + t.getName());
                }
            }
            System.out.println("已选目标：" + attacking.stream().map(LivingThing::getName).collect(Collectors.joining(", ")));
            System.out.print("输入索引或 next: ");

            input = GameMain.SCANNER.nextLine();
            if (input.equalsIgnoreCase("next")) {
                if (attacking.isEmpty()) {
                    System.out.println("至少选择一个目标才能结束");
                    continue;
                }
                break;
            }

            try {
                int idx = Integer.parseInt(input);
                if (idx < 0 || idx >= availableList.size()) {
                    System.out.println("索引超出范围");
                    continue;
                }
                LivingThing candidate = availableList.get(idx);
                if (attacking.contains(candidate)) {
                    System.out.println("该目标已被选择，不能重复");
                    continue;
                }
                attacking.add(candidate);
            } catch (NumberFormatException e) {
                System.out.println("请输入数字或 'next'");
            }
        }
        selectedSkill.use(fight, getOwner(), new ArrayList<>(attacking));
    }
}
