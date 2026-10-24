package com.dawnduskterminal.item;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.minecraft.world.item.Item.Properties;

/**
 * 试验物品 047~050：那套护甲
 *
 * <p>四件共用一个材质 {@link com.dawnduskterminal.registry.ModArmorMaterials#EXPERIMENT_ARMOR}，
 * 穿戴贴图是源文件那张 64×64 的 {@code textures/entities/experiment_armor.png}，内外两层共用；
 * 发光层由 {@code mixin/ExperimentArmorGlowMixin} 叠上去，暗处也看得见
 */

public class ExperimentArmorItem extends ArmorItem {
    public static final ResourceLocation ARMOR_TEXTURE = DawnDuskTerminal.id("textures/entities/experiment_armor.png");

    public ExperimentArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, Layer layer,
                                            boolean innerModel) {
        return ARMOR_TEXTURE;
    }

    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        stack.remove(DataComponents.TRIM);
    }
}
