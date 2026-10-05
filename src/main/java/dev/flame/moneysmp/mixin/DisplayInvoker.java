package dev.flame.moneysmp.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// vanilla only sets these from NBT, so the setters are private
@Mixin(Display.class)
public interface DisplayInvoker {
    @Invoker("setTransformation")
    void invokeSetTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void invokeSetTransformationInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void invokeSetTransformationInterpolationDelay(int ticks);

    @Invoker("setBillboardConstraints")
    void invokeSetBillboardConstraints(Display.BillboardConstraints billboard);
}
