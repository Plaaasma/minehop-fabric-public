package net.nerdorg.minehop.anticheat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Timer;
import java.util.TimerTask;

public class CancelableTimer {
    private ServerPlayerEntity player;
    private Timer timer;
    private TimerTask timerTask;
    private boolean isRunning;

    public CancelableTimer(ServerPlayerEntity player) {
        timer = new Timer();
        isRunning = false;
        this.player = player;
    }

    public void start(long delay, long period) {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                // Code to be executed periodically
                player.networkHandler.disconnect(Text.of("Failed Anti-Cheat Check"));
                timer.cancel();
            }
        };

        timer.scheduleAtFixedRate(timerTask, delay, period);
        isRunning = true;
    }

    public void cancel() {
        if (isRunning) {
            timerTask.cancel();
            timer.cancel();
            isRunning = false;
        }
    }
}
