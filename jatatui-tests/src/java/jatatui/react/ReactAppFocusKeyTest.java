package jatatui.react;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.backend.TestBackend;
import jatatui.core.terminal.Terminal;
import jatatui.widgets.Borders;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tui.crossterm.CrosstermJni;
import tui.crossterm.Event;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyEvent;
import tui.crossterm.KeyEventKind;
import tui.crossterm.KeyEventState;
import tui.crossterm.KeyModifiers;

/// Exercises ReactApp.handle directly to verify focus-key routing:
///   - Tab cycles forward
///   - Shift-Tab AND BackTab both cycle backward (terminals send one or the other)
///   - Esc / Ctrl-C quit
class ReactAppFocusKeyTest {

  /// A four-focusable Element: outer / middle / inner / button (in render order).
  /// Each `useFocus` registers in the order rendered.
  static Element fourFocusables() {
    return component(
        ctx -> {
          ctx.useFocus(Optional.of("outer"), true);
          return box(
              " Outer ",
              Borders.ALL,
              component(
                  c -> {
                    c.useFocus(Optional.of("middle"), false);
                    return box(
                        " Middle ",
                        Borders.ALL,
                        component(
                            c2 -> {
                              c2.useFocus(Optional.of("inner"), false);
                              return box(
                                  " Inner ",
                                  Borders.ALL,
                                  component(
                                      c3 -> {
                                        c3.useFocus(Optional.of("button"), false);
                                        return text("[ button ]");
                                      }));
                            }));
                  }));
        });
  }

  /// Render and prime ReactApp's state.
  static ReactApp prime(Element root) throws IOException {
    ReactApp app = new ReactApp(root, new CrosstermJni());
    TestBackend backend = new TestBackend(80, 24);
    Terminal<TestBackend> terminal = Terminal.create(backend);
    terminal.draw(
        frame -> {
          app.events.clear();
          app.focus.clearFrame();
          RenderContext ctx = new RenderContext(frame, app.events, app.hooks, app.focus, () -> {});
          app.events.recordBounds(Fiber.root(), frame.area());
          root.render(ctx, frame.area());
        });
    app.hooks.sweep();
    app.focus.commit();
    return app;
  }

  static Event keyEvent(KeyCode code, int modifierBits) {
    return new Event.Key(
        new KeyEvent(
            code, new KeyModifiers(modifierBits), KeyEventKind.Press, new KeyEventState(0)));
  }

  // ---- Forward (Tab) ----

  @Test
  void tab_cycles_forward_through_four_focusables() throws IOException {
    ReactApp app = prime(fourFocusables());
    assertEquals(Optional.of("outer"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.Tab(), 0));
    assertEquals(Optional.of("middle"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.Tab(), 0));
    assertEquals(Optional.of("inner"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.Tab(), 0));
    assertEquals(Optional.of("button"), app.focus.currentlyFocused());

    // Wraps around
    app.handle(keyEvent(new KeyCode.Tab(), 0));
    assertEquals(Optional.of("outer"), app.focus.currentlyFocused());
  }

  // ---- Backward (Shift+Tab AND BackTab) ----

  @Test
  void shift_tab_cycles_backward() throws IOException {
    ReactApp app = prime(fourFocusables());
    assertEquals(Optional.of("outer"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.Tab(), KeyModifiers.SHIFT));
    assertEquals(Optional.of("button"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.Tab(), KeyModifiers.SHIFT));
    assertEquals(Optional.of("inner"), app.focus.currentlyFocused());
  }

  /// Most terminals send Shift-Tab as KeyCode.BackTab rather than KeyCode.Tab + SHIFT.
  /// Both must work.
  @Test
  void back_tab_cycles_backward() throws IOException {
    ReactApp app = prime(fourFocusables());
    assertEquals(Optional.of("outer"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.BackTab(), 0));
    assertEquals(Optional.of("button"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.BackTab(), 0));
    assertEquals(Optional.of("inner"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.BackTab(), 0));
    assertEquals(Optional.of("middle"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.BackTab(), 0));
    assertEquals(Optional.of("outer"), app.focus.currentlyFocused());
  }

  // ---- Quit ----

  @Test
  void esc_quits_loop() throws IOException {
    ReactApp app = prime(fourFocusables());
    boolean stillRunning = app.handle(keyEvent(new KeyCode.Esc(), 0));
    assertFalse(stillRunning);
  }

  @Test
  void ctrl_c_quits_loop() throws IOException {
    ReactApp app = prime(fourFocusables());
    boolean stillRunning = app.handle(keyEvent(new KeyCode.Char('c'), KeyModifiers.CONTROL));
    assertFalse(stillRunning);
  }

  @Test
  void plain_q_quits_loop() throws IOException {
    // Per the existing isQuit logic, plain Esc and Ctrl-C quit; plain 'q' alone does NOT.
    // Components should explicitly bind it via onGlobalKey if they want.
    ReactApp app = prime(fourFocusables());
    boolean stillRunning = app.handle(keyEvent(new KeyCode.Char('q'), 0));
    assertTrue(stillRunning, "plain 'q' should not quit unless an app-defined handler does it");
  }

  // ---- Focus key only fires for focused fiber's chain ----

  @Test
  void shift_tab_starting_from_inner_goes_to_middle() throws IOException {
    ReactApp app = prime(fourFocusables());
    // Tab to inner (outer → middle → inner)
    app.handle(keyEvent(new KeyCode.Tab(), 0));
    app.handle(keyEvent(new KeyCode.Tab(), 0));
    assertEquals(Optional.of("inner"), app.focus.currentlyFocused());

    app.handle(keyEvent(new KeyCode.BackTab(), 0));
    assertEquals(Optional.of("middle"), app.focus.currentlyFocused());
  }

  @Test
  void unused_param_warning_suppressor() {
    @SuppressWarnings("unused")
    List<Object> __ = List.of();
  }
}
