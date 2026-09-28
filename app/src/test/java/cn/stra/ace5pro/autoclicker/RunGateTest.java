package cn.stra.ace5pro.autoclicker;
import org.junit.Test;
import static org.junit.Assert.*;
public class RunGateTest {
 @Test public void cancelledStartCannotBecomeRunning() { RunGate g=new RunGate(); long old=g.begin(); long stop=g.stop(); assertFalse(g.started(old)); assertFalse(g.finish(old)); assertEquals(-1,g.begin()); assertTrue(g.stopped(stop)); assertTrue(g.begin()>old); }
 @Test public void oldCompletionCannotStopNewSession() { RunGate g=new RunGate(); long old=g.begin(); g.stopped(g.stop()); long next=g.begin(); assertFalse(g.finish(old)); assertTrue(g.started(next)); assertEquals(RunGate.State.RUNNING,g.state()); }
 @Test public void closeRejectsQueuedWork() { RunGate g=new RunGate(); long old=g.begin(); g.close(); assertFalse(g.current(old)); assertFalse(g.started(old)); assertEquals(-1,g.begin()); assertEquals(-1,g.stop()); }
 @Test public void staleStopCannotReopenControls() { RunGate g=new RunGate(); g.begin(); long first=g.stop(); long second=g.stop(); assertFalse(g.stopped(first)); assertTrue(g.stopped(second)); }
 @Test public void completionBeforeStartUiRemainsIdle() { RunGate g=new RunGate(); long token=g.begin(); assertTrue(g.finish(token)); assertFalse(g.started(token)); assertEquals(RunGate.State.IDLE,g.state()); }
}
