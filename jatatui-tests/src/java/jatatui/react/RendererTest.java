package jatatui.react;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.backend.TestBackend;
import jatatui.core.terminal.Terminal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyModifiers;

/// Validates the embedding API: a host-owned loop drives [Renderer] directly, with no
/// [TestHarness] or [ReactApp] in the picture. Mirrors what jatatui-react consumers (e.g. typr,
/// game loops, multi-pane TUIs) need to do.
class RendererTest {

  /// Standalone happy-path: starts dirty, renders, becomes clean. After a state mutation, dirty
  /// flips back. takeDirty is the loop idiom.
  @Test
  void render_dirty_lifecycle() throws IOException {
    Renderer r = new Renderer();
    AtomicInteger renderCount = new AtomicInteger();
    Element app =
        component(
            ctx -> {
              renderCount.incrementAndGet();
              return text("hello");
            });

    assertTrue(r.isDirty(), "fresh Renderer is dirty (so the host renders the first frame)");
    assertTrue(r.takeDirty());
    drawOnce(r, app);
    assertEquals(1, renderCount.get());
    assertFalse(r.isDirty(), "after takeDirty + render, clean");

    // No-op iteration: nothing to render.
    if (r.takeDirty()) {
      drawOnce(r, app);
    }
    assertEquals(1, renderCount.get(), "no extra render when not dirty");
  }

  /// Mouse dispatch routes through the registered handler and flips dirty.
  @Test
  void mouse_dispatch_through_renderer() throws IOException {
    Renderer r = new Renderer();
    AtomicBoolean clicked = new AtomicBoolean();

    Element app =
        component(
            ctx -> {
              ctx.onClick(e -> clicked.set(true));
              return text("clickable");
            });

    drawOnce(r, app);
    r.clearDirty();

    boolean fired =
        r.dispatchMouse(new MouseEvent(2, 0, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertTrue(fired, "click on the rendered text should fire onClick");
    assertTrue(clicked.get());
    assertTrue(r.isDirty(), "handler firing flips dirty so the host re-renders");
  }

  /// Key dispatch obeys focus + bubbles.
  @Test
  void key_dispatch_to_focused_fiber() throws IOException {
    Renderer r = new Renderer();
    AtomicInteger pressed = new AtomicInteger();

    Element app =
        component(
            ctx -> {
              ctx.useFocus(Optional.of("only"), true);
              ctx.onKey(new KeyCode.Char('a'), () -> pressed.incrementAndGet());
              return text("focused");
            });

    // Two renders so autoFocus has a chance to commit AND useFocus returns true so the onKey
    // handler routes via the focused-bubble path.
    drawOnce(r, app);
    drawOnce(r, app);
    r.clearDirty();

    boolean fired = r.dispatchKey(new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0)));
    assertTrue(fired);
    assertEquals(1, pressed.get());
  }

  /// tab / shiftTab cycle focus and flip dirty.
  @Test
  void tab_cycles_focus() throws IOException {
    Renderer r = new Renderer();
    List<Boolean> focusedFlags = new ArrayList<>();

    Element three =
        component(
            ctx -> {
              focusedFlags.clear();
              focusedFlags.add(focusOn(ctx, "a", true));
              focusedFlags.add(focusOn(ctx, "b", false));
              focusedFlags.add(focusOn(ctx, "c", false));
              return text("3 focusables");
            });

    // First render registers focusables; commit happens AFTER render so autoFocus on "a" only
    // takes effect for the *next* frame's useFocus call. (Same lazy semantics as Ink.)
    drawOnce(r, three);
    drawOnce(r, three);
    assertEquals(List.of(true, false, false), focusedFlags);

    r.tab();
    drawOnce(r, three);
    assertEquals(List.of(false, true, false), focusedFlags);

    r.tab();
    drawOnce(r, three);
    assertEquals(List.of(false, false, true), focusedFlags);

    r.shiftTab();
    drawOnce(r, three);
    assertEquals(List.of(false, true, false), focusedFlags);
  }

  /// resetState drops hook state AND runs cleanups.
  @Test
  void resetState_clears_hooks_and_runs_cleanups() throws IOException {
    Renderer r = new Renderer();
    AtomicInteger cleanupCount = new AtomicInteger();
    AtomicInteger initialCount = new AtomicInteger();

    Element app =
        component(
            ctx -> {
              ctx.useState(
                  () -> {
                    initialCount.incrementAndGet();
                    return 0;
                  });
              ctx.useEffect(
                  () -> {
                    // Effect registers a cleanup, but our useEffect API runs `effect` and the
                    // cleanup must be registered by the effect's body. Here we just simulate.
                  });
              return text("with-state");
            });

    drawOnce(r, app);
    drawOnce(r, app);
    assertEquals(1, initialCount.get(), "useState initializer should run only once");

    // Pre-seed a cleanup directly on the hook store (mirrors what useEffect would have done).
    HookKey k = new HookKey(Fiber.root().child(0), 99);
    r.events(); // touch a method to keep the renderer alive in the test
    // The hooks field is package-private; reach in via Renderer's same-package accessor.
    HookStore hooks = renderHooks(r);
    hooks.values.put(k, "x");
    hooks.cleanups.put(k, () -> cleanupCount.incrementAndGet());

    r.resetState();
    assertEquals(1, cleanupCount.get(), "resetState runs cleanups");
    assertTrue(hooks.values.isEmpty(), "values cleared");
    assertTrue(hooks.cleanups.isEmpty(), "cleanups cleared");
    assertTrue(r.isDirty(), "resetState flips dirty");

    // Next render starts fresh.
    drawOnce(r, app);
    assertEquals(2, initialCount.get(), "post-reset, useState initializer runs again");
  }

  /// requestRerender from a background thread is observed by the loop thread.
  @Test
  void request_rerender_threadsafe() throws Exception {
    Renderer r = new Renderer();
    r.clearDirty();
    assertFalse(r.isDirty());

    Thread t = new Thread(r::requestRerender);
    t.start();
    t.join();
    assertTrue(r.isDirty(), "requestRerender from another thread is visible");
  }

  // ---- helpers ----

  /// One frame against a TestBackend Terminal — this is the embedding pattern: build a Frame from
  /// somewhere, hand it to renderer.render. The host can use whatever Terminal it already owns.
  private static void drawOnce(Renderer r, Element root) throws IOException {
    TestBackend backend = new TestBackend(40, 12);
    Terminal<TestBackend> terminal = Terminal.create(backend);
    terminal.draw(frame -> r.render(frame, root));
  }

  private static boolean focusOn(RenderContext ctx, String id, boolean autoFocus) {
    return ctx.useFocus(Optional.of(id), autoFocus);
  }

  /// Same-package access to the internal HookStore for the resetState test.
  private static HookStore renderHooks(Renderer r) {
    return r.hooks();
  }
}
