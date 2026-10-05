package cn.gfhnv.game.entityController;

import cn.gfhnv.game.GameMain;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.inventory.Slot;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.command.CommandManager;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 玩家控制器：负责在命令行里询问「用不用物品 / 用哪个技能 / 打谁」。
 * <p>
 * <b>由 AI 追加的部分</b>：所有 {@code SCANNER.nextLine()} 都改走 {@link #nextLine()}，
 * 该方法先判断这一行是不是命令（以 {@code /} 或 {@code #} 开头）：是命令就地执行并继续读下一行，
 * 不是命令就原样返回。除此之外本类的输入模式<b>没有任何改动</b>。
 *
 * @author gfhnv（命令接入部分由 AI 完成）
 */
public class PlayerController extends UniversalController {
    public PlayerController(List<Skill> skills, LivingThing owner) {
        super(skills, owner);
    }

    public PlayerController(PlayerController playerController, LivingThing owner) {
        super(playerController, owner);
    }

    /**
     * 读一行输入；命令会被就地执行，然后继续等待真正的输入。
     * <p>
     * 与 {@code GameMain.SCANNER.nextLine()} 的调用方式完全等价，只是多了命令拦截；
     * 把 {@link CommandManager#setEnabled(boolean)} 设为 {@code false} 即可关闭命令拦截。
     *
     * @return 一行非命令输入
     */
    private static String nextLine() {
        while (true) {
            String line = GameMain.SCANNER.nextLine();
            if (CommandManager.isCommand(line)) {
                CommandManager.process(line);
                continue;
            }
            return line;
        }
    }

    /**
     * 判断「这场战斗是否已经结束了」。
     * <p>
     * {@code /endfight} 之类的命令是在玩家回合内执行的，此时 {@link #act(Fight)}
     * 正卡在等输入上，它自己不会知道战斗已经结束；若不检查，玩家会被留在
     * 「已经没有战斗」的技能选择界面里。
     * <p>
     * 每个输入点之后都调一次本方法，一旦战斗结束就立刻 {@code return}，
     * 让回合循环能正常退出、控制权回到主循环。
     *
     * @return 战斗是否已经结束（应当立即退出本次行动）
     */
    private static boolean fightIsOver() {
        return !GameMain.isInFight();
    }

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
        if (fightIsOver()) {
            return;
        }
        boolean hasItem = false;
        for (Slot slot : this.getOwner().getInventory().getSlots()) {
            if (slot.getContainedItem() != null) {
                hasItem = true;
                break;
            }
        }
        if (hasItem) {
            System.out.println("是否使用物品?(yes/no)");
            input = nextLine();
            if (fightIsOver()) {
                return;
            }
            if (input.equals("yes")) {
                this.useItem(fight);
            }
        }

        System.out.println(getOwner().getName() + "有以下技能，输入前方数字使用：");
        Skill[] skills = getSkills().toArray(new Skill[0]);
        for (int i = 0; i < skills.length; i++) {
            System.out.println(i + " " + skills[i].getName() + " 剩余冷却：" + skills[i].getNowCoolDown());
        }
        Skill selectedSkill;
        while (true) {
            input = nextLine();
            // 战斗可能已经被 /endfight 结束（命令是在 nextLine() 里执行的）
            if (fightIsOver()) {
                return;
            }
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
        if (fightIsOver()) {
            return;
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
        if (selectedSkill.getAims() == -1) {
            attacking.addAll(availableList);
        } else {
            System.out.println("需要选择 " + selectedSkill.getAims()
                    + " 个不同的目标。输入数字选择（一行可以写多个，用 / 分隔，例如 0/1/2），输入 'next' 结束（至少选1个）");

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

                input = nextLine();
                if (fightIsOver()) {
                    return;
                }
                if (isNext(input)) {
                    if (attacking.isEmpty()) {
                        System.out.println("至少选择一个目标才能结束");
                        continue;
                    }
                    break;
                }

                int[] indices = parseIndices(input);
                if (indices == null) {
                    System.out.println("请输入数字或 'next'");
                    continue;
                }
                for (int idx : indices) {
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
                    // 够了就停在最后一个被采纳的下标上（同一行后面的数字不再计入）
                    if (attacking.size() >= selectedSkill.getAims()) {
                        break;
                    }
                }
            }
        }
        selectedSkill.use(fight, getOwner(), new ArrayList<>(attacking));
        EventBus.post(new SelectTargetEvent(getOwner(), new ArrayList<>(attacking), fight));
    }

    /**
     * 输入是不是 {@code next}（含缩写 {@code n}/{@code ne}/{@code nex}，忽略大小写）。
     * <p>
     * 这里的 {@code n} 是 {@code next}（现有约定）；目标选择这一步没有 {@code no}，
     * 所以不存在"一个字母两个关键字"的歧义 —— 那边「是否使用物品?(yes/no)」只认 {@code yes}，
     * 与这里互不影响。
     *
     * @param input 一行非命令输入
     * @return 是否表示"结束选择"
     */
    private static boolean isNext(String input) {
        if (input == null) {
            return false;
        }
        String value = input.trim();
        if ("n".equalsIgnoreCase(value)) {
            return true;
        }
        return value.length() >= 2 && value.length() < "next".length()
                && "next".regionMatches(true, 0, value, 0, value.length());
    }

    /**
     * 把一行输入解析成下标列表，允许 {@code /} 或空白作分隔，例如 {@code 0/1/2}、{@code 0 1 2}。
     * <p>
     * 整行以 {@code /} 开头的是命令（由 {@link #nextLine()} 提前执行掉），不会走到这里。
     *
     * @param input 一行非命令输入
     * @return 解析出的下标；含非数字时返回 {@code null}（调用方按"请输入数字"处理）
     */
    private static int[] parseIndices(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String[] parts = input.trim().split("[\\s/]+");
        int[] indices = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                indices[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return indices;
    }

    @Override
    public void useItem(Fight fight) {
        if (this.getOwner() == null) {
            return;
        }
        LivingThing user = this.getOwner();
        List<Item> itemList = new ArrayList<>();
        for (Slot slot : getOwner().getInventory().getSlots()) {
            Item item = slot.getContainedItem();
            if (item != null) {
                itemList.add(item);
            }
        }
        if (itemList.isEmpty()) {
            return;
        }
        Item[] items = new Item[itemList.size()];
        System.out.println(user.getName() + "的背包");
        int i = 0;
        for (Item item : itemList) {
            items[i] = item;
            // 同种物品叠在一格，这里带上堆叠数量，否则「有几把」在界面上看不出来
            System.out.println(i + " " + item.getName()
                    + (item.getStackNumber() > 1 ? " x" + item.getStackNumber() : ""));
            i++;

        }
        System.out.println("输入需要使用的物品.一回合只能使用一次物品,使用物品不会占有释放技能的回合");
        Item usedItem;
        while (true) {
            // 这里用「先读一整行再解析」而不是 SCANNER.nextInt()：
            // nextInt() 会把换行符留在缓冲区里，导致随后的 nextLine() 读到一个空行。
            String line = nextLine();
            if (fightIsOver()) {
                return;
            }
            try {
                int input = Integer.parseInt(line.trim());
                usedItem = items[input];
                System.out.println(user.getName() + "使用了" + usedItem.getName());
                break;
            } catch (Exception e) {
                System.out.println("输入错误请重试");
            }
        }
        usedItem.comeToEffect(user, fight);//如果物品需要选择目标,自己写.
        // 只消耗一个：背包里同种物品是叠在一格的，removeOne 扣 1 点堆叠数（扣到 0 自动清空格子）
        user.getInventory().removeOne(usedItem);
    }
}
