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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;

/**
 * 试验物品 047~050 那套护甲的穿戴几何
 *
 * <p>源文件是基岩版模型，四个槽位各一份几何，坐标照搬；{@code registerLayerDefinitions} 在客户端 init
 * 里挂上去，{@code model(slot)} 按槽位取模型给 {@code IClientItemExtensions#getHumanoidArmorModel}。
 * 贴图由 {@code ExperimentArmorItem#getArmorTexture} 指到 {@code textures/entities/experiment_armor.png}，
 * 发光层在 {@code mixin/ExperimentArmorGlowMixin} 里叠
 */

public final class ExperimentArmorModel {
    public static final ModelLayerLocation HELMET_LAYER = layer("experiment_armor_helmet");
    public static final ModelLayerLocation CHESTPLATE_LAYER = layer("experiment_armor_chestplate");
    public static final ModelLayerLocation LEGGINGS_LAYER = layer("experiment_armor_leggings");
    public static final ModelLayerLocation BOOTS_LAYER = layer("experiment_armor_boots");
    public static final ResourceLocation GLOW_TEXTURE = DawnDuskTerminal.id("textures/entities/experiment_armor_glow.png");
    private static final float SHOULDER_TILT = (float) (Math.PI / 8);
    private static final float TOE_PLATE_YAW = (float) (-Math.PI / 2);
    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> MODELS = new EnumMap<>(EquipmentSlot.class);

    private ExperimentArmorModel() {
    }

    public static void registerLayerDefinitions(RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HELMET_LAYER, ExperimentArmorModel::helmetLayer);
        event.registerLayerDefinition(CHESTPLATE_LAYER, ExperimentArmorModel::chestplateLayer);
        event.registerLayerDefinition(LEGGINGS_LAYER, ExperimentArmorModel::leggingsLayer);
        event.registerLayerDefinition(BOOTS_LAYER, ExperimentArmorModel::bootsLayer);
    }

    public static HumanoidModel<LivingEntity> model(EquipmentSlot slot) {
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) {
            throw new IllegalArgumentException("experiment armor has no model for slot " + slot);
        } else {
            return MODELS.computeIfAbsent(slot, ExperimentArmorModel::build);
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
                parts.put("right_leg", leggings.getChild("RightLeg"));
                parts.put("left_leg", leggings.getChild("LeftLeg"));
                break;
            case FEET:
                ModelPart boots = models.bakeLayer(BOOTS_LAYER);
                parts.put("right_leg", boots.getChild("RightFoot"));
                parts.put("left_leg", boots.getChild("LeftFoot"));
                break;
            default:
                throw new IllegalArgumentException("experiment armor has no model for slot " + slot);
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
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(1.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition chestplateLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "Body",
            CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(1.01F)),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        PartDefinition rightArm = root.addOrReplaceChild(
            "RightArm",
            CubeListBuilder.create().texOffs(40, 25).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(1.0F)),
            PartPose.offset(-5.0F, 2.0F, 0.0F)
        );
        rightArm.addOrReplaceChild(
            "RightArm_sleeve",
            CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(1.05F)),
            PartPose.offsetAndRotation(-1.0F, -1.0F, 0.0F, 0.0F, 0.0F, (float) (Math.PI / 8))
        );
        rightArm.addOrReplaceChild(
            "RightArm_pauldron",
            CubeListBuilder.create().texOffs(32, 8).addBox(-2.75F, 0.5F, -2.0F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.8F)),
            PartPose.offsetAndRotation(-1.0F, -1.0F, -0.5F, 0.0F, 0.0F, (float) (Math.PI / 8))
        );
        PartDefinition leftArm = root.addOrReplaceChild(
            "LeftArm",
            CubeListBuilder.create().texOffs(40, 25).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(1.0F)).mirror(false),
            PartPose.offset(5.0F, 2.0F, 0.0F)
        );
        leftArm.addOrReplaceChild(
            "LeftArm_sleeve",
            CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-2.0F, -1.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(1.05F)).mirror(false),
            PartPose.offsetAndRotation(1.0F, -1.0F, 0.0F, 0.0F, 0.0F, (float) (-Math.PI / 8))
        );
        leftArm.addOrReplaceChild(
            "LeftArm_pauldron",
            CubeListBuilder.create().texOffs(32, 8).mirror().addBox(-0.25F, 0.5F, -2.0F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.8F)).mirror(false),
            PartPose.offsetAndRotation(1.0F, -1.0F, -0.5F, 0.0F, 0.0F, (float) (-Math.PI / 8))
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition leggingsLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
            "RightLeg",
            CubeListBuilder.create().texOffs(0, 23).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)),
            PartPose.offset(-1.9F, 12.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "LeftLeg",
            CubeListBuilder.create().texOffs(0, 23).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)).mirror(false),
            PartPose.offset(1.9F, 12.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition bootsLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition rightFoot = root.addOrReplaceChild(
            "RightFoot",
            CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 9.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(1.0F)),
            PartPose.offset(-1.9F, 12.0F, 0.0F)
        );
        rightFoot.addOrReplaceChild(
            "RightFoot_plate",
            CubeListBuilder.create().texOffs(32, 8).addBox(-2.0F, -2.0F, -3.25F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.75F)),
            PartPose.offsetAndRotation(-1.25F, 9.5F, -0.55F, 0.0F, (float) (-Math.PI / 2), 0.0F)
        );
        PartDefinition leftFoot = root.addOrReplaceChild(
            "LeftFoot",
            CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 9.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(1.0F)).mirror(false),
            PartPose.offset(1.9F, 12.0F, 0.0F)
        );
        leftFoot.addOrReplaceChild(
            "LeftFoot_plate",
            CubeListBuilder.create().texOffs(32, 8).mirror().addBox(-1.0F, -2.0F, -3.25F, 3.0F, 3.0F, 5.0F, new CubeDeformation(0.75F)).mirror(false),
            PartPose.offsetAndRotation(1.25F, 9.5F, -0.55F, 0.0F, (float) (Math.PI / 2), 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }
}
