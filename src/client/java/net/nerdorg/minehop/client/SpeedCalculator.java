package net.nerdorg.minehop.client;

import oshi.util.tuples.Pair;

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

    public static Pair<String, Integer> gaugeText(double gauge, boolean horizontal) {
        if (horizontal) {
            int right_amount = 0;
            int left_amount = 0;

            if (gauge < 0) {
                for (int i = 0; i > gauge && left_amount < 4; i -= 10) {
                    left_amount += 1;
                }
            } else if (gauge > 0) {
                for (int i = 0; i < gauge && right_amount < 4; i += 10) {
                    right_amount += 1;
                }
            }

            String gaugeString =
                    "<" + "-".repeat(Math.max(0, left_amount)) + "+" + "-".repeat(Math.max(0, right_amount)) + ">";

            int offsetToO = Math.max(0, left_amount);
            if (offsetToO == 0) {
                offsetToO = -Math.max(0, right_amount);
            }

            return new Pair<>(gaugeString, offsetToO);
        }
        else {
            int up_amount = 0;
            int down_amount = 0;

            if (gauge < 0) {
                for (int i = 0; i > gauge && down_amount < 4; i -= 10) {
                    down_amount += 1;
                }
            } else if (gauge > 0) {
                for (int i = 0; i < gauge && up_amount < 4; i += 10) {
                    up_amount += 1;
                }
            }

            String gaugeString =
                    "/\\" +
                            "\n|  |".repeat(Math.max(0, up_amount)) +
                            "\n++" +
                            "\n|  |".repeat(Math.max(0, down_amount)) +
                            "\n\\/";

            // Offset is the number of lines before the 'O'
            int offsetToO = 1 + Math.max(0, up_amount);  // 1 for '/\' plus the number of up bars

            return new Pair<>(gaugeString, offsetToO);
        }
    }

    private static double blocksPerSecond(double speed) {
        return speed / 0.05F;
    }
}
