package jatatui.react;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.backend.TestBackend;
import jatatui.core.terminal.Terminal;
import jatatui.widgets.Borders;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyModifiers;

/// Integration tests: render a tree of Components that register handlers,
/// then dispatch events through [EventRegistry] and verify the right handlers fire.
///
/// Mirrors what the `bubble` demo does at runtime. Reproduces the user-reported bug:
///  > "focused inner, typed a — only outer fires; click inner — nothing happens"
class EventBubblingIntegrationTest {

  /// Render harness — renders the Element to a TestBackend, returns the components for dispatch.
  record Harness(EventRegistry events, FocusManager focus, HookStore hooks) {}

  static Harness render(Element root, int width, int height) throws IOException {
    EventRegistry events = new EventRegistry();
    HookStore hooks = new HookStore();
    FocusManager focus = new FocusManager();
    TestBackend backend = new TestBackend(width, height);
    Terminal<TestBackend> terminal = Terminal.create(backend);

    terminal.draw(
        frame -> {
          events.clear();
          focus.clearFrame();
          RenderContext ctx = new RenderContext(frame, events, hooks, focus, () -> {});
          events.recordBounds(Fiber.root(), frame.area());
          root.render(ctx, frame.area());
        });
    hooks.sweep();
    focus.commit();
    return new Harness(events, focus, hooks);
  }

  // ---------- Mirror the bubble demo: three nested components, register onClick + onKey ----------

  static Element nestedBoxes(List<String> log) {
    return outerBox(log, middleBox(log, innerBox(log)));
  }

  static Element outerBox(List<String> log, Element child) {
    return component(
        ctx -> {
          ctx.useFocus(Optional.of("outer"), true);
          ctx.onClick(() -> log.add("outer click"));
          ctx.onKey(new KeyCode.Char('a'), () -> log.add("outer 'a'"));
          return box(" Outer ", Borders.ALL, child);
        });
  }

  static Element middleBox(List<String> log, Element child) {
    return component(
        ctx -> {
          ctx.useFocus(Optional.of("middle"), false);
          ctx.onClick(() -> log.add("middle click"));
          ctx.onKey(new KeyCode.Char('a'), () -> log.add("middle 'a'"));
          return box(" Middle ", Borders.ALL, child);
        });
  }

  static Element innerBox(List<String> log) {
    return component(
        ctx -> {
          ctx.useFocus(Optional.of("inner"), false);
          ctx.onClick(() -> log.add("inner click"));
          ctx.onKey(new KeyCode.Char('a'), () -> log.add("inner 'a'"));
          return box(" Inner ", Borders.ALL, text("Hello"));
        });
  }

  // ---------- Tests ----------

  @Test
  void key_a_fires_at_outer_when_outer_focused() throws IOException {
    List<String> log = new ArrayList<>();
    Harness h = render(nestedBoxes(log), 80, 24);

    // Outer should be auto-focused (autoFocus=true)
    assertEquals(Optional.of("outer"), h.focus().currentlyFocused());

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    h.events().dispatchKey(ev, h.focus().focusedFiber());

    assertEquals(List.of("outer 'a'"), log);
  }

  @Test
  void key_a_bubbles_through_middle_when_middle_focused() throws IOException {
    List<String> log = new ArrayList<>();
    Harness h = render(nestedBoxes(log), 80, 24);

    // Tab once to move focus from outer → middle
    h.focus().tab();
    log.clear();

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    h.events().dispatchKey(ev, h.focus().focusedFiber());

    assertEquals(List.of("middle 'a'", "outer 'a'"), log);
  }

  @Test
  void key_a_bubbles_full_chain_when_inner_focused() throws IOException {
    List<String> log = new ArrayList<>();
    Harness h = render(nestedBoxes(log), 80, 24);

    h.focus().tab();
    h.focus().tab();
    log.clear();

    assertEquals(Optional.of("inner"), h.focus().currentlyFocused());

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    h.events().dispatchKey(ev, h.focus().focusedFiber());

    assertEquals(List.of("inner 'a'", "middle 'a'", "outer 'a'"), log);
  }

  /// Mirror the actual bubble demo's structure: column → row → fill(2, nestedBoxes). The user
  /// reported that in the live demo, only "Outer" fires when inner is focused. This test
  /// reproduces that exact wrapping.
  @Test
  void key_a_bubbles_in_full_demo_wrapping() throws IOException {
    List<String> log = new ArrayList<>();
    Element appLike =
        column(
                length(1, text(" header ")),
                fill(
                    1,
                    row(fill(2, nestedBoxes(log)), fill(1, text(" log panel ")))
                        .with(p -> p.withSpacing(1))),
                length(1, text(" hint ")))
            .with(p -> p.withMargin(new jatatui.core.layout.Margin(1, 0)));

    Harness h = render(appLike, 80, 24);

    // Tab to inner (outer is auto-focused → tab → middle → tab → inner)
    h.focus().tab();
    h.focus().tab();
    log.clear();

    assertEquals(
        Optional.of("inner"),
        h.focus().currentlyFocused(),
        "tab cycle should reach inner after two presses");

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    h.events().dispatchKey(ev, h.focus().focusedFiber());

    assertEquals(List.of("inner 'a'", "middle 'a'", "outer 'a'"), log);
  }

  /// Re-render flow: simulates the full ReactApp loop.
  /// Tab → re-render → Tab → re-render → 'a' → dispatch.
  /// Catches bugs where re-renders break fiber identity / handler registration.
  @Test
  void tab_render_tab_render_a_bubbles_full_chain() throws IOException {
    List<String> log = new ArrayList<>();
    EventRegistry events = new EventRegistry();
    HookStore hooks = new HookStore();
    FocusManager focus = new FocusManager();

    // Initial render
    rerender(events, hooks, focus, () -> nestedBoxes(log), 80, 24);
    assertEquals(Optional.of("outer"), focus.currentlyFocused());

    // Tab → re-render
    focus.tab();
    rerender(events, hooks, focus, () -> nestedBoxes(log), 80, 24);
    assertEquals(Optional.of("middle"), focus.currentlyFocused());

    // Tab → re-render
    focus.tab();
    rerender(events, hooks, focus, () -> nestedBoxes(log), 80, 24);
    assertEquals(Optional.of("inner"), focus.currentlyFocused());

    log.clear();

    // 'a' should bubble inner → middle → outer
    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    events.dispatchKey(ev, focus.focusedFiber());

    assertEquals(List.of("inner 'a'", "middle 'a'", "outer 'a'"), log);
  }

  static void rerender(
      EventRegistry events,
      HookStore hooks,
      FocusManager focus,
      java.util.function.Supplier<Element> root,
      int width,
      int height)
      throws IOException {
    TestBackend backend = new TestBackend(width, height);
    Terminal<TestBackend> terminal = Terminal.create(backend);
    terminal.draw(
        frame -> {
          events.clear();
          focus.clearFrame();
          RenderContext ctx = new RenderContext(frame, events, hooks, focus, () -> {});
          events.recordBounds(Fiber.root(), frame.area());
          root.get().render(ctx, frame.area());
        });
    hooks.sweep();
    focus.commit();
  }

  @Test
  void click_inside_inner_bubbles_through_middle_to_outer() throws IOException {
    List<String> log = new ArrayList<>();
    Harness h = render(nestedBoxes(log), 80, 24);

    // The inner box is the deepest. Outer fills the whole 80x24, with a 1-cell border on each
    // side; middle the same inside outer; inner the same inside middle. So a click at (20, 10)
    // is well inside all three.
    MouseEvent ev = new MouseEvent(20, 10, new KeyModifiers(0), MouseEvent.Kind.DOWN);
    h.events().dispatchMouse(ev);

    assertEquals(List.of("inner click", "middle click", "outer click"), log);
  }
}
