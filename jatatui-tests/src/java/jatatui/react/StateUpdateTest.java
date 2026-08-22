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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyModifiers;

/// Reproduces the user-reported bug: focused inner, typed 'a' → only "outer" appeared.
///
/// The dispatch correctly fires inner/middle/outer in order, but each handler calls
/// `state.update(prev -> ...)` reading the captured render-time `prev` — so they all see the
/// SAME prev value and the last write wins.
class StateUpdateTest {

  @Test
  void multiple_state_updates_within_one_dispatch_all_take_effect() throws IOException {
    EventRegistry events = new EventRegistry();
    HookStore hooks = new HookStore();
    FocusManager focus = new FocusManager();

    // The app holds a State<List<String>> at the root; three nested components each register
    // onKey('a') that does state.update(prev -> append(prev, "<level>")).
    AtomicReference<State<List<String>>> rootStateRef = new AtomicReference<>();

    Element app =
        component(
            ctx -> {
              State<List<String>> log = ctx.useState(() -> List.<String>of());
              rootStateRef.set(log);
              return component(
                  innerCtx -> {
                    innerCtx.useFocus(Optional.of("outer"), true);
                    innerCtx.onKey(
                        new KeyCode.Char('a'), () -> log.update(prev -> append(prev, "outer")));
                    return box(
                        " Outer ",
                        Borders.ALL,
                        component(
                            mctx -> {
                              mctx.useFocus(Optional.of("middle"), false);
                              mctx.onKey(
                                  new KeyCode.Char('a'),
                                  () -> log.update(prev -> append(prev, "middle")));
                              return box(
                                  " Middle ",
                                  Borders.ALL,
                                  component(
                                      ictx -> {
                                        ictx.useFocus(Optional.of("inner"), false);
                                        ictx.onKey(
                                            new KeyCode.Char('a'),
                                            () -> log.update(prev -> append(prev, "inner")));
                                        return box(" Inner ", Borders.ALL, text("hi"));
                                      }));
                            }));
                  });
            });

    rerender(events, hooks, focus, app, 80, 24);
    focus.tab();
    focus.tab(); // → inner
    rerender(events, hooks, focus, app, 80, 24);
    assertEquals(Optional.of("inner"), focus.currentlyFocused());

    // Press 'a': should fire inner, middle, outer in order.
    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    events.dispatchKey(ev, focus.focusedFiber());

    // The State stored in HookStore should contain all three messages.
    @SuppressWarnings("unchecked")
    List<String> finalLog =
        (List<String>)
            hooks.values.values().stream().filter(v -> v instanceof List).findFirst().orElseThrow();

    assertEquals(List.of("inner", "middle", "outer"), finalLog);
  }

  @Test
  void multiple_clicks_bubble_with_state_updates_all_take_effect() throws IOException {
    EventRegistry events = new EventRegistry();
    HookStore hooks = new HookStore();
    FocusManager focus = new FocusManager();

    Element app =
        component(
            ctx -> {
              State<List<String>> log = ctx.useState(() -> List.<String>of());
              return component(
                  innerCtx -> {
                    innerCtx.onClick(() -> log.update(prev -> append(prev, "outer")));
                    return box(
                        " Outer ",
                        Borders.ALL,
                        component(
                            mctx -> {
                              mctx.onClick(() -> log.update(prev -> append(prev, "middle")));
                              return box(
                                  " Middle ",
                                  Borders.ALL,
                                  component(
                                      ictx -> {
                                        ictx.onClick(
                                            () -> log.update(prev -> append(prev, "inner")));
                                        return box(" Inner ", Borders.ALL, text("hi"));
                                      }));
                            }));
                  });
            });

    rerender(events, hooks, focus, app, 80, 24);

    // Click well inside the inner box. With 3 nested borders (1 cell each), inner content starts
    // around (3, 3) on a fully-filled 80x24.
    MouseEvent ev = new MouseEvent(20, 10, new KeyModifiers(0), MouseEvent.Kind.DOWN);
    events.dispatchMouse(ev);

    @SuppressWarnings("unchecked")
    List<String> finalLog =
        (List<String>)
            hooks.values.values().stream().filter(v -> v instanceof List).findFirst().orElseThrow();

    assertEquals(List.of("inner", "middle", "outer"), finalLog);
  }

  /// Mirrors the bubble demo's actual layout: column → row → fill(2, nestedBoxes) plus a sibling
  /// log panel on the right. Click inside the nested-boxes column should bubble all the way up.
  ///
  /// (This is the test most directly equivalent to running `bleep run jatatui-demo-react -- bubble`
  /// and clicking inside the inner box.)
  @Test
  void click_in_full_demo_wrapping_with_state_updates_all_take_effect() throws IOException {
    EventRegistry events = new EventRegistry();
    HookStore hooks = new HookStore();
    FocusManager focus = new FocusManager();

    Element app =
        component(
            ctx -> {
              State<List<String>> log = ctx.useState(() -> List.<String>of());
              return column(
                      length(1, text(" header ")),
                      fill(
                          1,
                          row(fill(2, nestedClickBoxes(log)), fill(1, text(" log panel ")))
                              .with(p -> p.withSpacing(1))),
                      length(1, text(" hint ")))
                  .with(p -> p.withMargin(new jatatui.core.layout.Margin(1, 0)));
            });

    rerender(events, hooks, focus, app, 80, 24);

    // (20, 10) is well inside the nested-boxes column on an 80x24 buffer with 1-cell margin and
    // borders (left side; the row's first child is fill(2, ...) so it gets ~50 cols of width).
    MouseEvent ev = new MouseEvent(20, 10, new KeyModifiers(0), MouseEvent.Kind.DOWN);
    events.dispatchMouse(ev);

    @SuppressWarnings("unchecked")
    List<String> finalLog =
        (List<String>)
            hooks.values.values().stream().filter(v -> v instanceof List).findFirst().orElseThrow();

    assertEquals(List.of("inner", "middle", "outer"), finalLog);
  }

  static Element nestedClickBoxes(State<List<String>> log) {
    return component(
        ctx -> {
          ctx.onClick(() -> log.update(prev -> append(prev, "outer")));
          return box(
              " Outer ",
              Borders.ALL,
              component(
                  mctx -> {
                    mctx.onClick(() -> log.update(prev -> append(prev, "middle")));
                    return box(
                        " Middle ",
                        Borders.ALL,
                        component(
                            ictx -> {
                              ictx.onClick(() -> log.update(prev -> append(prev, "inner")));
                              return box(" Inner ", Borders.ALL, text("hi"));
                            }));
                  }));
        });
  }

  static List<String> append(List<String> prev, String s) {
    List<String> next = new ArrayList<>(prev);
    next.add(s);
    return List.copyOf(next);
  }

  static void rerender(
      EventRegistry events, HookStore hooks, FocusManager focus, Element root, int w, int h)
      throws IOException {
    TestBackend backend = new TestBackend(w, h);
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
  }
}
