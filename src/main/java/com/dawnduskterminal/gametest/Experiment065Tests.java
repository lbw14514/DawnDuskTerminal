package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.world.CubeField;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 065 的规格：占位号让出、物品是那个方块的 {@link BlockItem}、方块没有状态且常亮 15 级光、
 * 场是 17×17×17、场里打出的伤害打不进去（近战与射弹都算）、场外打进来的照常、环境伤害不拦、
 * 挖掉就停；资源那一套（两张贴图、双层模型、方块状态、物品模型、战利品表）也在这里钉住
 *
 * <p>判定「这一下到底有没有打进去」用的是 {@code LivingEntity#hurt} 的返回值：取消掉伤害事件以后
 * 原版那一段会直接返回 {@code false}，同时血量、击退、受击音都不会发生，所以断言返回值与血量两样就够。
 * 每次试打都用刚出生的猪，不会撞上受击后的无敌帧
 *
 * <p>场是 17 格：中心那一格往每个方向各 8 格。三条轴各试两格——正好 8 格打不动、9 格打得动，
 * 结构是 16×16×16 的空房间，中心摆在 (4,3,4)，两个位置都还落在里面
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment065Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_065";

    /** 猪的满血：{@code EntityType.PIG} 的 {@code max_health} 是 10 */
    private static final float PIG_HEALTH = 10.0F;

    /** 每次试打的伤害：4 点，打进去以后猪从 10 掉到 6 */
    private static final float STRIKE_DAMAGE = 4.0F;

    /** 场心那一格 */
    private static final BlockPos CENTRE = new BlockPos(4, 3, 4);

    /** 受害人站的格：场心里那一格的正上方，谁也没占着 */
    private static final BlockPos VICTIM = CENTRE.above();

    @GameTest(template = "ddt_empty")
    public static void blockReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(ModExperimentItems.placeholders().containsKey(65),
            "number 65 must not be a placeholder anymore, it has a block now");
        Item item = ModItems.EXPERIMENT_065.get();
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == ModBlocks.EXPERIMENT_065.get(),
            SUBJECT + " must be the item form of its own block, got " + item);
        // 方块物品的名字取自方块，所以语言文件的键是 block.dawnduskterminal.experiment_065
        helper.assertTrue(item.getDescriptionId().equals("block.dawnduskterminal.experiment_065"),
            "a block item must take its name from the block, got " + item.getDescriptionId());

        BlockState state = ModBlocks.EXPERIMENT_065.get().defaultBlockState();
        helper.assertTrue(state.getProperties().isEmpty(),
            SUBJECT + " must carry no state, it works as soon as it is placed, got " + state.getProperties());
        helper.assertTrue(state.getLightEmission(helper.getLevel(), helper.absolutePos(CENTRE)) == 15,
            SUBJECT + " must emit 15 light levels, it is its own light source and carries no glow layer, got "
                + state.getLightEmission(helper.getLevel(), helper.absolutePos(CENTRE)));
        helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE),
            SUBJECT + " must stay in #minecraft:mineable/pickaxe, otherwise a pickaxe cannot drop it");
        helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(DawnDuskTerminal.id("experiment_065")),
            "dawnduskterminal:experiment_065 must be registered as a block entity type, the field needs one");
        // 场的大小就写在这里：17×17×17，中心那一格往每个方向各 8 格
        helper.assertTrue(CubeField.SIZE == 17 && CubeField.RADIUS == 8,
            "the field must stay 17x17x17, got " + CubeField.SIZE
                + " (radius " + CubeField.RADIUS + ")");
        helper.succeed();
    }

    /**
     * 场里打出的伤害无效：打场里的、打场外的都打不动
     *
     * <p>判定看的是「打人的那个实体在不在场里」，与挨打的人站在哪无关，所以第二条把受害人摆到场外，
     * 结果必须一样
     */
    @GameTest(template = "ddt_empty")
    public static void attacksFromInsideDoNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeField(level, helper.absolutePos(CENTRE));

        Pig attacker = spawn(helper, CENTRE.offset(2, 0, 0));
        Pig victimInside = spawn(helper, CENTRE.west(2));
        helper.assertFalse(victimInside.hurt(level.damageSources().mobAttack(attacker), STRIKE_DAMAGE),
            "an attack made from inside the field must not land, on a victim inside it");
        helper.assertTrue(victimInside.getHealth() == PIG_HEALTH,
            "a cancelled hit must leave the health alone, the pig has " + victimInside.getHealth());

        Pig victimOutside = spawn(helper, CENTRE.east(11));
        helper.assertFalse(victimOutside.hurt(level.damageSources().mobAttack(attacker), STRIKE_DAMAGE),
            "an attack made from inside the field must not land, on a victim outside it either");
        helper.assertTrue(victimOutside.getHealth() == PIG_HEALTH,
            "a cancelled hit must leave the health alone, the pig has " + victimOutside.getHealth());
        helper.succeed();
    }

    /** 场外打进来的照常：攻击者站在场外，受害人在场里，这一下要真打进去 */
    @GameTest(template = "ddt_empty")
    public static void attacksFromOutsideStillLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeField(level, helper.absolutePos(CENTRE));

        Pig attacker = spawn(helper, CENTRE.east(9));
        Pig victim = spawn(helper, VICTIM);
        helper.assertTrue(victim.hurt(level.damageSources().mobAttack(attacker), STRIKE_DAMAGE),
            "an attack made from outside the field must land, the field only nullifies what is made inside it");
        helper.assertTrue(Math.abs(victim.getHealth() - (PIG_HEALTH - STRIKE_DAMAGE)) < 1.0E-4F,
            "the pig must be down to " + (PIG_HEALTH - STRIKE_DAMAGE) + ", it has " + victim.getHealth());
        helper.succeed();
    }

    /** 场正好 17 格：三条轴各在 8 格处打不动、9 格处打得动 */
    @GameTest(template = "ddt_empty")
    public static void fieldIsExactlySeventeenBlocksWide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeField(level, helper.absolutePos(CENTRE));

        int[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        String[] names = {"x", "y", "z"};
        for (int axis = 0; axis < axes.length; axis++) {
            int dx = axes[axis][0];
            int dy = axes[axis][1];
            int dz = axes[axis][2];
            BlockPos edge = CENTRE.offset(dx * CubeField.RADIUS, dy * CubeField.RADIUS,
                dz * CubeField.RADIUS);
            BlockPos beyond = CENTRE.offset(dx * (CubeField.RADIUS + 1),
                dy * (CubeField.RADIUS + 1), dz * (CubeField.RADIUS + 1));

            Pig victimOnEdge = spawn(helper, VICTIM);
            helper.assertFalse(
                victimOnEdge.hurt(level.damageSources().mobAttack(spawn(helper, edge)), STRIKE_DAMAGE),
                "the field is " + CubeField.SIZE + " blocks wide, " + names[axis]
                    + " = " + CubeField.RADIUS + " must still be inside it");

            Pig victimBeyond = spawn(helper, VICTIM);
            helper.assertTrue(
                victimBeyond.hurt(level.damageSources().mobAttack(spawn(helper, beyond)), STRIKE_DAMAGE),
                "the field is " + CubeField.SIZE + " blocks wide, " + names[axis]
                    + " = " + (CubeField.RADIUS + 1) + " must already be outside it");
        }
        helper.succeed();
    }

    /**
     * 环境伤害不拦：仙人掌这类伤害的来源上没有实体，场不做判定
     *
     * <p>挨打的猪就站在场里，这一下照样打进去
     */
    @GameTest(template = "ddt_empty")
    public static void environmentDamagePassesThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeField(level, helper.absolutePos(CENTRE));

        Pig victim = spawn(helper, VICTIM);
        helper.assertTrue(victim.hurt(level.damageSources().cactus(), STRIKE_DAMAGE),
            "damage without a source entity must not be stopped by the field");
        helper.assertTrue(Math.abs(victim.getHealth() - (PIG_HEALTH - STRIKE_DAMAGE)) < 1.0E-4F,
            "the pig must be down to " + (PIG_HEALTH - STRIKE_DAMAGE) + ", it has " + victim.getHealth());
        helper.succeed();
    }

    /**
     * 射弹按弹体自己所在的位置算：场里射出去的箭飞到场外照常伤人，场外射进来的箭飞到人身上时已经在场里、
     * 反而打不动——这条是「攻击源自范围内」这句话的直接后果，射手站在哪不参与判定
     */
    @GameTest(template = "ddt_empty")
    public static void projectilesAreJudgedWhereTheyAre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeField(level, helper.absolutePos(CENTRE));

        Arrow inside = helper.spawn(EntityType.ARROW, CENTRE.offset(2, 0, 0));
        Pig shooterInside = spawn(helper, CENTRE.west(1));
        Pig victimOutside = spawn(helper, CENTRE.east(11));
        helper.assertFalse(victimOutside.hurt(level.damageSources().arrow(inside, shooterInside), STRIKE_DAMAGE),
            "an arrow sitting inside the field must not deal damage, wherever it is aimed");

        Arrow outside = helper.spawn(EntityType.ARROW, CENTRE.east(11));
        Pig shooterAlsoInside = spawn(helper, CENTRE.west(2));
        Pig victimAlsoOutside = spawn(helper, CENTRE.east(10));
        helper.assertTrue(victimAlsoOutside.hurt(level.damageSources().arrow(outside, shooterAlsoInside), STRIKE_DAMAGE),
            "an arrow outside the field must land even when the shooter stands inside it, "
                + "the judgement is the projectile's own position");
        helper.succeed();
    }

    /**
     * 摆下就生效、挖掉就停：登记与撤登记都跟着方块自己走，不等下一 tick 的方块实体装载
     */
    @GameTest(template = "ddt_empty")
    public static void breakingTheBlockTurnsTheFieldOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(CENTRE);
        placeField(level, centre);

        Pig victimOnField = spawn(helper, VICTIM);
        helper.assertFalse(victimOnField.hurt(level.damageSources().mobAttack(spawn(helper, CENTRE.offset(3, 0, 0))),
                STRIKE_DAMAGE),
            "the field must already work in the same tick the block was placed");

        level.setBlockAndUpdate(centre, Blocks.AIR.defaultBlockState());
        Pig victimAfterBreak = spawn(helper, VICTIM);
        helper.assertTrue(victimAfterBreak.hurt(
                level.damageSources().mobAttack(spawn(helper, CENTRE.offset(3, 0, 0))), STRIKE_DAMAGE),
            "breaking the block must turn the field off");
        helper.succeed();
    }

    /**
     * 资源那一套：方块状态一个变体、单层 {@code cube_all} 模型（方块自己发光，不再叠发光层）、
     * 物品模型指向方块模型、战利品表掉自己，本体贴图在 jar 里
     *
     * <p>assets 下的模型服务端不加载，构建也不会因为路径写错而失败，所以这里直接读 classpath 上的文件本身
     */
    @GameTest(template = "ddt_empty")
    public static void assetsMatchTheBlock(GameTestHelper helper) {
        JsonObject states = readJson(helper, "/assets/dawnduskterminal/blockstates/experiment_065.json");
        JsonObject variants = states.getAsJsonObject("variants");
        helper.assertTrue(variants.size() == 1,
            SUBJECT + " has no state, its blockstates file must carry exactly one variant, got " + variants.size());
        helper.assertTrue(variants.getAsJsonObject("").get("model").getAsString()
                .equals("dawnduskterminal:block/experiment_065"),
            "the only variant must point at block/experiment_065, got " + variants.getAsJsonObject(""));

        JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/block/experiment_065.json");
        helper.assertTrue(model.get("parent").getAsString().equals("minecraft:block/cube_all"),
            "block/experiment_065 must stay a plain cube_all model, got " + model.get("parent"));
        JsonObject textures = model.getAsJsonObject("textures");
        helper.assertTrue(textures.entrySet().size() == 1
                && textures.get("all").getAsString().equals("dawnduskterminal:block/experiment_065"),
            "the block is a light source, one texture is enough, got " + textures);
        helper.assertFalse(model.has("elements"),
            "one texture needs no hand written elements, the cube comes from cube_all, got " + model.get("elements"));

        JsonObject item = readJson(helper, "/assets/dawnduskterminal/models/item/experiment_065.json");
        helper.assertTrue(item.get("parent").getAsString().equals("dawnduskterminal:block/experiment_065"),
            "the item model must point at the block model, got " + item.get("parent"));

        JsonObject loot = readJson(helper, "/data/dawnduskterminal/loot_table/blocks/experiment_065.json");
        helper.assertTrue(loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                .get(0).getAsJsonObject().get("name").getAsString().equals(SUBJECT),
            "the block must drop itself, got " + loot.getAsJsonArray("pools").get(0));

        for (String texture : new String[] {"experiment_065"}) {
            String path = "/assets/dawnduskterminal/textures/block/" + texture + ".png";
            try (InputStream stream = Experiment065Tests.class.getResourceAsStream(path)) {
                helper.assertTrue(stream != null, path + " must ship inside the mod resources");
            } catch (IOException e) {
                helper.assertTrue(false, "failed to read " + path + ": " + e);
            }
        }

        // 场的范围由立方框粒子画：服务端一颗都不发，所以服务端那颗 ticker 必须是空的
        BlockState state = ModBlocks.EXPERIMENT_065.get().defaultBlockState();
        helper.assertTrue(ModBlocks.EXPERIMENT_065.get().getTicker(helper.getLevel(), state,
                ModBlockEntities.EXPERIMENT_065.get()) == null,
            "the field frame is drawn on the client only, the server must get no ticker at all");
        helper.succeed();
    }

    /** 摆一台 065，场心是 {@code pos} */
    private static void placeField(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_065.get().defaultBlockState());
    }

    private static Pig spawn(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityType.PIG, pos);
    }

    /** 读一个资源（路径以 / 开头），失败时直接把断言打红 */
    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment065Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (IOException e) {
            helper.assertTrue(false, "failed to read " + resource + ": " + e);
            return new JsonObject();
        }
    }

    private Experiment065Tests() {}
}
