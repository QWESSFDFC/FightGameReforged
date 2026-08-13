package cn.gfhnv.game.system.thinkingSystem;

import cn.gfhnv.game.effect.Effect;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.entityController.UniversalController;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.ElementSort;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TurnEntry;
import cn.gfhnv.game.system.fight.TurnManager;
import cn.gfhnv.game.system.mana.Mana;

import java.util.*;

/**
 * AI写的
 */
public class ThinkingControllerAI extends UniversalController {

    // ----- 构造器保持不变 -----
    public ThinkingControllerAI(UniversalController universalController, LivingThing owner) {
        super(universalController, owner);
    }

    public ThinkingControllerAI(List<Skill> skills, LivingThing owner) {
        super(skills, owner);
    }

    // ================================================================
    // 主决策入口
    // ================================================================
    @Override
    public void act(Fight fight) {
        LivingThing self = getOwner();
        if (self.getTags() == null) {
            System.out.println(self.getName() + " 没有思维标签，采用随机行动");
            super.act(fight);
            return;
        }
        // ----- 1. 无Tag则无法思考，回退随机（父类） -----
        if (self.getTags().isEmpty()) {
            System.out.println(self.getName() + " 没有思维标签，采用随机行动");
            super.act(fight);
            return;
        }

        // ----- 2. 获取所有可用技能（必须能对至少一个目标使用）-----
        List<Skill> allSkills = this.getSkills();
        List<Skill> availableSkills = new ArrayList<>();
        for (Skill skill : allSkills) {
            if (skill == null) continue;
            // 获取该技能可作用的目标候选
            List<LivingThing> candidates = getSkillCandidates(fight, self, skill);
            if (candidates == null || candidates.isEmpty()) continue;
            // 检查是否至少有一个目标满足 canUse（带具体目标）
            for (LivingThing target : candidates) {
                if (skill.canUse(fight, self, Collections.singletonList(target))) {
                    availableSkills.add(skill);
                    break;
                }
            }
        }

        if (availableSkills.isEmpty()) {
            System.out.println(self.getName() + " 无可用技能，执行普通攻击");
            super.act(fight);
            return;
        }

        // ----- 3. 读取各策略的基础权重（来自实体 Tag 表）-----
        double attackWeight = getWeight(TagType.ATTACK);
        double defenseWeight = getWeight(TagType.DEFENCE);
        double healWeight = getWeight(TagType.HEAL);
        double manaWeight = getWeight(TagType.RESTORATION_MANA);
        double damageEnhanceWeight = getWeight(TagType.DAMAGE_ENHANCE);

        // ----- 4. 预测下一回合敌方可能造成的最大伤害（如果下一位是敌人）-----
        double predictedEnemyDamage = 0;
        if (!TurnManager.getTurns().isEmpty()) {
            TurnEntry nextTurn = TurnManager.getTurns().getFirst();
            LivingThing nextActor = nextTurn.getLivingThing();
            if (nextActor != null && fight.getOpponentList(self).contains(nextActor) && nextActor != self) {
                long maxDmg = 0;
                for (Skill skill : nextActor.getController().getSkills()) {
                    if (skill == null) continue;
                    // 检查该技能能否对「自己」使用
                    if (skill.canUse(fight, nextActor, Collections.singletonList(self))) {
                        long dmg = skill.getAnticipatedDamage(self, nextActor); // 参数：(被攻击者, 攻击者)
                        if (dmg > maxDmg) maxDmg = dmg;
                    }
                }
                predictedEnemyDamage = maxDmg;
            }
        }

        // ----- 5. 根据战况动态调整权重（核心策略）-----
        // 5.1 如果会被敌方秒杀，极大提升治疗/防御优先级
        if (self.getHp() - predictedEnemyDamage <= 0) {
            healWeight *= 10;
            defenseWeight *= 5;
        }
        // 5.2 血量健康（>60%）且敌人数量多，提高攻击权重
        if ((double) self.getHp() / self.getHpMax() > 0.6 &&
                fight.getOpponentList(self).size() >= 2) {
            attackWeight *= 1.5;
        }
        // 5.3 蓝量低于30%时提高回蓝权重
        double manaPercent = getTotalManaPercent(self);
        if (manaPercent < 0.3) {
            manaWeight *= 3;
        }
        // 5.4 自身带有负面效果时，防御/驱散优先级提高
        boolean hasDebuff = self.getEntityEffectList().stream().anyMatch(Effect::isNegative);
        if (hasDebuff) {
            defenseWeight *= 2;
        }

        // 将所有权重放入 Map 方便选择
        Map<TagType, Double> weightMap = new HashMap<>();
        weightMap.put(TagType.ATTACK, attackWeight);
        weightMap.put(TagType.DEFENCE, defenseWeight);
        weightMap.put(TagType.HEAL, healWeight);
        weightMap.put(TagType.RESTORATION_MANA, manaWeight);
        weightMap.put(TagType.DAMAGE_ENHANCE, damageEnhanceWeight);

        // 选出权重最高的策略 Tag
        TagType selectedTag = TagType.ATTACK;
        double maxWeight = 0;
        for (Map.Entry<TagType, Double> entry : weightMap.entrySet()) {
            if (entry.getValue() > maxWeight) {
                maxWeight = entry.getValue();
                selectedTag = entry.getKey();
            }
        }
        if (maxWeight == 0) selectedTag = TagType.ATTACK;

        // ----- 6. 从可用技能中筛选出匹配所选 Tag 的技能 -----
        List<Skill> candidateSkills = new ArrayList<>();
        for (Skill skill : availableSkills) {
            TagType skillTag = getSkillMainTag(skill);
            if (skillTag == selectedTag) {
                candidateSkills.add(skill);
            }
        }

        // 如果没有匹配技能，退化为攻击技能，再没有就用全部
        if (candidateSkills.isEmpty()) {
            for (Skill skill : availableSkills) {
                if (getSkillMainTag(skill) == TagType.ATTACK) {
                    candidateSkills.add(skill);
                }
            }
            if (candidateSkills.isEmpty()) {
                candidateSkills.addAll(availableSkills);
            }
        }

        // ----- 7. 对候选技能+目标组合进行综合评分，选出最优解 -----
        Skill bestSkill = null;
        LivingThing bestTarget = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Skill skill : candidateSkills) {
            List<LivingThing> candidates = getSkillCandidates(fight, self, skill);
            if (candidates == null || candidates.isEmpty()) continue;
            for (LivingThing target : candidates) {
                if (!skill.canUse(fight, self, Collections.singletonList(target))) continue;
                double score = evaluateAction(fight, skill, target);
                if (score > bestScore) {
                    bestScore = score;
                    bestSkill = skill;
                    bestTarget = target;
                }
            }
        }

        // ----- 8. 执行最优行动，若无则回退 -----
        if (bestSkill != null && bestTarget != null) {
            System.out.printf("%s 使用 %s 对 %s%n",
                    self.getName(), bestSkill.getName(), bestTarget.getName());
            bestSkill.use(fight, self, Collections.singletonList(bestTarget));
        } else {
            System.out.println(self.getName() + " 无法决策，执行普通攻击");
            super.act(fight);
        }
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 获取实体某个 Tag 的权重（不存在返回 0）
     */
    private double getWeight(TagType tagType) {
        if (!getOwner().getTags().containsKey(tagType)) return 0;
        return getOwner().getTags().get(tagType).getWeight();
    }

    /**
     * 获取技能的主要 TagType（取 tags Map 中第一个键）
     * 注意：你的 Skill 类中 tags 是 Map<TagType, Tag>，通常一个技能只挂一个主要 Tag
     */
    private TagType getSkillMainTag(Skill skill) {
        Map<TagType, Tag> tags = skill.getTags();
        if (tags == null || tags.isEmpty()) return TagType.ATTACK; // 默认攻击
        return tags.keySet().iterator().next();
    }

    /**
     * 获取技能可作用的目标候选列表（根据 aims 和 isForEnemies）
     */
    private List<LivingThing> getSkillCandidates(Fight fight, LivingThing self, Skill skill) {
        int aims = skill.getAims();
        List<LivingThing> pool;
        if (skill.isForEnemies()) {
            pool = new ArrayList<>(fight.getOpponentList(self));
        } else {
            // 己方目标：包括自己 + 队友（如果有多角色）
            pool = new ArrayList<>(fight.getOwnList(self));
            if (!pool.contains(self)) pool.add(self);
        }

        if (pool.isEmpty()) return Collections.emptyList();

        // aims = 0 → 自身
        if (aims == 0) {
            return Collections.singletonList(self);
        }
        // aims = -1 → 全部
        if (aims == -1) {
            return new ArrayList<>(pool);
        }
        // aims > 0 → 返回前 aims 个（简化处理，实际可做全排列选最优，但为性能只取前 N）
        if (aims > 0 && aims < pool.size()) {
            return new ArrayList<>(pool.subList(0, aims));
        }
        return new ArrayList<>(pool);
    }

    /**
     * 计算实体的总蓝量百分比（所有元素蓝量之和 / 总上限之和）
     */
    private double getTotalManaPercent(LivingThing entity) {
        List<Mana> manas = entity.getManas();
        if (manas == null || manas.isEmpty()) return 0;
        double total = 0, max = 0;
        for (Mana m : manas) {
            total += m.getAmount();
            max += m.getAmountMax();
        }
        return max == 0 ? 0 : total / max;
    }

    /**
     * 评估某个技能-目标组合的得分，分数越高越倾向执行
     */
    private double evaluateAction(Fight fight, Skill skill, LivingThing target) {
        LivingThing self = getOwner();
        TagType tag = getSkillMainTag(skill);
        double baseScore = getWeight(tag);          // 基础分来自实体 Tag 权重
        double bonus = 0;

        switch (tag) {
            case ATTACK: {
                // 目标血量越低，得分越高
                double targetHpRatio = (double) target.getHp() / target.getHpMax();
                bonus += (1 - targetHpRatio) * 15;
                // 预测伤害，如果能击杀则极大加分
                long predicted = skill.getAnticipatedDamage(target, self);
                if (predicted >= target.getHp()) {
                    bonus += 30;
                }
                // 如果目标有负面状态（易伤等），额外加分（这里简化）
                break;
            }
            case HEAL: {
                // 治疗目标血量越低，得分越高（通常目标是自身或队友）
                double healTargetHpRatio = (double) target.getHp() / target.getHpMax();
                bonus += (1 - healTargetHpRatio) * 20;
                // 如果目标即将死亡（血量<20%），额外加分
                if (healTargetHpRatio < 0.2) {
                    bonus += 15;
                }
                break;
            }
            case DEFENCE: {
                // 自身血量低时防御价值高
                if ((double) self.getHp() / self.getHpMax() < 0.4) {
                    bonus += 15;
                }
                // 如果自身有负面效果，防御技能可能附带驱散，加分
                if (self.getEntityEffectList().stream().anyMatch(Effect::isNegative)) {
                    bonus += 10;
                }
                break;
            }
            case RESTORATION_MANA: {
                // 自身蓝量越低，回蓝收益越高
                double manaPercent = getTotalManaPercent(self);
                bonus += (1 - manaPercent) * 15;
                break;
            }
            case DAMAGE_ENHANCE: {
                // 如果自身还没有增伤Buff，或者增伤即将结束，提高价值
                boolean hasDmgBuff = self.getEntityEffectList().stream()
                        .anyMatch(e -> e.getId() != null && e.getId().contains("damageEnhance"));
                if (!hasDmgBuff) {
                    bonus += 10;
                }
                break;
            }
            default:
                break;
        }

        // 成本惩罚：法力消耗（如果有）
        double costPenalty = 0;
        Mana consumed = skill.getConsumedMana();
        if (consumed != null) {
            // 根据不同元素取对应蓝量百分比作为惩罚
            double manaPercent = getManaPercentByElement(self, consumed.getElementSort());
            costPenalty = (1 - manaPercent) * 5; // 蓝越少，用大耗蓝技能越亏
        }

        return baseScore + bonus - costPenalty;
    }

    /**
     * 获取指定元素蓝量的百分比
     */
    private double getManaPercentByElement(LivingThing entity, ElementSort element) {
        for (Mana m : entity.getManas()) {
            if (m.getElementSort() == element) {
                return m.getAmountMax() == 0 ? 0 : m.getAmount() / m.getAmountMax();
            }
        }
        return 0;
    }
}