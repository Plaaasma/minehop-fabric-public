package net.nerdorg.minehop.anticheat;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
public class ProcessChecker {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static boolean isProcessRunning(String processName) {
        Future<Boolean> future = executor.submit(new ProcessDetectionTask(processName));
        try {
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static class ProcessDetectionTask implements Callable<Boolean> {
        private final String processName;

        ProcessDetectionTask(String processName) {
            this.processName = processName;
        }

        @Override
        public Boolean call() {
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
}