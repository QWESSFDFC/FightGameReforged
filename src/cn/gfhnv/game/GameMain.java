package cn.gfhnv.game;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.FightStartEvent;
import cn.gfhnv.game.event.GameStartEvent;
import cn.gfhnv.game.event.eventListener.EffectEventListener;
import cn.gfhnv.game.event.eventListener.GameStartEventListener;
import cn.gfhnv.game.event.eventListener.PhysicsEventListener;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.logSystem.LogWriter;
import cn.gfhnv.game.mod.ModLoader;
import cn.gfhnv.game.officialStuff.OfficialGameContent;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.FightStartEventListener;
import cn.gfhnv.game.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 代码开源 MIT　License.---------- @author gfhnv
 */
public class GameMain {
    public static String userName;
    public static Scanner SCANNER = new Scanner(System.in);

    public static void gameInitialize() {
        World.addMod(new OfficialGameContent());
        ModLoader.modLoaderInitialize();
        EventBus.register(new GameStartEventListener());
        EventBus.register(new EffectEventListener());
        EventBus.register(new PhysicsEventListener());
        EventBus.register(new FightStartEventListener());
        EventBus.post(new GameStartEvent());
    }

    public static void main(String[] args) {
        gameInitialize();

        String input;
        System.out.println("欢迎进入游戏!请你输入你的名字");
        userName = SCANNER.nextLine();
        LogWriter.writeLog("UserName:"+userName);
        System.out.println("好的." + userName + ".这是一款文字战斗游戏马上你可以选择你的角色和你的敌人,甚至是你的奖励");
        startAFight();
        do {
            System.out.println("要不要再玩一局?输入no退出游戏.yes继续." + GameMain.userName);
            input = SCANNER.nextLine();
            if (input.equalsIgnoreCase("yes")) {
                GameMain.startAFight();
            }
        } while (!input.equalsIgnoreCase("no"));
    }

    public static void startAFight() {
        List<Item> rewards = new ArrayList<>();
        List<LivingThing> enemies = new ArrayList<>();
        List<LivingThing> fighters = new ArrayList<>();
        String input = "";
        LivingThing selectedLivingThing = null;
        Item selectedItem = null;
        System.out.println("首先让我们从选择你的角色开始.可以选择任意数量的角色.这是角色列表.->输入角色名字前的数字来选择<-.\n输入next下一步,quit退出游戏");
        System.out.println("选择一名角色之后你可以获得其介绍,之后再输入yes来把其加入到队伍中,输入no返回上一步");
        while (true) {
            if (!enemies.isEmpty() && !fighters.isEmpty()) {
                break;
            }

            LivingThing[] livingThings = World.getLivingEntityList().toArray(new LivingThing[0]);
            int i = 0;
            for (LivingThing livingThing : World.getLivingEntityList()) {
                System.out.println(i + livingThing.getName());
                i++;
            }
            while (true) {
                input = SCANNER.nextLine();
                if (input.equalsIgnoreCase("quit")) {
                    System.exit(0);
                }
                if (input.equalsIgnoreCase("next")) {
                    break;
                }
                try {
                    selectedLivingThing=livingThings[Integer.parseInt(input)].copy();;
                    System.out.println(selectedLivingThing.getName());
                    System.out.println(selectedLivingThing.getDescription());
                    while (true) {
                        input = SCANNER.nextLine();
                        if (input.equalsIgnoreCase("yes")) {
                            World.addThing(selectedLivingThing);
                            System.out.println("输入下一个数字或next");
                            fighters.add(selectedLivingThing);
                            break;
                        }
                        if (input.equalsIgnoreCase("no")) {
                            selectedLivingThing = null;
                            System.out.println("输入下一个数字或next");
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("输入错误");
                    selectedLivingThing = null;
                    System.out.println("输入下一个数字或next");
                }
            }

            System.out.println("接下来选择对手.依然是刚才的生物列表");
            i = 0;
            for (LivingThing livingThing : World.getLivingEntityList()) {
                System.out.println(i + livingThing.getName());
                i++;
            }
            while (true) {
                input = SCANNER.nextLine();
                if (input.equalsIgnoreCase("quit")) {
                    System.exit(0);
                }
                if (input.equalsIgnoreCase("next")) {
                    break;
                }
                try {
                    selectedLivingThing =livingThings[Integer.parseInt(input)].copy();
                    System.out.println(selectedLivingThing.getName());
                    System.out.println(selectedLivingThing.getDescription());
                    while (true) {
                        input = SCANNER.nextLine();
                        if (input.equalsIgnoreCase("yes")) {
                            World.addThing(selectedLivingThing);
                            enemies.add(selectedLivingThing);
                            System.out.println("输入下一个数字或next");
                            break;
                        }
                        if (input.equalsIgnoreCase("no")) {
                            selectedLivingThing = null;
                            System.out.println("输入下一个数字或next");
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("输入错误");
                    selectedLivingThing = null;
                    System.out.println("输入下一个数字或next");
                }
            }

            System.out.println("奖励.同理");
            i = 0;
            Item[] items = World.getItemList().toArray(new Item[0]);
            for (Item item : World.getItemList()) {
                System.out.println(i + item.getName());
                i++;
            }
            while (true) {
                input = SCANNER.nextLine();
                if (input.equalsIgnoreCase("quit")) {
                    System.exit(0);
                }
                if (input.equalsIgnoreCase("next")) {
                    break;
                }
                try {
                    selectedItem = new Item(items[Integer.parseInt(input)]);
                    System.out.println(selectedItem.getDescription());
                    while (true) {
                        input = SCANNER.nextLine();
                        if (input.equalsIgnoreCase("yes")) {
                            World.addThing(selectedItem);
                            rewards.add(selectedItem);
                            System.out.println("输入下一个数字或next");
                            break;
                        }
                        if (input.equalsIgnoreCase("no")) {
                            selectedItem = null;
                            System.out.println("输入下一个数字或next");
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("输入错误");
                    selectedItem = null;
                    System.out.println("输入下一个数字或next");
                }
            }
        }
        for (LivingThing fighter : fighters) {
            fighter.setHp((long) fighter.getHpMax());
        }
        for (LivingThing enemy : enemies) {
            enemy.setHp((long) enemy.getHpMax());
        }
        System.out.println("游戏开始");
        EventBus.post(new FightStartEvent(new Fight(enemies, rewards, fighters)));
    }
}
