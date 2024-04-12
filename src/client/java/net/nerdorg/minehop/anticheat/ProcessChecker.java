package net.nerdorg.minehop.anticheat;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
public class ProcessChecker {
    public static byte isProcessRunningByte(String processName) {
        if (isProcessRunning(processName)) return 1;
        return 0;
    }

    public static boolean isProcessRunning(String processName) {
        ProcessBuilder processBuilder = new ProcessBuilder("tasklist.exe", "/FI", "IMAGENAME eq " + processName);
        try {
            Process process = processBuilder.start();
            byte[] output = process.getInputStream().readAllBytes();
            return new String(output).contains(processName);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}