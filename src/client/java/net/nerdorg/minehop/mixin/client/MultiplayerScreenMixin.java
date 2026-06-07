package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.render.GameRenderer;
import net.nerdorg.minehop.MinehopClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
    @Shadow private ButtonWidget buttonJoin;

    @Shadow private ButtonWidget buttonEdit;

    @Shadow private ButtonWidget buttonDelete;

    @Shadow protected MultiplayerServerListWidget serverListWidget;

    @Inject(method = "updateButtonActivationStates", at = @At("HEAD"), cancellable = true)
    private void onUpdateButtonActivationStates(CallbackInfo ci) {
        this.buttonJoin.active = false;
        this.buttonEdit.active = false;
        this.buttonDelete.active = false;
        MultiplayerServerListWidget.Entry entry = (MultiplayerServerListWidget.Entry)this.serverListWidget.getSelectedOrNull();
        if (entry != null && !(entry instanceof MultiplayerServerListWidget.ScanningEntry)) {
            this.buttonJoin.active = true;
            if (entry instanceof MultiplayerServerListWidget.ServerEntry serverEntry) {
                if (!serverEntry.getServer().address.equals("mh.nerd-org.com")) {
                    this.buttonEdit.active = true;
                    this.buttonDelete.active = true;
                }
            }
        }

        ci.cancel();
    }
}