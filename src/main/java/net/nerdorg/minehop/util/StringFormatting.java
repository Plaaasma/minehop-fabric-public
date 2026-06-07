package net.nerdorg.minehop.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringFormatting {
    public static String limitDecimals(String string) {
        Pattern pattern = Pattern.compile("\\d+\\.\\d+");
        Matcher matcher = pattern.matcher(string);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            double floatValue = Double.parseDouble(matcher.group());
            matcher.appendReplacement(result, String.format("%.5f", floatValue));
        }
        matcher.appendTail(result);

        string = result.toString();

        return string;
    }
}
