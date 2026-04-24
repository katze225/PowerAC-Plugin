package me.katze.powerac.scheduler;

public interface TaskHandle {

    TaskHandle NO_OP = new TaskHandle() {
        @Override
        public void cancel() {
        }
    };

    void cancel();
}
