package dev.flame.moneysmp.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayInvoker {
    @Invoker("setText")
    void invokeSetText(Component text);

    @Invoker("setBackgroundColor")
    void invokeSetBackgroundColor(int argb);

    @Invoker("setFlags")
    void invokeSetFlags(byte flags);
}
