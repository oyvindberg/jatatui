package jatatui.react;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/// When the Element type at a fiber position changes between frames, the old subtree is
/// unmounted (state dropped, cleanups run) before the new one renders. This is what React
/// calls "reconciliation" — the rule that prevents the stack-router bug where SourceEditor
/// and OutputEditor share a fiber slot and one's useState supplier never re-runs because the
/// other's state still occupies the slot.
class ReconciliationTest {

  /// The canonical bug repro: two screens with different state types at the same fiber slot.
  /// Without reconciliation, screen B's useState returns screen A's stored value (CCE in
  /// strongly-typed code). With reconciliation, screen B's initializer runs fresh.
  @Test
  void state_resets_when_component_changes_at_same_fiber() throws IOException {
    TestHarness h = new TestHarness(40, 12);
    AtomicReference<Object> seen = new AtomicReference<>();

    Element screenA =
        component(
            ctx -> {
              State<Integer> s = ctx.useState(() -> 42);
              seen.set(s.get());
              return text("A");
            });
    Element screenB =
        component(
            ctx -> {
              State<String> s = ctx.useState(() -> "hello");
              seen.set(s.get());
              return text("B");
            });

    AtomicBoolean showA = new AtomicBoolean(true);
    Element wrapper = component(ctx -> showA.get() ? screenA : screenB);

    h.render(wrapper);
    assertEquals(42, seen.get());

    h.render(wrapper); // stable
    assertEquals(42, seen.get());

    showA.set(false);
    h.render(wrapper);
    assertEquals("hello", seen.get(), "different component at same fiber → fresh useState initial");

    showA.set(true);
    h.render(wrapper);
    assertEquals(42, seen.get(), "swap back → A's state freshly initialized again");
  }

  /// Cleanups for the unmounted subtree run BEFORE the new component renders.
  @Test
  void unmount_runs_cleanups_for_old_subtree() throws IOException {
    TestHarness h = new TestHarness(40, 12);
    AtomicInteger cleanupCount = new AtomicInteger();

    Element withCleanup =
        component(
            ctx -> {
              ctx.useState(() -> 1);
              // Hand-place a cleanup keyed on the same fiber + an unused hook slot so unmount
              // exercises its cleanup-execution branch without depending on useEffect timing
              // semantics.
              h.hooks.cleanups.put(new HookKey(ctx.fiber, 99), cleanupCount::incrementAndGet);
              return text("with-cleanup");
            });
    Element other = component(ctx -> text("other"));

    AtomicBoolean showCleanup = new AtomicBoolean(true);
    Element wrapper = component(ctx -> showCleanup.get() ? withCleanup : other);

    h.render(wrapper);
    h.render(wrapper);
    assertEquals(0, cleanupCount.get(), "no swap yet, no unmount");

    showCleanup.set(false);
    h.render(wrapper);
    assertEquals(1, cleanupCount.get(), "swap → unmount → cleanup runs");
  }

  /// Same component at same fiber across renders → state is reused (no spurious unmount).
  @Test
  void state_persists_when_same_component_re_renders() throws IOException {
    TestHarness h = new TestHarness(40, 12);
    AtomicReference<State<Integer>> stateRef = new AtomicReference<>();

    Element app =
        component(
            ctx -> {
              State<Integer> s = ctx.useState(() -> 0);
              stateRef.set(s);
              return text("count = " + s.get());
            });

    h.render(app);
    stateRef.get().set(7);
    h.render(app);
    assertEquals(7, stateRef.get().get(), "same component, state reused");

    h.render(app);
    h.render(app);
    assertEquals(7, stateRef.get().get(), "still 7 after more renders");
  }

  /// `apply(STABLE_COMPONENT, props)` form — different stable Component instances at the same
  /// fiber slot trigger reconciliation just like FUNCTION lambdas do.
  @Test
  void state_resets_for_apply_with_distinct_components() throws IOException {
    TestHarness h = new TestHarness(40, 12);
    AtomicReference<Object> seen = new AtomicReference<>();

    Component<Integer> compA =
        (props, ctx) ->
            text(
                "A:"
                    + (Integer)
                        ctx.useState(
                                () -> {
                                  seen.set("A-init");
                                  return props * 10;
                                })
                            .get());
    Component<Integer> compB =
        (props, ctx) ->
            text(
                "B:"
                    + (Integer)
                        ctx.useState(
                                () -> {
                                  seen.set("B-init");
                                  return props + 100;
                                })
                            .get());

    AtomicBoolean useA = new AtomicBoolean(true);
    Element wrapper = component(ctx -> useA.get() ? apply(compA, 5) : apply(compB, 7));

    h.render(wrapper);
    assertEquals("A-init", seen.get());

    h.render(wrapper);
    // re-render of A: initializer NOT called again
    seen.set("(unchanged)");
    h.render(wrapper);
    assertEquals("(unchanged)", seen.get(), "same Component, state reused");

    useA.set(false);
    h.render(wrapper);
    assertEquals("B-init", seen.get(), "different Component, fresh state");
  }
}
