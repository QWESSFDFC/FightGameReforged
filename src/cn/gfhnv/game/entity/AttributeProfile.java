package cn.gfhnv.game.entity;

import cn.gfhnv.game.data.NoConfig;

/**
 * 生物的「属性组件」：五行组 + 全局组。
 * <p>
 * <b>为什么单独一个类</b>：这些字段在 {@link LivingThing} 里原本是一堆散落的机械重复
 * （五行那一族是 5 个元素 × 4 种属性），收进来以后 {@code copyFrom} / {@code resetTemporary}
 * 各自只有一处，不会再加一个元素就漏抄一半。
 * <p>
 * <b>两类属性，两张互补的表</b>（规则见 {@code ENTITY-ATTRIBUTE-SPLIT-2026-10.md} 第二节）：
 * <ul>
 *     <li><b>面板属性</b> —— 配置出来、一局内不变：五行组的 5 抗性 + 5 法力成长，
 *     全局组的 {@code penetration} / {@code enhance} / {@code criticalDMG}。
 *     {@link #copyFrom} 要把它们带过去，{@link #resetTemporary} <b>不动</b>它们；</li>
 *     <li><b>临时属性</b> —— 效果/技能给的一次性加成：五行组的 5 单元素穿透 + 5 元素增伤。
 *     不复制，整场结束时由 {@link #resetTemporary} 清零。</li>
 * </ul>
 * <p>
 * <b>字段名就是 {@code /data} 的键名</b>（数据名是对外 API，见 TIPS §5.10）——
 * 所以这里用<b>显式命名</b>的字段，不用 {@code EnumMap}：用表的话字段名就没了，键名也就跟着没了。
 * {@link LivingThing} 用 {@code @DataFlatten} 把这些字段<b>无前缀</b>并进自己的复合标签，
 * 所以 {@code /data get entity @s} 里看到的仍是 {@code fireResistance}、{@code penetration}、
 * {@code metalManaGrow}…… 与它们还写在 {@code LivingThing} 里时逐个字符一致。
 * <p>
 * <b>访问入口只有 {@link LivingThing} 上的转发 getter/setter</b>（那 460 个调用点靠它们），
 * 本类的方法只给 {@code LivingThing} 自己用。
 *
 * @author AI（DeepSeek）生成
 */
public class AttributeProfile {

    /**
     * 那 10 个临时属性（5 单元素穿透 + 5 元素增伤）共同的 {@code @NoConfig} 理由。
     * <p>
     * <b>为什么 10 个字段共用一句话</b>：它们的生命周期完全一样（不复制、清零、零写入点），
     * 逐字抄 10 遍只会让"改一个忘九个"变得更容易。真正的依据写在
     * {@link #metalPenetration} 的 javadoc 与 {@code copyFrom} / {@code resetTemporary} 上。
     * <p>
     * <b>这句话必须是真的</b>（{@code @NoConfig} 的纪律：写不出真理由就不该挡）——
     * 三条依据：① {@link #copyFrom(AttributeProfile)} 刻意不带这 10 个；
     * ② {@link #resetTemporary()} 每局结束清零；③ 全项目零写入点（{@code DamageCalculate} 只读）。
     * 于是"配在注册表模板上"对任何一局战斗都不生效。
     */
    static final String TEMPORARY_REASON =
            "临时属性：copyFrom 刻意不带（副本从干净状态开始）、每局结束 resetTemporary 清零，"
                    + "且全项目零写入点（预留未接线）→ 配在模板上不生效，要加请用效果/技能";

    /* ------------------------------------------------------------------
     * ① 五行组 · 五行抗性（面板属性：复制，不清零）
     * ------------------------------------------------------------------ */

    /**
     * 金抗性。
     */
    private double metalResistance;
    /**
     * 木抗性。
     */
    private double woodResistance;
    /**
     * 水抗性。
     */
    private double waterResistance;
    /**
     * 火抗性。
     */
    private double fireResistance;
    /**
     * 土抗性。
     */
    private double dirtResistance;

    /* ------------------------------------------------------------------
     * ② 五行组 · 五元素穿透（临时属性：不复制，清零）
     *
     * 这 5 个 + ③ 那 5 个一共 10 个，是 2026-10-03「反射混血」第 2 步逐个判断过的那一批：
     * 结论是**都不能配**，所以每个字段都写了 @NoConfig（理由逐字相同，见下面那一行）。
     * ------------------------------------------------------------------ */

    /**
     * 金元素穿透。
     * <p>
     * <b>不进配置文件</b>（{@code @NoConfig}）：临时属性，而且<b>配了不生效</b> ——
     * {@link #copyFrom(AttributeProfile)} 刻意不带它（副本从干净状态开始）、
     * {@link #resetTemporary()} 每局结束清零，而全项目<b>零写入点</b>（预留未接线，
     * 只被伤害计算读来加算）。要加穿透请用效果/技能。
     */
    @NoConfig(TEMPORARY_REASON)
    private double metalPenetration;
    /**
     * 木元素穿透。
     */
    @NoConfig(TEMPORARY_REASON)
    private double woodPenetration;
    /**
     * 水元素穿透。
     */
    @NoConfig(TEMPORARY_REASON)
    private double waterPenetration;
    /**
     * 火元素穿透。
     */
    @NoConfig(TEMPORARY_REASON)
    private double firePenetration;
    /**
     * 土元素穿透。
     */
    @NoConfig(TEMPORARY_REASON)
    private double dirtPenetration;

    /* ------------------------------------------------------------------
     * ③ 五行组 · 五元素增伤（临时属性：不复制，清零）
     * ------------------------------------------------------------------ */

    /**
     * 金元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double metalDamageEnhance;
    /**
     * 木元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double woodDamageEnhance;
    /**
     * 水元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double waterDamageEnhance;
    /**
     * 火元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double fireDamageEnhance;
    /**
     * 土元素伤害增强。
     */
    @NoConfig(TEMPORARY_REASON)
    private double dirtDamageEnhance;

    /* ------------------------------------------------------------------
     * ④ 五行组 · 五行法力成长系数（面板属性：复制，不清零）
     * ------------------------------------------------------------------ */

    /**
     * 金法力成长系数。<b>对外数据名是 {@code metalManaGrow}</b>（下面四个同理）——
     * Java 名与数据名一致，所以这五个都不需要改名注解（{@code @DataField}）。
     */
    private double metalManaGrow;
    /**
     * 木法力成长系数。
     */
    private double woodManaGrow;
    /**
     * 水法力成长系数。
     */
    private double waterManaGrow;
    /**
     * 火法力成长系数。
     */
    private double fireManaGrow;
    /**
     * 土法力成长系数。
     */
    private double dirtManaGrow;

    /* ------------------------------------------------------------------
     * ⑤ 全局组（面板属性：复制，不清零）
     * ------------------------------------------------------------------ */

    /**
     * 全属性穿透：伤害计算时与「单元素穿透」<b>相加</b>（见 {@code DamageCalculate}）。
     * <p>
     * 它和 5 个单元素穿透不是一个生命周期：这个是面板属性，那 5 个是临时属性。
     */
    private double penetration;
    /**
     * 全属性增伤百分比。
     */
    private double enhance;
    /**
     * 基础暴击伤害倍率（最终值见 {@link LivingThing#getCriticalDMG()}，
     * 还会叠加 {@code criticalDMGEnhancePercent} / {@code criticalDMGEnhanceAmount}）。
     */
    private double criticalDMG;

    /**
     * 从另一个组件复制<b>面板属性</b>：
     * 五行组的 5 抗性 + 5 法力成长，以及全局组的 {@code penetration} / {@code enhance} / {@code criticalDMG}。
     * <p>
     * <b>刻意不带 5 单元素穿透与 5 元素增伤</b>：那是效果/技能给的一次性加成，
     * 副本必须从干净的状态开始（与 {@link #resetTemporary} 是互补的两张表，
     * 改一个记得看另一个）。
     *
     * @param other 被复制的组件
     */
    public void copyFrom(AttributeProfile other) {
        // 五行组：抗性 + 法力成长
        this.metalResistance = other.metalResistance;
        this.woodResistance = other.woodResistance;
        this.waterResistance = other.waterResistance;
        this.fireResistance = other.fireResistance;
        this.dirtResistance = other.dirtResistance;
        this.metalManaGrow = other.metalManaGrow;
        this.woodManaGrow = other.woodManaGrow;
        this.waterManaGrow = other.waterManaGrow;
        this.fireManaGrow = other.fireManaGrow;
        this.dirtManaGrow = other.dirtManaGrow;
        // 全局组
        this.penetration = other.penetration;
        this.enhance = other.enhance;
        this.criticalDMG = other.criticalDMG;
    }

    /**
     * 把<b>临时属性</b>清零：五行组的 5 单元素穿透 + 5 元素增伤。
     * <p>
     * <b>抗性、法力成长与全局组（{@code penetration} / {@code enhance} / {@code criticalDMG}）不清</b> ——
     * 它们是面板属性，清掉就是永久削弱这个角色。
     * <p>
     * 由 {@link LivingThing#clearTemporaryAttributes()} 在整场战斗结束时调用：
     * 这两组是"战斗中一次性给出去"的加成，留着会渗进下一局。
     */
    public void resetTemporary() {
        this.metalPenetration = 0;
        this.woodPenetration = 0;
        this.waterPenetration = 0;
        this.firePenetration = 0;
        this.dirtPenetration = 0;
        this.metalDamageEnhance = 0;
        this.woodDamageEnhance = 0;
        this.waterDamageEnhance = 0;
        this.fireDamageEnhance = 0;
        this.dirtDamageEnhance = 0;
    }

    /* ------------------------------------------------------------------
     * ① 五行抗性
     * ------------------------------------------------------------------ */

    /**
     * @return 金抗性
     */
    public double getMetalResistance() {
        return metalResistance;
    }

    /**
     * @param metalResistance 金抗性
     */
    public void setMetalResistance(double metalResistance) {
        this.metalResistance = metalResistance;
    }

    /**
     * @return 木抗性
     */
    public double getWoodResistance() {
        return woodResistance;
    }

    /**
     * @param woodResistance 木抗性
     */
    public void setWoodResistance(double woodResistance) {
        this.woodResistance = woodResistance;
    }

    /**
     * @return 水抗性
     */
    public double getWaterResistance() {
        return waterResistance;
    }

    /**
     * @param waterResistance 水抗性
     */
    public void setWaterResistance(double waterResistance) {
        this.waterResistance = waterResistance;
    }

    /**
     * @return 火抗性
     */
    public double getFireResistance() {
        return fireResistance;
    }

    /**
     * @param fireResistance 火抗性
     */
    public void setFireResistance(double fireResistance) {
        this.fireResistance = fireResistance;
    }

    /**
     * @return 土抗性
     */
    public double getDirtResistance() {
        return dirtResistance;
    }

    /**
     * @param dirtResistance 土抗性
     */
    public void setDirtResistance(double dirtResistance) {
        this.dirtResistance = dirtResistance;
    }

    /* ------------------------------------------------------------------
     * ② 五元素穿透
     * ------------------------------------------------------------------ */

    /**
     * @return 金元素穿透
     */
    public double getMetalPenetration() {
        return metalPenetration;
    }

    /**
     * @param metalPenetration 金元素穿透
     */
    public void setMetalPenetration(double metalPenetration) {
        this.metalPenetration = metalPenetration;
    }

    /**
     * @return 木元素穿透
     */
    public double getWoodPenetration() {
        return woodPenetration;
    }

    /**
     * @param woodPenetration 木元素穿透
     */
    public void setWoodPenetration(double woodPenetration) {
        this.woodPenetration = woodPenetration;
    }

    /**
     * @return 水元素穿透
     */
    public double getWaterPenetration() {
        return waterPenetration;
    }

    /**
     * @param waterPenetration 水元素穿透
     */
    public void setWaterPenetration(double waterPenetration) {
        this.waterPenetration = waterPenetration;
    }

    /**
     * @return 火元素穿透
     */
    public double getFirePenetration() {
        return firePenetration;
    }

    /**
     * @param firePenetration 火元素穿透
     */
    public void setFirePenetration(double firePenetration) {
        this.firePenetration = firePenetration;
    }

    /**
     * @return 土元素穿透
     */
    public double getDirtPenetration() {
        return dirtPenetration;
    }

    /**
     * @param dirtPenetration 土元素穿透
     */
    public void setDirtPenetration(double dirtPenetration) {
        this.dirtPenetration = dirtPenetration;
    }

    /* ------------------------------------------------------------------
     * ③ 五元素增伤
     * ------------------------------------------------------------------ */

    /**
     * @return 金元素伤害增强
     */
    public double getMetalDamageEnhance() {
        return metalDamageEnhance;
    }

    /**
     * @param metalDamageEnhance 金元素伤害增强
     */
    public void setMetalDamageEnhance(double metalDamageEnhance) {
        this.metalDamageEnhance = metalDamageEnhance;
    }

    /**
     * @return 木元素伤害增强
     */
    public double getWoodDamageEnhance() {
        return woodDamageEnhance;
    }

    /**
     * @param woodDamageEnhance 木元素伤害增强
     */
    public void setWoodDamageEnhance(double woodDamageEnhance) {
        this.woodDamageEnhance = woodDamageEnhance;
    }

    /**
     * @return 水元素伤害增强
     */
    public double getWaterDamageEnhance() {
        return waterDamageEnhance;
    }

    /**
     * @param waterDamageEnhance 水元素伤害增强
     */
    public void setWaterDamageEnhance(double waterDamageEnhance) {
        this.waterDamageEnhance = waterDamageEnhance;
    }

    /**
     * @return 火元素伤害增强
     */
    public double getFireDamageEnhance() {
        return fireDamageEnhance;
    }

    /**
     * @param fireDamageEnhance 火元素伤害增强
     */
    public void setFireDamageEnhance(double fireDamageEnhance) {
        this.fireDamageEnhance = fireDamageEnhance;
    }

    /**
     * @return 土元素伤害增强
     */
    public double getDirtDamageEnhance() {
        return dirtDamageEnhance;
    }

    /**
     * @param dirtDamageEnhance 土元素伤害增强
     */
    public void setDirtDamageEnhance(double dirtDamageEnhance) {
        this.dirtDamageEnhance = dirtDamageEnhance;
    }

    /* ------------------------------------------------------------------
     * ④ 五行法力成长系数
     * ------------------------------------------------------------------ */

    /**
     * @return 金法力成长系数
     */
    public double getMetalManaGrow() {
        return metalManaGrow;
    }

    /**
     * @param metalManaGrow 金法力成长系数
     */
    public void setMetalManaGrow(double metalManaGrow) {
        this.metalManaGrow = metalManaGrow;
    }

    /**
     * @return 木法力成长系数
     */
    public double getWoodManaGrow() {
        return woodManaGrow;
    }

    /**
     * @param woodManaGrow 木法力成长系数
     */
    public void setWoodManaGrow(double woodManaGrow) {
        this.woodManaGrow = woodManaGrow;
    }

    /**
     * @return 水法力成长系数
     */
    public double getWaterManaGrow() {
        return waterManaGrow;
    }

    /**
     * @param waterManaGrow 水法力成长系数
     */
    public void setWaterManaGrow(double waterManaGrow) {
        this.waterManaGrow = waterManaGrow;
    }

    /**
     * @return 火法力成长系数
     */
    public double getFireManaGrow() {
        return fireManaGrow;
    }

    /**
     * @param fireManaGrow 火法力成长系数
     */
    public void setFireManaGrow(double fireManaGrow) {
        this.fireManaGrow = fireManaGrow;
    }

    /**
     * @return 土法力成长系数
     */
    public double getDirtManaGrow() {
        return dirtManaGrow;
    }

    /**
     * @param dirtManaGrow 土法力成长系数
     */
    public void setDirtManaGrow(double dirtManaGrow) {
        this.dirtManaGrow = dirtManaGrow;
    }

    /* ------------------------------------------------------------------
     * ⑤ 全局组
     * ------------------------------------------------------------------ */

    /**
     * @return 全属性穿透
     */
    public double getPenetration() {
        return penetration;
    }

    /**
     * @param penetration 全属性穿透
     */
    public void setPenetration(double penetration) {
        this.penetration = penetration;
    }

    /**
     * @return 全属性增伤百分比
     */
    public double getEnhance() {
        return enhance;
    }

    /**
     * @param enhance 全属性增伤百分比
     */
    public void setEnhance(double enhance) {
        this.enhance = enhance;
    }

    /**
     * @return 基础暴击伤害倍率（不含 {@code criticalDMGEnhance*} 的加成）
     */
    public double getCriticalDMG() {
        return criticalDMG;
    }

    /**
     * @param criticalDMG 基础暴击伤害倍率
     */
    public void setCriticalDMG(double criticalDMG) {
        this.criticalDMG = criticalDMG;
    }
}
