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
///   - **Mouse**: hit-test finds the topmost Fiber whose recorded bounds contain (x,y) —
///     "topmost" resolved by paint order (see below), NOT merely fiber depth; we then walk from
///     that Fiber up to root, firing matching handlers along the way. Handlers can call
///     [MouseEvent#stopPropagation] to prevent further ancestors from being notified.
///   - **Key**: walk from the focused Fiber up to root, firing matching key handlers. Then global
///     key handlers fire (lowest priority — last-resort shortcuts). [KeyEvent#stopPropagation]
///     stops the chain anywhere along it.
///
/// ## Z-order / paint-order hit-testing
///
/// The buffer is immediate-mode: a cell's visible content is whatever painted it **last**. So the
/// widget a user "sees" at a point is the last one to paint there. Hit-testing must agree with
/// that, otherwise clicking a modal would fall through to the widget behind it.
///
/// We capture paint order directly. [#recordBounds] is called in pre-order traversal — a fiber
/// records its bounds immediately before its subtree paints — so the recording order **is** the
/// paint order. Each record carries:
///   - `z`   — a layer index. The main pass is layer 0; each generation of portals drained after
///             the main pass gets a strictly higher layer (see [RenderContext#drainPortals]). This
///             makes portals (modals, dropdowns, tooltips) win hit-tests over the main content they
///             overlap, regardless of how deep in the fiber tree that content sits.
///   - `seq` — a monotonically increasing paint sequence number within the frame. Uniquely orders
///             every record, so within a layer "last painted wins" (a later sibling over an earlier
///             one; a child over its parent) with no ties to break arbitrarily.
///
/// Hit resolution picks the containing bound with the greatest `(z, seq)`. This subsumes the old
/// depth heuristic (a nested child paints after its parent → higher seq → still wins) while also
/// handling the two cases depth got wrong: equal-depth overlapping siblings (resolved by paint
/// order, not `HashMap` iteration order) and portals (resolved by layer, not depth).
public final class EventRegistry {

  // Per-fiber handler buckets, one per routed mouse kind.
  private final Map<Fiber, List<AreaHandler>> clickByFiber = new HashMap<>();
  private final Map<Fiber, List<AreaHandler>> scrollByFiber = new HashMap<>();
  private final Map<Fiber, List<AreaHandler>> hoverByFiber = new HashMap<>();
  private final Map<Fiber, List<AreaHandler>> dragByFiber = new HashMap<>();
  private final Map<Fiber, List<AreaHandler>> dragEndByFiber = new HashMap<>();
  private final Map<Fiber, List<KeyHandler>> keysByFiber = new HashMap<>();

  // Global key handlers (no fiber, fire after fiber-bubble)
  private final List<KeyHandler> globalKeys = new ArrayList<>();

  // Per-fiber recorded bounds — used for hit-testing. Carries paint order (z, seq).
  private final Map<Fiber, Bound> bounds = new HashMap<>();
  private int seqCounter;

  void clear() {
    clickByFiber.clear();
    scrollByFiber.clear();
    hoverByFiber.clear();
    dragByFiber.clear();
    dragEndByFiber.clear();
    keysByFiber.clear();
    globalKeys.clear();
    bounds.clear();
    seqCounter = 0;
  }

  // -------------------- Registration --------------------

  /// Record `f`'s bounds for hit-testing at paint layer `z`. Called in pre-order during render, so
  /// the call order is the paint order; each record gets the next paint sequence number.
  void recordBounds(Fiber f, Rect r, int z) {
    bounds.put(f, new Bound(r, z, seqCounter++));
  }

  /// Record `f`'s bounds on the base paint layer (z = 0). Convenience for callers that don't deal
  /// in portal layers (e.g. the root bound, direct unit tests).
  void recordBounds(Fiber f, Rect r) {
    recordBounds(f, r, 0);
  }

  /// The bounds last recorded for `f` this frame, if any. Used by `RenderContext.area()` so
  /// components can register handlers "for my whole area" without re-threading the Rect.
  Optional<Rect> boundsOf(Fiber f) {
    return Optional.ofNullable(bounds.get(f)).map(Bound::rect);
  }

  void addClick(Fiber f, Rect area, Consumer<MouseEvent> handler) {
    clickByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new AreaHandler(area, handler));
  }

  void addScroll(Fiber f, Rect area, Consumer<MouseEvent> handler) {
    scrollByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new AreaHandler(area, handler));
  }

  void addHover(Fiber f, Rect area, Consumer<MouseEvent> handler) {
    hoverByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new AreaHandler(area, handler));
  }

  void addDrag(Fiber f, Rect area, Consumer<MouseEvent> handler) {
    dragByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new AreaHandler(area, handler));
  }

  void addDragEnd(Fiber f, Rect area, Consumer<MouseEvent> handler) {
    dragEndByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new AreaHandler(area, handler));
  }

  void addKey(Fiber f, Object matcher, Consumer<KeyEvent> handler) {
    keysByFiber.computeIfAbsent(f, k -> new ArrayList<>()).add(new KeyHandler(matcher, handler));
  }

  void addGlobalKey(Object matcher, Consumer<KeyEvent> handler) {
    globalKeys.add(new KeyHandler(matcher, handler));
  }

  // -------------------- Dispatch --------------------

  /// Dispatch a mouse event to the topmost fiber containing (x,y) and bubble up. Every mouse kind
  /// routes to its own handler bucket: `DOWN`→click, `SCROLL_*`→scroll, `MOVE`→hover, `DRAG`→drag,
  /// `UP`→drag-end. Returns true if any handler fired.
  public boolean dispatchMouse(MouseEvent ev) {
    Map<Fiber, List<AreaHandler>> bucket =
        switch (ev.kind()) {
          case DOWN -> clickByFiber;
          case SCROLL_UP, SCROLL_DOWN -> scrollByFiber;
          case MOVE -> hoverByFiber;
          case DRAG -> dragByFiber;
          case UP -> dragEndByFiber;
        };

    Fiber target = topmostAt(ev.x(), ev.y());
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

  /// Find the topmost fiber whose recorded bounds contain (x,y): the containing bound with the
  /// greatest `(z, seq)`. `z` (paint layer) dominates so portals win over the content they cover;
  /// within a layer `seq` (paint order) breaks ties so the last-painted widget wins. Returns null
  /// if no recorded bound contains the point.
  private Fiber topmostAt(int x, int y) {
    Fiber best = null;
    int bestZ = Integer.MIN_VALUE;
    int bestSeq = Integer.MIN_VALUE;
    for (Map.Entry<Fiber, Bound> e : bounds.entrySet()) {
      Bound b = e.getValue();
      if (contains(b.rect(), x, y) && (b.z() > bestZ || (b.z() == bestZ && b.seq() > bestSeq))) {
        best = e.getKey();
        bestZ = b.z();
        bestSeq = b.seq();
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

  /// A fiber's recorded bounds plus its paint-order coordinates. `z` is the paint layer (0 = main
  /// pass, higher = later portal generations); `seq` is the paint sequence within the frame.
  private record Bound(Rect rect, int z, int seq) {}
}
