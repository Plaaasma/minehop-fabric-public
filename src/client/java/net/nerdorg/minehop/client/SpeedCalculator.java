package net.nerdorg.minehop.client;

public class SpeedCalculator {
    public static String speedText(double speed) {
        return String.format("%.2f blocks/sec", blocksPerSecond(speed));
    }

    public static String ssjText(double speed, int jumps) {
        return String.format("%.2f", blocksPerSecond(speed)) + " (" + jumps + ")";
    }

    public static String effText(double percentage) {
        return String.format("%.2f", percentage) + "%";
    }

    private static double blocksPerSecond(double speed) {
        return speed / 0.05F;
    }
}
