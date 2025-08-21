package com.agent.cli.ui;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;
import java.io.PrintWriter;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * A UI component that displays a spinning cursor in the console
 * while a long-running background task is being executed.
 */
@Component
public class Spinner {

    private final Terminal terminal;
    private final char[] spinnerChars = new char[]{'|', '/', '-', '\\'};

    public Spinner(Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * Executes a long-running task and displays a spinner while it's running.
     * This version uses a timed Future.get() to avoid a "busy-wait" sleep loop,
     * resolving the common IDE code quality warning.
     *
     * @param task The task to execute, which must return a result (e.g., via a lambda).
     * @param <T> The type of the result.
     * @return The result of the task.
     * @throws RuntimeException if the task throws an exception.
     */
    public <T> T spin(Supplier<T> task) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<T> future = executor.submit(task::get);
        PrintWriter writer = terminal.writer();
        int spinnerIndex = 0;

        try {
            while (true) {
                try {
                    // Wait for the future to complete, with a short timeout.
                    // This is a non-blocking wait that avoids the busy-wait loop.
                    T result = future.get(100, TimeUnit.MILLISECONDS);

                    // If we get here, the task is done. Clean up and return.
                    clearSpinnerLine(writer);
                    return result;

                } catch (TimeoutException e) {
                    // The future is not yet complete, so we print the next spinner frame.
                    writer.print("\r\u001B[33mProcessing... " + spinnerChars[spinnerIndex++ % spinnerChars.length] + "\u001B[0m");
                    writer.flush();
                }
            }
        } catch (Exception e) {
            clearSpinnerLine(writer);
            // Unwrap the ExecutionException to propagate the original cause.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private void clearSpinnerLine(PrintWriter writer) {
        // Clear the spinner line by printing spaces over it and returning the cursor.
        writer.print("\r" + " ".repeat(20) + "\r");
        writer.flush();
    }
}