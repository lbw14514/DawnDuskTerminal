package com.dawnduskterminal.item;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 绯色钼护甲（T2）
 *
 * <p>四件穿齐时持续给生命恢复 I。实现方式是每个部件在自己的 {@link #inventoryTick} 里检查整套，
 * 而不是挂一个全局的实体刻事件：检查本身就是四次槽位读取，放在这里最省事也最好读
 *
 * <p>每 10 tick 续一次 60 tick 的效果，留够了余量，中途脱掉一件最多 0.5 秒就断
 *
 * <p>穿戴贴图不走原版按材质名拼路径那一套（那会去找 {@code textures/models/armor/<材质>_layer_1.png}），
 * 直接覆盖 {@link #getArmorTexture} 指到源文件那张 {@code textures/entities/carmarmort1.png}，
 * 内外两层共用同一张
 *
 * <p>护甲纹饰一律扒掉（{@link #verifyComponentsAfterLoad}）：这套的贴图是一整张自绘的，
 * 叠上任意一条纹饰都会露馅
 */
public class ScarletMolybdArmorItem extends ArmorItem {
    private static final ResourceLocation ARMOR_TEXTURE = DawnDuskTerminal.id("textures/entities/carmarmort1.png");
    private static final int REFRESH_INTERVAL = 10;
    private static final int EFFECT_TICKS = 60;

    public ScarletMolybdArmorItem(Holder<ArmorMaterial> material, ArmorItem.Type type, Properties properties) {
        super(material, type, properties);
    }

    /** 源文件那张 64×64 的穿戴贴图，内外两层共用 */
    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return ARMOR_TEXTURE;
    }

    /** 读档时把纹饰组件扒掉：这条护甲不能挂纹饰 */
    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        stack.remove(DataComponents.TRIM);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        if (player.tickCount % REFRESH_INTERVAL != 0 || !hasFullSet(player)) {
            return;
        }
        // ambient 为 true，粒子少一点；showIcon 保留，玩家该看到自己的状态
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_TICKS, 0, true, true));
    }

    /** 四个槽位都是本套部件才算穿齐 */
    public static boolean hasFullSet(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(player.getItemBySlot(slot).getItem() instanceof ScarletMolybdArmorItem)) {
                return false;
            }
        }
        return true;
    }
}
