package cn.stra.ace5pro.autoclicker;

/** Session identity prevents cancelled starts and stale completion callbacks. */
final class RunGate {
    enum State { IDLE, STARTING, RUNNING, STOPPING, CLOSED }
    private State state = State.IDLE;
    private long generation;
    synchronized long begin() {
        if (state != State.IDLE) return -1;
        state = State.STARTING;
        return ++generation;
    }
    synchronized boolean current(long token) {
        return generation == token && (state == State.STARTING || state == State.RUNNING);
    }
    synchronized boolean started(long token) {
        if (!current(token)) return false;
        state = State.RUNNING;
        return true;
    }
    synchronized boolean finish(long token) {
        if (!current(token)) return false;
        state = State.IDLE;
        return true;
    }
    synchronized long stop() {
        if (state == State.CLOSED) return -1;
        state = State.STOPPING;
        return ++generation;
    }
    synchronized boolean stopped(long token) {
        if (generation != token || state != State.STOPPING) return false;
        state = State.IDLE;
        return true;
    }
    synchronized void close() { ++generation; state = State.CLOSED; }
    synchronized State state() { return state; }
}
