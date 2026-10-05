package net.nerdorg.minehop.anticheat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

public class ProcessChecker {
    private static final int CHUNK_SIZE = 1024 * 64; // 64KB
    private static final int MIN_STRING_LENGTH = 4;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    public static String scanProcessesForKeywords(List<String> keywords) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Callable<String>> tasks = new ArrayList<>();

        for (ProcessHandle ph : ProcessHandle.allProcesses().toList()) {
            tasks.add(() -> {
                try {
                    String exePath = getProcessExecutable(ph);
                    if (exePath != null && Files.isRegularFile(Paths.get(exePath))) {
                        String matchedKeyword = getMatchingKeyword(Paths.get(exePath), keywords, MIN_STRING_LENGTH);
                        if (matchedKeyword != null) {
                            String fileName = Paths.get(exePath).getFileName().toString();
                            return matchedKeyword + "~" + fileName;
                        }
                    }
                } catch (Exception ignored) {}
                return null;
            });
        }

        try {
            List<Future<String>> results = executor.invokeAll(tasks);
            for (Future<String> result : results) {
                String match = result.get();
                if (match != null) {
                    executor.shutdownNow();
                    return match;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        return null;
    }

    private static String getProcessExecutable(ProcessHandle ph) {
        try {
            Optional<String> commandOpt = ph.info().command();
            if (commandOpt.isPresent()) {
                return commandOpt.get();
            } else {
                Path exeLink = Paths.get("/proc", String.valueOf(ph.pid()), "exe");
                if (Files.isSymbolicLink(exeLink)) {
                    return Files.readSymbolicLink(exeLink).toString();
                } else if (Files.exists(exeLink)) {
                    return exeLink.toRealPath().toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String getMatchingKeyword(Path executable, List<String> keywords, int minLength) {
        try (InputStream inputStream = Files.newInputStream(executable)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            StringBuilder sb = new StringBuilder();
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    char c = (char) buffer[i];
                    if (c >= 32 && c <= 126) {
                        sb.append(c);
                    } else {
                        if (sb.length() >= minLength) {
                            String str = sb.toString().toLowerCase();
                            for (String keyword : keywords) {
                                if (str.contains(keyword.toLowerCase())) {
                                    return keyword;
                                }
                            }
                        }
                        sb.setLength(0);
                    }
                }
            }

            if (sb.length() >= minLength) {
                String str = sb.toString().toLowerCase();
                for (String keyword : keywords) {
                    if (str.contains(keyword.toLowerCase())) {
                        return keyword;
                    }
                }
            }
        } catch (IOException ignored) {}

        return null;
    }
}