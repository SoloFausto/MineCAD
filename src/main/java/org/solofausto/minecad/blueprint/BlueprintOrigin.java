package org.solofausto.minecad.blueprint;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record BlueprintOrigin(BlockPos blockPos, Direction face) {
}
