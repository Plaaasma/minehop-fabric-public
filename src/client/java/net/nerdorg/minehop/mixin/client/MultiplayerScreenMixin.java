package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.nerdorg.minehop.MinehopClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public class MultiplayerScreenMixin {
    @Shadow private Button selectButton;

    @Shadow private Button editButton;

    @Shadow private Button deleteButton;

    @Shadow protected ServerSelectionList serverSelectionList;

    @Inject(method = "onSelectedChange", at = @At("HEAD"), cancellable = true)
    private void onUpdateButtonActivationStates(CallbackInfo ci) {
        this.selectButton.active = false;
        this.editButton.active = false;
        this.deleteButton.active = false;
        ServerSelectionList.Entry entry = (ServerSelectionList.Entry)this.serverSelectionList.getSelected();
        if (entry != null && !(entry instanceof ServerSelectionList.LANHeader)) {
            this.selectButton.active = true;
            if (entry instanceof ServerSelectionList.OnlineServerEntry serverEntry) {
                if (!serverEntry.getServerData().ip.equals("mh.nerd-org.com")) {
                    this.editButton.active = true;
                    this.deleteButton.active = true;
                }
            }
        }

        ci.cancel();
    }
}