package jatatui.react;

import jatatui.core.layout.Rect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/// Per-frame collection of event handlers and fiber bounds, plus the dispatch logic that walks
/// the fiber tree from the event target up to the root.
///
/// Storage is bucketed by [Fiber] (not flat) so dispatch can walk the parent chain of a focused or
/// hit-tested target. This is the React-DOM bubbling model:
///   - **Mouse**: hit-test finds the deepest Fiber whose recorded bounds contain (x,y); we then
///     walk from that Fiber up to root, firing matching click/scroll handlers along the way.
///     Handlers can call [MouseEvent#stopPropagation] to prevent further ancestors from being
///     notified.
///   - **Key**: walk from the focused Fiber up to root, firing matching key handlers. Then global
///     key handlers fire (lowest priority — last-resort shortcuts). [KeyEvent#stopPropagation]
///     stops the chain anywhere along it.
public final class EventRegistry {

  // Per-fiber handler buckets
  private final Map<Fiber, List<AreaHandler>> clickByFiber = new HashMap<>();
  private final Map<Fiber, List<AreaHandler>> scrollByFiber = new HashMap<>();
  private final Map<Fiber, List<KeyHandler>> keysByFiber = new HashMap<>();

  // Global key handlers (no fiber, fire after fiber-bubble)
  private final List<KeyHandler> globalKeys = new ArrayList<>();

  // Per-fiber recorded bounds — used for hit-testing
  private final Map<Fiber, Rect> bounds = new HashMap<>();

  void clear() {
    clickByFiber.clear();
    scrollByFiber.clear();
    keysByFiber.clear();
    globalKeys.clear();
    bounds.clear();
  }

  // -------------------- Registration --------------------

  void recordBounds(Fiber f, Rect r) {
    bounds.put(f, r);
  }

  /// The bounds last recorded for `f` this frame, if any. Used by `RenderContext.area()` so
  /// components can register handlers "for my whole area" without re-threading the Rect.
  Optional<Rect> boundsOf(Fiber f) {
    return Optional.ofNullable(bounds.get(f));
  }

  void addClick(Fiber f, Rect area, Consumer<MouseEvent> handler) {
    clickByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new AreaHandler(area, handler));
  }

  void addScroll(Fiber f, Rect area, Consumer<MouseEvent> handler) {
    scrollByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new AreaHandler(area, handler));
  }

  void addKey(Fiber f, Object matcher, Consumer<KeyEvent> handler) {
    keysByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new KeyHandler(matcher, handler));
  }

  void addGlobalKey(Object matcher, Consumer<KeyEvent> handler) {
    globalKeys.add(new KeyHandler(matcher, handler));
  }

  // -------------------- Dispatch --------------------

  /// Dispatch a mouse event to the deepest fiber containing (x,y) and bubble up. Returns true if
  /// any handler fired.
  public boolean dispatchMouse(MouseEvent ev) {
    Map<Fiber, List<AreaHandler>> bucket =
        switch (ev.kind()) {
          case DOWN -> clickByFiber;
          case SCROLL_UP, SCROLL_DOWN -> scrollByFiber;
          // UP, DRAG, MOVE: not currently delivered (no public API yet)
          default -> null;
        };
    if (bucket == null) return false;

    Fiber target = deepestAt(ev.x(), ev.y());
    if (target == null) return false;

    boolean fired = false;
    Fiber cur = target;
    while (cur != null && !ev.isPropagationStopped()) {
      List<AreaHandler> handlers = bucket.get(cur);
      if (handlers != null) {
        for (AreaHandler h : handlers) {
          if (contains(h.area, ev.x(), ev.y())) {
            h.handler.accept(ev);
            fired = true;
            if (ev.isPropagationStopped()) break;
          }
        }
      }
      cur = cur.parent().orElse(null);
    }
    return fired;
  }

  /// Dispatch a key event. Bubbles from `focused` up to root, then fires global handlers.
  /// Returns true if any handler fired.
  public boolean dispatchKey(KeyEvent ev, Optional<Fiber> focused) {
    boolean fired = false;

    // Bubble phase: focused → root
    Fiber cur = focused.orElse(null);
    while (cur != null && !ev.isPropagationStopped()) {
      List<KeyHandler> handlers = keysByFiber.get(cur);
      if (handlers != null) {
        for (KeyHandler h : handlers) {
          if (matches(h.matcher, ev.code())) {
            h.handler.accept(ev);
            fired = true;
            if (ev.isPropagationStopped()) break;
          }
        }
      }
      cur = cur.parent().orElse(null);
    }

    // Global handlers fire last (after the whole bubble chain), if still propagating.
    if (!ev.isPropagationStopped()) {
      for (KeyHandler h : globalKeys) {
        if (matches(h.matcher, ev.code())) {
          h.handler.accept(ev);
          fired = true;
          if (ev.isPropagationStopped()) break;
        }
      }
    }
    return fired;
  }

  // -------------------- Hit testing --------------------

  /// Find the deepest fiber whose recorded bounds contain (x,y). Returns null if none.
  private Fiber deepestAt(int x, int y) {
    Fiber best = null;
    int bestDepth = -1;
    for (Map.Entry<Fiber, Rect> e : bounds.entrySet()) {
      Rect r = e.getValue();
      if (contains(r, x, y)) {
        int d = e.getKey().depth();
        if (d > bestDepth) {
          best = e.getKey();
          bestDepth = d;
        }
      }
    }
    return best;
  }

  private static boolean contains(Rect r, int x, int y) {
    return x >= r.x() && x < r.x() + r.width() && y >= r.y() && y < r.y() + r.height();
  }

  /// Does the matcher accept this code? Two flavors:
  ///   - a [java.util.function.Predicate] of [tui.crossterm.KeyCode]: tested against `code`
  ///   - anything else: compared via `equals(code)`
  /// The Predicate path lets handlers match families of keys ("any printable char", "any digit")
  /// without registering N handlers.
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static boolean matches(Object matcher, tui.crossterm.KeyCode code) {
    if (matcher instanceof java.util.function.Predicate p) return p.test(code);
    return matcher.equals(code);
  }

  private record AreaHandler(Rect area, Consumer<MouseEvent> handler) {}

  private record KeyHandler(Object matcher, Consumer<KeyEvent> handler) {}
}
