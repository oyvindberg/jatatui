package jatatui.react;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UseTimeoutTest {

  /// Mount, render before delay elapses, callback hasn't fired. Sleep past delay, render
  /// again, callback fires on the render thread.
  @Test
  void fires_once_after_delay_on_render_thread() throws Exception {
    AtomicInteger fireCount = new AtomicInteger();
    AtomicReferenceLong fireThreadId = new AtomicReferenceLong();

    Element app =
        component(
            ctx -> {
              ctx.useTimeout(
                  60,
                  () -> {
                    fireCount.incrementAndGet();
                    fireThreadId.set(Thread.currentThread().getId());
                  });
              return text("");
            });

    TestHarness h = new TestHarness(20, 5);
    long renderThreadId = Thread.currentThread().getId();

    h.render(app);
    assertEquals(0, fireCount.get(), "render before delay → no fire");

    Thread.sleep(120);
    h.render(app);
    assertEquals(1, fireCount.get(), "render after delay → cb fires once");

    // Re-rendering after fire does not re-fire.
    h.render(app);
    h.render(app);
    assertEquals(1, fireCount.get(), "subsequent renders → no re-fire");

    assertEquals(renderThreadId, fireThreadId.get(), "cb runs on the render thread");
  }

  /// Unmount before delay elapses → cleanup cancels the schedule → callback never runs.
  @Test
  void unmount_before_delay_cancels_callback() throws Exception {
    AtomicBoolean fired = new AtomicBoolean();
    AtomicBoolean show = new AtomicBoolean(true);

    Element child =
        component(
            ctx -> {
              ctx.useTimeout(80, () -> fired.set(true));
              return text("inside");
            });

    Element wrapper = component(ctx -> show.get() ? child : text("(unmounted)"));

    TestHarness h = new TestHarness(20, 5);
    h.render(wrapper);
    assertFalse(fired.get());

    // Unmount: hook sweep runs cleanup which cancels the scheduled future.
    show.set(false);
    h.render(wrapper);

    // Wait well past the original delay; even if the scheduler still fires its requestRerender
    // (cancel might race), the callback only runs when useTimeout is invoked during the
    // unmounted component's render — which doesn't happen anymore.
    Thread.sleep(150);
    h.render(wrapper);
    assertFalse(fired.get(), "callback must not run after unmount");
  }

  /// Deps change cancels the previous timer and re-arms with a fresh delay.
  @Test
  void deps_change_resets_the_timer() throws Exception {
    AtomicInteger fireCount = new AtomicInteger();
    AtomicInteger deps = new AtomicInteger(1);

    Element app =
        component(
            ctx -> {
              ctx.useTimeout(80, fireCount::incrementAndGet, deps.get());
              return text("");
            });

    TestHarness h = new TestHarness(20, 5);
    h.render(app);

    // Wait nearly the whole delay, then bump deps. The first timer should be cancelled.
    Thread.sleep(50);
    deps.set(2);
    h.render(app);

    // Wait the remaining 30ms of the original delay — should NOT fire because the new timer
    // restarted from this point.
    Thread.sleep(40);
    h.render(app);
    assertEquals(0, fireCount.get(), "first timer was cancelled when deps changed");

    // Now wait past the second timer's full 80ms (we already slept 40 of the new period).
    Thread.sleep(60);
    h.render(app);
    assertEquals(1, fireCount.get(), "second timer fired");
  }

  /// Convenience: java.util.concurrent.atomic.AtomicLong with a `getId` semantic.
  static final class AtomicReferenceLong {
    private final java.util.concurrent.atomic.AtomicLong v =
        new java.util.concurrent.atomic.AtomicLong(-1);

    long get() {
      return v.get();
    }

    void set(long val) {
      v.set(val);
    }
  }
}
