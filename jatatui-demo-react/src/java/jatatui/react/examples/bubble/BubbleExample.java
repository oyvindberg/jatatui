package jatatui.react.examples.bubble;

import static jatatui.react.Components.*;

import jatatui.core.layout.Margin;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import jatatui.react.State;
import jatatui.widgets.Borders;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tui.crossterm.KeyCode;

/// End-to-end test of tree-walking event dispatch.
///
/// Three nested boxes — Outer / Middle / Inner — each register:
///   - an `onClick` over their whole area
///   - an `onKey('a')` (focus-scoped — fires only when this fiber is focused or focus is below)
///
/// Inside Inner there's a `[ Stop click here ]` button whose `onClick` calls
/// `e.stopPropagation()`, demonstrating that bubbling can be halted.
///
/// Globally, `c` clears the log via `onGlobalKey` — fires regardless of focus.
///
/// Try:
///   - Tab cycles focus between the three boxes
///   - Click anywhere inside Inner → log shows `Inner click`, `Middle click`, `Outer click`
///     (the click bubbled up the fiber tree)
///   - Click `[ Stop click here ]` → log shows just `Inner button (stopped)`
///   - Press `a` while Inner is focused → log shows `'a' at inner`, `'a' at middle`, `'a' at outer`
///   - Press `a` while Outer is focused → log shows just `'a' at outer`
///   - Press `c` anywhere → log clears
///   - Press `q` or `Esc` to quit
public final class BubbleExample {

  public static void main(String[] args) throws IOException {
    ReactApp.run(app());
  }

  // ---- Top-level ----

  static Element app() {
    return component(
        ctx -> {
          var log = ctx.useState(() -> List.<String>of());

          // Global "clear log": fires regardless of focus, after the focus chain is done.
          ctx.onGlobalKey(new KeyCode.Char('c'), () -> log.set(List.of()));

          return column(
                  length(
                      1,
                      text(
                          " event bubbling demo  —  Tab=focus, click + 'a' to fire, 'c' clears,"
                              + " q quits ",
                          Style.empty().withFg(Color.WHITE).withBg(Color.BLUE))),
                  fill(
                      1,
                      row(fill(2, nestedBoxes(log)), fill(1, logPanel(log.get())))
                          .with(p -> p.withSpacing(1))),
                  length(1, hint()))
              .with(p -> p.withMargin(new Margin(1, 0)));
        });
  }

  static Element hint() {
    return text(
        " Tab/Shift-Tab=cycle focus  •  Enter on focused button stops propagation  •"
            + "  Esc quits  •  'c' clears (global)",
        Style.empty().withFg(Color.GRAY));
  }

  // ---- Nested boxes ----

  static Element nestedBoxes(State<List<String>> log) {
    return outerBox(log, middleBox(log, innerBox(log)));
  }

  static Element outerBox(State<List<String>> log, Element child) {
    return component(
        ctx -> {
          boolean focused = ctx.useFocus(Optional.of("outer"), true);

          // "Anywhere in this Component's bounds" — uses the new ctx.area() default.
          ctx.onClick(() -> log.update(prev -> append(prev, "Outer click")));
          ctx.onKey(new KeyCode.Char('a'), () -> log.update(prev -> append(prev, "'a' at outer")));

          return box(
              focused ? " Outer ★ " : " Outer ",
              Borders.ALL,
              text(
                  focused ? "(focused: 'a' starts here, then bubbles)" : "(Tab to focus)",
                  Style.empty().withFg(focused ? Color.YELLOW : Color.GRAY)),
              fill(1, child));
        });
  }

  static Element middleBox(State<List<String>> log, Element child) {
    return component(
        ctx -> {
          boolean focused = ctx.useFocus(Optional.of("middle"), false);

          ctx.onClick(() -> log.update(prev -> append(prev, "Middle click")));
          ctx.onKey(new KeyCode.Char('a'), () -> log.update(prev -> append(prev, "'a' at middle")));

          return box(
              focused ? " Middle ★ " : " Middle ",
              Borders.ALL,
              text(
                  focused ? "(focused)" : "",
                  Style.empty().withFg(focused ? Color.YELLOW : Color.GRAY)),
              fill(1, child));
        });
  }

  static Element innerBox(State<List<String>> log) {
    return component(
        ctx -> {
          boolean focused = ctx.useFocus(Optional.of("inner"), false);

          ctx.onClick(() -> log.update(prev -> append(prev, "Inner click")));
          ctx.onKey(new KeyCode.Char('a'), () -> log.update(prev -> append(prev, "'a' at inner")));

          return box(
              focused ? " Inner ★ " : " Inner ",
              Borders.ALL,
              text(
                  focused ? "(focused)" : "",
                  Style.empty().withFg(focused ? Color.YELLOW : Color.GRAY)),
              text(""),
              text("  Click anywhere here to see bubbling.", Style.empty().withFg(Color.WHITE)),
              text(""),
              focusableStopButton(log));
        });
  }

  /// Wrap the button in its own focusable component. Tab can land on it; Enter activates it
  /// (with stopPropagation, like a click).
  static Element focusableStopButton(State<List<String>> log) {
    return component(
        ctx -> {
          boolean focused = ctx.useFocus(Optional.of("button"), false);

          ctx.onKey(
              new KeyCode.Enter(),
              (jatatui.react.KeyEvent e) -> {
                log.update(prev -> append(prev, "Inner button via Enter (stopped)"));
                e.stopPropagation();
              });

          return button(
              focused ? "  [ Stop click HERE ★ ]  " : "  [ Stop click here ]  ",
              Style.empty().withFg(focused ? Color.YELLOW : Color.RED),
              (jatatui.react.MouseEvent e) -> {
                log.update(prev -> append(prev, "Inner button (stopped)"));
                e.stopPropagation();
              });
        });
  }

  // ---- Log panel ----

  static Element logPanel(List<String> entries) {
    if (entries.isEmpty()) {
      return box(
          " Log ",
          Borders.ALL,
          text("(empty — try clicking or pressing 'a')", Style.empty().withFg(Color.GRAY)));
    }
    // newest first
    List<String> reversed = new ArrayList<>(entries);
    java.util.Collections.reverse(reversed);
    return box(
        " Log (newest first) ",
        Borders.ALL,
        forEach(
            reversed,
            // Need a stable-ish key per row. Indices won't work because rows shift on prepend; use
            // sequential numbering baked into the entry text (see append()).
            s -> s,
            s -> text("  " + s, Style.empty().withFg(Color.WHITE))));
  }

  // ---- Helpers ----

  /// Append with a sequence number so forEach's keyFn doesn't collide on duplicate messages.
  private static List<String> append(List<String> prev, String msg) {
    List<String> next = new ArrayList<>(prev);
    next.add("[" + (prev.size() + 1) + "] " + msg);
    return List.copyOf(next);
  }
}
