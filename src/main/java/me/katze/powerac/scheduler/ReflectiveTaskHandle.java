package me.katze.powerac.scheduler;

import java.lang.reflect.Method;

public final class ReflectiveTaskHandle implements TaskHandle {

    private final Object task;

    public ReflectiveTaskHandle(Object task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        if (task == null) {
            return;
        }

        try {
            Method cancelMethod = task.getClass().getMethod("cancel");
            cancelMethod.invoke(task);
        } catch (Exception ignored) {
        }
    }
}
