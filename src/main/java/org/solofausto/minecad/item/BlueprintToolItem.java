package org.solofausto.minecad.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.solofausto.minecad.blueprint.BlueprintItemData;
import org.solofausto.minecad.blueprint.BlueprintManager;
import org.solofausto.minecad.blueprint.BlueprintOrigin;
import org.solofausto.minecad.blueprint.BlueprintSession;

public class BlueprintToolItem extends Item {
    public BlueprintToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient()) {
            PlayerEntity player = context.getPlayer();
            if (player == null) {
                return ActionResult.PASS;
            }

            BlockPos pos = context.getBlockPos();
            Direction face = context.getSide();

            BlueprintSession session = BlueprintManager.getInstance().getOrCreate(player.getUuid());
            BlueprintItemData.ensureBlueprintId(context.getStack(), session);
            if (BlueprintItemData.loadFromItem(context.getStack(), session)) {
                return ActionResult.SUCCESS;
            }
            session.start(new BlueprintOrigin(pos, face));
            BlueprintItemData.writeToItem(context.getStack(), session);
            return ActionResult.SUCCESS;
        }

        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }

        BlockPos pos = context.getBlockPos();
        Direction face = context.getSide();

        BlueprintSession session = BlueprintManager.getInstance().getOrCreate(player.getUuid());
        BlueprintItemData.ensureBlueprintId(context.getStack(), session);
        if (BlueprintItemData.loadFromItem(context.getStack(), session)) {
            return ActionResult.CONSUME;
        }
        session.start(new BlueprintOrigin(pos, face));
        BlueprintItemData.writeToItem(context.getStack(), session);

        player.sendMessage(Text.literal("Blueprint started at " + pos + " facing " + face), true);
        return ActionResult.CONSUME;
    }
}
