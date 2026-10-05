package net.nerdorg.minehop.anticheat;

public final class AntiCheatFlag {
    public String checkName = "";
    public long timestamp;
    public double severity;
    public String details = "";
    public double posX;
    public double posY;
    public double posZ;

    public AntiCheatFlag() {
    }

    public AntiCheatFlag(String checkName, long timestamp, double severity, String details, double posX, double posY, double posZ) {
        this.checkName = checkName == null ? "" : checkName;
        this.timestamp = timestamp;
        this.severity = severity;
        this.details = details == null ? "" : details;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }
}
