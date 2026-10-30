package com.dawnduskterminal.item;

import com.dawnduskterminal.block.SusCakeBlock;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 试验物品 107：整块的可疑的红丝绒蛋糕
 *
 * <p>就是那一块方块的方块物品，与七种可疑蛋糕（{@code sus_<口味>_cake}，一件＝一片）的方块、状态、补片、
 * 取片、空手吃片全部共用，两处不同：
 *
 * <ol>
 *   <li><b>摆下来就是满的 8 片</b>：{@code omnomnom=0}，而一片那件摆下来是只剩 1 片的 7
 *   <li><b>不带食物组件</b>：方块物品只负责摆放，不能拿在手里咬
 * </ol>
 *
 * <p>显示名是「试验物品107」，而方块仍是 {@code sus_redvelvet_cake}：{@link net.minecraft.world.item.BlockItem}
 * 的默认名字取自方块，不换描述 ID 就会和那件一片的重名
 *
 * <p>还有一处必须自己动手：{@link #registerBlocks}。方块与物品是「一块方块两件物品」，而
 * {@code BlockItem#registerBlocks} 写的是 {@code map.put(block, item)}，后注册的会顶掉先注册的——
 * 整块那件晚于七种一片注册，不改就会把「这一块方块对外的物品」抢成整块那件，于是方块里的
 * {@code asItem()} 在补片、取片、比较器那几处全指向整块，一片那件再也补不回去。这里把它改成不登记，
 * 方块对外的物品始终是 {@code sus_redvelvet_cake} 那一片那件
 */
public class Experiment107Item extends SusCakeItem {
    /** 名字的键：{@code item.dawnduskterminal.experiment_107}，不走方块那一条键 */
    private static final String DESCRIPTION_ID = "item.dawnduskterminal.experiment_107";

    public Experiment107Item(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public String getDescriptionId() {
        return DESCRIPTION_ID;
    }

    /** 摆下来是满的 8 片，不是一片那件的只剩 1 片 */
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : state.setValue(SusCakeBlock.BITES_TAKEN, 0);
    }

    /**
     * 不把这一块方块登记成「整块那件的方块」：{@code Item.BY_BLOCK} 走的是 {@code put}，
     * 后注册的会顶掉先注册的，登记了就会把一片那件挤掉
     */
    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        // 有意留空，见类注释
    }
}
