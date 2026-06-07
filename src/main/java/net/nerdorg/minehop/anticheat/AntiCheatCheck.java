package net.nerdorg.minehop.anticheat;

public abstract class AntiCheatCheck {
    private final String name;
    private final double cancelThreshold;
    private final double lagbackThreshold;
    private final double decayPerTick;

    protected AntiCheatCheck(String name, double cancelThreshold, double lagbackThreshold, double decayPerTick) {
        this.name = name;
        this.cancelThreshold = cancelThreshold;
        this.lagbackThreshold = lagbackThreshold;
        this.decayPerTick = decayPerTick;
    }

    public final String name() {
        return this.name;
    }

    public final double cancelThreshold() {
        return this.cancelThreshold;
    }

    public final double lagbackThreshold() {
        return this.lagbackThreshold;
    }

    public final double decayPerTick() {
        return this.decayPerTick;
    }

    public abstract CheckResult run(AntiCheatContext context);

    public static final class CheckResult {
        public static final CheckResult OK = new CheckResult(0.0D, "", false);

        public final double violationIncrement;
        public final String details;
        public final boolean forceLagback;

        public CheckResult(double violationIncrement, String details, boolean forceLagback) {
            this.violationIncrement = violationIncrement;
            this.details = details == null ? "" : details;
            this.forceLagback = forceLagback;
        }

        public static CheckResult flag(double increment, String details) {
            return new CheckResult(increment, details, false);
        }

        public static CheckResult flagAndLagback(double increment, String details) {
            return new CheckResult(increment, details, true);
        }
    }
}
