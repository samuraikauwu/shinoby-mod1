package com.shinobi;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Простейший планировщик отложенных действий (в тиках сервера). */
public final class Tasks {
    private record Task(long runAt, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private Tasks() {
    }

    public static void later(MinecraftServer server, int delayTicks, Runnable action) {
        TASKS.add(new Task(server.getTicks() + delayTicks, action));
    }

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        long now = server.getTicks();
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.runAt <= now) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.action.run();
            } catch (Exception ignored) {
            }
        }
    }
}
