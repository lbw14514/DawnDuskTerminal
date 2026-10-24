package com.dawnduskterminal.client.model;

import com.dawnduskterminal.DawnDuskTerminal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;

/**
 * 绯色钼护甲（T2）的穿戴几何
 *
 * <p>四个槽位各一份模型，与 {@code item/ScarletMolybdArmorItem} 一一对应；贴图由那件物品的
 * {@code getArmorTexture} 指到 {@code textures/entities/carmarmort1.png}，这里只管几何
 */

public final class ScarletMolybdenumArmorModel {
    public static final ModelLayerLocation HELMET_LAYER = layer("scarlet_molybdenum_helmet");
    public static final ModelLayerLocation CHESTPLATE_LAYER = layer("scarlet_molybdenum_chestplate");
    public static final ModelLayerLocation LEGGINGS_LAYER = layer("scarlet_molybdenum_leggings");
    public static final ModelLayerLocation BOOTS_LAYER = layer("scarlet_molybdenum_boots");
    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> MODELS = new EnumMap<>(EquipmentSlot.class);

    private ScarletMolybdenumArmorModel() {
    }

    public static void registerLayerDefinitions(RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HELMET_LAYER, ScarletMolybdenumArmorModel::helmetLayer);
        event.registerLayerDefinition(CHESTPLATE_LAYER, ScarletMolybdenumArmorModel::chestplateLayer);
        event.registerLayerDefinition(LEGGINGS_LAYER, ScarletMolybdenumArmorModel::leggingsLayer);
        event.registerLayerDefinition(BOOTS_LAYER, ScarletMolybdenumArmorModel::bootsLayer);
    }

    public static HumanoidModel<LivingEntity> model(EquipmentSlot slot) {
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) {
            throw new IllegalArgumentException("scarlet molybdenum armor has no model for slot " + slot);
        } else {
            return MODELS.computeIfAbsent(slot, ScarletMolybdenumArmorModel::build);
        }
    }

    private static HumanoidModel<LivingEntity> build(EquipmentSlot slot) {
        EntityModelSet models = Minecraft.getInstance().getEntityModels();
        Map<String, ModelPart> parts = new HashMap<>(3);
        switch (slot) {
            case HEAD:
                ModelPart helmet = models.bakeLayer(HELMET_LAYER);
                parts.put("head", helmet.getChild("Head"));
                break;
            case CHEST:
                ModelPart chestplate = models.bakeLayer(CHESTPLATE_LAYER);
                parts.put("body", chestplate.getChild("Body"));
                parts.put("right_arm", chestplate.getChild("RightArm"));
                parts.put("left_arm", chestplate.getChild("LeftArm"));
                break;
            case LEGS:
                ModelPart leggings = models.bakeLayer(LEGGINGS_LAYER);
                parts.put("body", leggings.getChild("Body"));
                parts.put("right_leg", leggings.getChild("RightLeg"));
                parts.put("left_leg", leggings.getChild("LeftLeg"));
                break;
            case FEET:
                ModelPart boots = models.bakeLayer(BOOTS_LAYER);
                parts.put("right_leg", boots.getChild("RightLeg"));
                parts.put("left_leg", boots.getChild("LeftLeg"));
                break;
            default:
                throw new IllegalArgumentException("scarlet molybdenum armor has no model for slot " + slot);
        }

        return new HumanoidModel(new ModelPart(List.of(), withEmptyParts(parts)));
    }

    private static Map<String, ModelPart> withEmptyParts(Map<String, ModelPart> parts) {
        Map<String, ModelPart> children = new HashMap<>(7);

        for (String name : List.of("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
            children.put(name, parts.getOrDefault(name, new ModelPart(List.of(), Map.of())));
        }

        return children;
    }

    private static ModelLayerLocation layer(String path) {
        return new ModelLayerLocation(DawnDuskTerminal.id(path), "main");
    }

    private static LayerDefinition helmetLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "Head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(1.0F))
                .texOffs(24, 26)
                .addBox(-5.5F, -4.5F, -0.5F, 11.0F, 3.0F, 3.0F, new CubeDeformation(0.5F)),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition chestplateLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild(
            "Body",
            CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.76F))
                .texOffs(24, 16)
                .addBox(-4.0F, 1.0F, -3.0F, 8.0F, 4.0F, 6.0F, new CubeDeformation(1.11F))
                .texOffs(32, 0)
                .addBox(-2.0F, 6.0F, -3.0F, 4.0F, 4.0F, 6.0F, new CubeDeformation(0.51F)),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "Chestplate_r1",
            CubeListBuilder.create().texOffs(32, 10).addBox(-2.0F, -1.0F, -0.5F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.5F)),
            PartPose.offsetAndRotation(0.0F, 11.6352F, 3.8715F, 0.6109F, 0.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "Chestplate_r2",
            CubeListBuilder.create().texOffs(32, 10).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.5F)),
            PartPose.offsetAndRotation(0.0F, 12.25F, -1.25F, -0.6109F, 0.0F, 0.0F)
        );
        PartDefinition rightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild(
            "RightArmArmor_r1",
            CubeListBuilder.create().texOffs(40, 41).addBox(-3.0F, -2.5F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(1.1F)),
            PartPose.offsetAndRotation(-1.0F, 0.5F, 0.0F, 0.0F, 0.0F, 0.4363F)
        );
        rightArm.addOrReplaceChild(
            "RightArmArmor_r2",
            CubeListBuilder.create().texOffs(40, 32).addBox(1.0F, 1.0F, 1.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.9F)),
            PartPose.offsetAndRotation(-4.0F, -2.0F, -3.0F, 0.0F, 0.0F, 0.1745F)
        );
        PartDefinition leftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild(
            "LeftArmArmor_r1",
            CubeListBuilder.create().texOffs(40, 32).mirror().addBox(-5.0F, 1.0F, 1.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.9F)).mirror(false),
            PartPose.offsetAndRotation(4.0F, -2.0F, -3.0F, 0.0F, 0.0F, -0.1745F)
        );
        leftArm.addOrReplaceChild(
            "LeftArmArmor_r2",
            CubeListBuilder.create().texOffs(40, 41).mirror().addBox(-1.0F, -2.5F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(1.1F)).mirror(false),
            PartPose.offsetAndRotation(1.0F, 0.5F, 0.0F, 0.0F, 0.0F, -0.4363F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition leggingsLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "Body",
            CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, 8.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.51F)),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        PartDefinition rightLeg = root.addOrReplaceChild(
            "RightLeg",
            CubeListBuilder.create().texOffs(24, 32).addBox(-2.1F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F, new CubeDeformation(0.52F)),
            PartPose.offset(-1.9F, 12.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
            "RightLegArmor_r1",
            CubeListBuilder.create().texOffs(28, 45).addBox(-2.5F, -3.0F, -0.5F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.51F)),
            PartPose.offsetAndRotation(1.9F, 2.0F, -3.75F, -0.1745F, 0.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
            "RightLegArmor_r2",
            CubeListBuilder.create().texOffs(28, 45).addBox(-2.5F, -3.0F, -0.5F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.51F)),
            PartPose.offsetAndRotation(1.9F, 2.0F, 3.75F, 0.1745F, 0.0F, 0.0F)
        );
        PartDefinition leftLeg = root.addOrReplaceChild(
            "LeftLeg",
            CubeListBuilder.create().texOffs(24, 32).mirror().addBox(-1.9F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F, new CubeDeformation(0.52F)).mirror(false),
            PartPose.offset(1.9F, 12.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
            "LeftLegArmor_r1",
            CubeListBuilder.create().texOffs(28, 45).addBox(-2.5F, -3.0F, -0.5F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.51F)),
            PartPose.offsetAndRotation(-1.9F, 2.0F, -3.75F, -0.1745F, 0.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
            "LeftLegArmor_r2",
            CubeListBuilder.create().texOffs(28, 45).addBox(-2.5F, -3.0F, -0.5F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.51F)),
            PartPose.offsetAndRotation(-1.9F, 2.0F, 3.75F, 0.1745F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition bootsLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "LeftLeg",
            CubeListBuilder.create().texOffs(0, 40).mirror().addBox(-2.0F, 6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(1.0F)).mirror(false),
            PartPose.offset(1.9F, 12.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "RightLeg",
            CubeListBuilder.create().texOffs(0, 40).addBox(-2.0F, 6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(1.0F)),
            PartPose.offset(-1.9F, 12.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }
}
