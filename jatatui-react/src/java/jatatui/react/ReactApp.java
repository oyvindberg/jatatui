package jatatui.react;

import jatatui.core.terminal.Terminal;
import jatatui.crossterm.CrosstermBackend;
import jatatui.crossterm.Jatatui;
import java.io.IOException;
import tui.crossterm.CrosstermJni;
import tui.crossterm.Duration;
import tui.crossterm.Event;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyEventKind;
import tui.crossterm.KeyModifiers;

/// Opinionated event-loop runner for an [Element] tree on the terminal. Wraps a [Renderer] with:
///   - Crossterm input polling (100ms timeout — promptly wakes timer-driven re-renders)
///   - Tab / Shift-Tab / BackTab cycle focus
///   - Ctrl-C always quits; Esc quits when no handler intercepts it
///   - Mouse capture enabled while the loop runs
///
/// Re-render is **strictly event-driven**: the loop only redraws when:
///   - a `useState.set(...)` reports a change → flips dirty
///   - a Crossterm event arrives → may dispatch a handler that flips dirty
///   - the terminal is resized
///
/// If the host has its own loop, its own focus / Esc / quit semantics, or doesn't want mouse
/// capture, embed [Renderer] directly instead of using this.
public final class ReactApp {

  private final Element root;
  private final Renderer renderer;
  // Package-private so tests in this package can drive ReactApp without going through the JNI.
  final EventRegistry events;
  final HookStore hooks;
  final FocusManager focus;
  private final CrosstermJni jni;

  /// Package-private — tests construct ReactApp directly to drive `handle(...)` without going
  /// through the JNI poll loop.
  ReactApp(Element root, CrosstermJni jni) {
    this.root = root;
    this.renderer = new Renderer();
    this.events = renderer.events();
    this.hooks = renderer.hooks();
    this.focus = renderer.focus();
    this.jni = jni;
  }

  public static void run(Element root) throws IOException {
    Jatatui.runIo(
        terminal -> {
          CrosstermJni jni = new CrosstermJni();
          // Enable mouse capture so onClick / onScroll handlers receive events. Without this the
          // terminal never sends mouse events to the app at all. Disabled in finally so the
          // terminal is restored cleanly on exit (or panic, via Jatatui.runIo's cleanup).
          jni.execute(new tui.crossterm.Command.EnableMouseCapture());
          try {
            ReactApp app = new ReactApp(root, jni);
            app.loop(terminal);
          } finally {
            try {
              jni.execute(new tui.crossterm.Command.DisableMouseCapture());
            } catch (RuntimeException e) {
              // best-effort cleanup; don't mask the original failure
            }
          }
        });
  }

  private void loop(Terminal<CrosstermBackend> terminal) throws IOException {
    boolean running = true;
    while (running) {
      if (renderer.takeDirty()) {
        terminal.draw(frame -> renderer.render(frame, root));
      }
      // Poll timeout: 100ms normally (timer-driven re-renders like toasts wake the loop promptly),
      // but ~8ms while a `useFrame` tick source is live so its frame-rate re-renders are picked up
      // at animation speed (~60fps-capable). Pure event-driven for app correctness — the loop body
      // just re-checks `dirty`.
      int pollNanos = renderer.frameActive() ? 8_000_000 : 100_000_000;
      if (jni.poll(new Duration(0L, pollNanos))) {
        Event ev = jni.read();
        running = handle(ev);
      }
    }
  }

  /// Returns false to quit the loop.
  /// Package-private so tests can drive ReactApp without going through the JNI poll loop.
  boolean handle(Event ev) {
    if (ev instanceof Event.Key keyEv && keyEv.keyEvent().kind() == KeyEventKind.Press) {
      KeyCode code = keyEv.keyEvent().code();
      KeyModifiers mods = keyEv.keyEvent().modifiers();
      // Ctrl-C is unconditional (OS-style abort).
      if (code instanceof KeyCode.Char ch
          && ch.c() == 'c'
          && (mods.bits() & KeyModifiers.CONTROL) != 0) return false;
      if (code instanceof KeyCode.Tab) {
        if ((mods.bits() & KeyModifiers.SHIFT) != 0) renderer.shiftTab();
        else renderer.tab();
        return true;
      }
      // Most terminals send Shift-Tab as KeyCode.BackTab rather than Tab+SHIFT modifier.
      if (code instanceof KeyCode.BackTab) {
        renderer.shiftTab();
        return true;
      }
      KeyEvent kev = new KeyEvent(code, mods);
      boolean handled = renderer.dispatchKey(kev);
      // Esc-to-quit is a debug fallback: if nothing in the app handled Esc, quit. Apps that want
      // Esc to mean something (modal dismiss, form cancel) just register an Esc handler.
      if (!handled && code instanceof KeyCode.Esc) return false;
    } else if (ev instanceof Event.Mouse mouseEv) {
      var me = mouseEv.mouseEvent();
      MouseEvent.Kind kind =
          switch (me.kind()) {
            case tui.crossterm.MouseEventKind.Down ignored -> MouseEvent.Kind.DOWN;
            case tui.crossterm.MouseEventKind.Up ignored -> MouseEvent.Kind.UP;
            case tui.crossterm.MouseEventKind.Drag ignored -> MouseEvent.Kind.DRAG;
            case tui.crossterm.MouseEventKind.Moved ignored -> MouseEvent.Kind.MOVE;
            case tui.crossterm.MouseEventKind.ScrollUp ignored -> MouseEvent.Kind.SCROLL_UP;
            case tui.crossterm.MouseEventKind.ScrollDown ignored -> MouseEvent.Kind.SCROLL_DOWN;
            default -> null;
          };
      if (kind != null) {
        MouseEvent mev = new MouseEvent(me.column(), me.row(), me.modifiers(), kind);
        renderer.dispatchMouse(mev);
      }
    } else if (ev instanceof Event.Resize) {
      renderer.requestRerender();
    }
    return true;
  }
}
