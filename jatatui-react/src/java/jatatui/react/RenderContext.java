package jatatui.react;

import jatatui.core.buffer.Buffer;
import jatatui.core.layout.Rect;
import jatatui.core.terminal.Frame;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Threaded through every Element's render call. Owns:
///   - the current [Frame] (and via it, the buffer)
///   - the per-frame [EventRegistry] for click / key / focus handlers
///   - the persistent [HookStore] across frames
///   - the current [Fiber] (component identity for hook lookup)
///   - the [FocusManager] so [#useFocus] can register focusables in render order
public final class RenderContext {
  final Frame frame;
  final EventRegistry events;
  final HookStore hooks;
  final FocusManager focus;
  final Runnable requestRerender;
  /// Per-frame context stacks. A [Context] maps to a stack of values; the top of the stack is
  /// what [#useContext] returns. Push/pop is balanced by the Provider intrinsic — see
  /// [Intrinsics#PROVIDER].
  final Map<Context<?>, Deque<Object>> contextStacks = new HashMap<>();
  /// Per-frame portal queue. Each entry captures the declaring fiber + child Element + absolute
  /// area; entries are drained AFTER the main render pass so portals paint on top of everything
  /// else. See [#queuePortal] and [#drainPortals].
  final java.util.List<PortalEntry> portals = new java.util.ArrayList<>();
  Fiber fiber;
  int hookIndex;
  /// The paint layer bounds are recorded at. 0 during the main pass; bumped per portal generation
  /// in [#drainPortals] so portals hit-test above the content they overlap. See [EventRegistry].
  int currentZ = 0;
  /// The highest `hz` requested by any [#useFrame] mounted this render, or 0 if none. Read by
  /// [Renderer] after render to drive (or stop) the frame tick source at the right rate.
  int frameMaxHz = 0;

  record PortalEntry(Fiber declaringFiber, int seq, Element child, Rect area) {}

  RenderContext(
      Frame frame,
      EventRegistry events,
      HookStore hooks,
      FocusManager focus,
      Runnable requestRerender) {
    this.frame = frame;
    this.events = events;
    this.hooks = hooks;
    this.focus = focus;
    this.requestRerender = requestRerender;
    this.fiber = Fiber.root();
    this.hookIndex = 0;
    // Mark root as touched so any hooks declared by the top-level component survive sweep.
    // Symmetric with `renderChild`, which adds each child fiber to touched.
    hooks.touched.add(this.fiber);
  }

  public Frame frame() {
    return frame;
  }

  public Buffer buffer() {
    return frame.bufferMut();
  }

  /// Trigger a re-render on the next loop tick. Threadsafe — can be called from background
  /// timers / callbacks. The actual re-render runs on the main loop thread, so component code
  /// remains single-threaded. Components like [jatatui.components.toast.ToastsProvider] use this
  /// to dismiss expired entries even when the user isn't interacting.
  public Runnable requestRerender() {
    return requestRerender;
  }

  // ---- Composition ----

  /// Render a child Element with a fresh fiber id derived from the parent's. Use the indexed
  /// overload for stable-order children; use the keyed overload to preserve hook state when
  /// children can reorder (analogue of React's `key="..."`).
  public void renderChild(int index, Element child, Rect area) {
    Fiber prev = fiber;
    int prevHook = hookIndex;
    fiber = prev.child(index);
    hookIndex = 0;
    hooks.touched.add(fiber);
    events.recordBounds(fiber, area, currentZ);
    reconcile(fiber, child);
    try {
      child.render(this, area);
    } finally {
      fiber = prev;
      hookIndex = prevHook;
    }
  }

  public void renderChild(String key, Element child, Rect area) {
    Fiber prev = fiber;
    int prevHook = hookIndex;
    fiber = prev.child(key);
    hookIndex = 0;
    hooks.touched.add(fiber);
    events.recordBounds(fiber, area, currentZ);
    reconcile(fiber, child);
    try {
      child.render(this, area);
    } finally {
      fiber = prev;
      hookIndex = prevHook;
    }
  }

  /// Reconciliation: when the type at `fiber` changes between frames (e.g. a Router swaps
  /// SourceEditor for OutputEditor at the same fiber slot), the old subtree is unmounted —
  /// state dropped, cleanups run — before the new one renders. Mirrors React's "different
  /// element type at same fiber → unmount + remount" rule.
  ///
  /// "Type" means the [Component] reference for [Element.Of], the `Element.Host` class for
  /// host leaves, and recursively the child's type for the transparent [Element.Sized].
  /// `component(...)`-wrapped functions all share the FUNCTION Component reference, so we
  /// special-case them and use the body lambda's class identity (different `component(...)`
  /// call sites produce different anonymous-lambda classes; same call site produces the same
  /// class even across renders).
  private void reconcile(Fiber f, Element child) {
    Object newType = typeOf(child);
    Object oldType = hooks.elementTypes.get(f);
    if (oldType != null && !oldType.equals(newType)) {
      hooks.unmount(f);
    }
    hooks.elementTypes.put(f, newType);
  }

  private static Object typeOf(Element e) {
    if (e instanceof Element.Of<?> of) {
      if (of.type() == Intrinsics.FUNCTION) {
        Intrinsics.FunctionProps fp = (Intrinsics.FunctionProps) of.props();
        return fp.body().getClass();
      }
      return of.type();
    }
    if (e instanceof Element.Sized s) {
      return typeOf(s.child());
    }
    return e.getClass();
  }

  /// Apply an [Element.Of] — runs the component body unconditionally. Memoization is opt-in via
  /// [Components#memo] / [Components#pureComponent], which use a separate per-Fiber memo cache.
  ///
  /// Auto-memoizing every Element.Of would skip the body, but handler registrations
  /// (`onClick` / `onKey` / `useFocus`) are SIDE EFFECTS of body execution rather than data in
  /// the returned tree — skipping the body silently drops them. For now, body always runs.
  /// Intrinsics like text/box/column are cheap; user components opt into memo explicitly.
  @SuppressWarnings({"unchecked", "rawtypes"})
  Element applyAndMemo(Element.Of<?> of) {
    return ((Component) of.type()).apply(of.props(), this);
  }

  // ---- Hooks ----

  public <T> State<T> useState(Supplier<T> initial) {
    HookKey key = new HookKey(fiber, hookIndex++);
    @SuppressWarnings("unchecked")
    T current = (T) hooks.values.computeIfAbsent(key, k -> initial.get());
    return new State<>(this, key, current);
  }

  public <T> Ref<T> useRef(Supplier<T> initial) {
    HookKey key = new HookKey(fiber, hookIndex++);
    @SuppressWarnings("unchecked")
    T current = (T) hooks.values.computeIfAbsent(key, k -> initial.get());
    return new Ref<>(hooks, key, current);
  }

  /// `useEffect` — runs `effect` after render if the dependency array changed (or on first render).
  /// Cleanup runs before the next effect, AND on unmount (when this fiber isn't touched in the
  /// next render — see [HookStore#sweep]).
  public void useEffect(Runnable effect, Object... deps) {
    HookKey key = new HookKey(fiber, hookIndex++);
    Object[] prev = hooks.deps.get(key);
    boolean changed = prev == null || !arraysEqual(prev, deps);
    if (changed) {
      Runnable cleanup = hooks.cleanups.remove(key);
      if (cleanup != null) cleanup.run();
      effect.run();
      hooks.deps.put(key, deps);
    }
  }

  /// Fire `callback` once on the render thread, `delayMs` after the component mounts (or after
  /// `deps` change, if supplied). Cleaned up automatically on unmount — if the component is
  /// unmounted before the timer fires, the callback never runs.
  ///
  /// Implementation: a shared daemon `ScheduledExecutorService` triggers
  /// [#requestRerender] at the right time; the callback itself runs synchronously inside this
  /// `useTimeout` call on the next render. So `state.set` / `router.push` / etc. from inside
  /// the callback are safe — they execute on the loop thread, not on the executor thread.
  ///
  /// `deps`: same convention as [#useEffect]. Omitted or empty = "once on mount". Changed deps
  /// cancel the in-flight timer and re-arm with a fresh delay.
  public void useTimeout(long delayMs, Runnable callback, Object... deps) {
    HookKey key = new HookKey(fiber, hookIndex++);
    Object[] prev = hooks.deps.get(key);
    boolean changed = prev == null || !arraysEqual(prev, deps);

    TimeoutHookState state;
    if (changed) {
      Runnable cleanup = hooks.cleanups.remove(key);
      if (cleanup != null) cleanup.run();
      state = new TimeoutHookState();
      state.fireAt = System.currentTimeMillis() + delayMs;
      state.fired = false;
      state.future =
          TIMEOUT_SCHEDULER.schedule(
              requestRerender, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
      hooks.values.put(key, state);
      final TimeoutHookState captured = state;
      hooks.cleanups.put(
          key,
          () -> {
            if (captured.future != null) captured.future.cancel(false);
          });
      hooks.deps.put(key, deps);
    } else {
      state = (TimeoutHookState) hooks.values.get(key);
    }

    if (!state.fired && System.currentTimeMillis() >= state.fireAt) {
      state.fired = true;
      callback.run();
    }
  }

  /// Mutable hook-store entry for [#useTimeout].
  static final class TimeoutHookState {
    long fireAt;
    boolean fired;
    java.util.concurrent.ScheduledFuture<?> future;
  }

  /// Animation frame hook — the declarative alternative to a hand-rolled render loop. Drives
  /// `onTick` at up to `hz` frames per second, passing the elapsed milliseconds since this hook's
  /// previous tick (so animation math is frame-rate independent — advance by `elapsed`, not a
  /// fixed step).
  ///
  /// Mechanics, mirroring [#useTimeout] but self-re-arming:
  ///   - The hook contributes its `hz` to this render's [#frameMaxHz]. After render, [Renderer]
  ///     runs a single shared tick source at the highest `hz` any mounted `useFrame` asked for,
  ///     which [#requestRerender]s at that cadence. While a frame source is live, [ReactApp]
  ///     shortens its input poll so those re-renders are picked up promptly (~60fps-capable).
  ///   - `onTick` runs synchronously here, on the render thread, at the next render — exactly like
  ///     `useTimeout`'s callback. So `state.set(...)` from inside `onTick` is safe.
  ///   - A tick fires only once this hook's own `1000/hz` ms period has elapsed since its last
  ///     tick, so a slow (e.g. 10hz) hook coexisting with a fast (60hz) one still ticks at its own
  ///     rate even though the shared source runs at 60hz.
  ///   - Auto-cancels on unmount: an unmounted fiber stops contributing to [#frameMaxHz], so the
  ///     tick source winds down (or stops) automatically; its hook state is swept.
  ///
  /// The first render after mount does not tick — it establishes the baseline timestamp; the first
  /// `onTick` arrives one period later.
  public void useFrame(int hz, Consumer<Long> onTick) {
    if (hz <= 0) throw new IllegalArgumentException("useFrame hz must be positive, got " + hz);
    HookKey key = new HookKey(fiber, hookIndex++);
    FrameHookState state = (FrameHookState) hooks.values.get(key);
    long now = System.currentTimeMillis();
    if (state == null) {
      state = new FrameHookState();
      state.lastTickMs = now;
      hooks.values.put(key, state);
    }

    // Advertise our rate so Renderer runs the shared tick source fast enough for us.
    frameMaxHz = Math.max(frameMaxHz, hz);

    long periodMs = Math.max(1L, 1000L / hz);
    long elapsed = now - state.lastTickMs;
    if (elapsed >= periodMs) {
      state.lastTickMs = now;
      onTick.accept(elapsed);
    }
  }

  /// Mutable hook-store entry for [#useFrame]. Tracks the timestamp of this hook's last tick so
  /// each hook can pace itself independently of the shared tick source's rate.
  static final class FrameHookState {
    long lastTickMs;
  }

  /// Shared daemon executor for [#useTimeout]. One thread per process, regardless of how many
  /// hooks are armed. Spawning a thread per timer would not scale; this matches what
  /// [jatatui.components.toast.ToastsProvider] already does for its own timer needs.
  private static final java.util.concurrent.ScheduledExecutorService TIMEOUT_SCHEDULER =
      java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "jatatui-react-timeouts");
            t.setDaemon(true);
            return t;
          });

  // ---- Portal ----

  /// Queue a portal for deferred rendering after the main pass. Called by the Portal intrinsic;
  /// user code uses [Components#portal]. The portal will render as a child of the declaring
  /// fiber (so events bubble through that parent chain), at the given absolute area.
  public void queuePortal(Element child, Rect area) {
    portals.add(new PortalEntry(fiber, portals.size(), child, area));
  }

  /// Render all queued portals in declaration order. Each portal renders as a `renderChild` of
  /// its declaring fiber, with a stable sequential key so its hooks survive across renders.
  /// Recursively drains portals queued by portal children (last-wins z-order). Called by
  /// [ReactApp] after the main render pass.
  void drainPortals() {
    int generation = currentZ;
    while (!portals.isEmpty()) {
      // Each generation of portals paints above the previous one, so bounds it records hit-test
      // above everything painted so far. Portals queued *by* this batch drain in the next
      // iteration at a still-higher layer (nested overlays stack).
      generation++;
      currentZ = generation;
      java.util.List<PortalEntry> batch = new java.util.ArrayList<>(portals);
      portals.clear();
      for (PortalEntry pe : batch) {
        Fiber prev = fiber;
        int prevHook = hookIndex;
        fiber = pe.declaringFiber();
        try {
          renderChild("portal:" + pe.seq(), pe.child(), pe.area());
        } finally {
          fiber = prev;
          hookIndex = prevHook;
        }
      }
    }
    currentZ = 0;
  }

  /// Read the value of `context` from the nearest enclosing provider in the Element tree, or
  /// [Context#defaultValue] if no provider is in scope. See [Components#provide].
  @SuppressWarnings("unchecked")
  public <T> T useContext(Context<T> context) {
    Deque<Object> stack = contextStacks.get(context);
    if (stack == null || stack.isEmpty()) return context.defaultValue;
    return (T) stack.peek();
  }

  /// Push a context value. Called by the Provider intrinsic — user code should use
  /// [Components#provide] instead.
  <T> void pushContext(Context<T> context, T value) {
    contextStacks.computeIfAbsent(context, c -> new ArrayDeque<>()).push(value);
  }

  /// Pop the top context value. Called by the Provider intrinsic on its render-end. Must be
  /// balanced with [#pushContext].
  void popContext(Context<?> context) {
    Deque<Object> stack = contextStacks.get(context);
    if (stack != null && !stack.isEmpty()) stack.pop();
  }

  /// Ink-style focus. Returns true when this fiber currently holds focus.
  public boolean useFocus(Optional<String> id, boolean autoFocus) {
    String fid = id.orElse("fiber:" + fiber.hashCode());
    focus.register(fid, fiber, autoFocus);
    return focus.isFocused(fid);
  }

  /// Imperatively move focus to the focusable with this `id`. Use from event handlers (click,
  /// validation-failed callbacks, list-row selection — anywhere you need to focus a specific
  /// component without going through Tab cycling). Pairs with [#useFocus]'s explicit `id`.
  ///
  /// Effective on the next render: focus state is read by `useFocus` during render, and this
  /// just stages the new focused id.
  public void focus(String id) {
    focus.focus(id);
    requestRerender.run();
  }

  /// Imperatively clear focus. Next render's `useFocus` calls all return false until something
  /// claims focus again (Tab cycling, autoFocus, or [#focus(String)]).
  public void blur() {
    focus.blur();
    requestRerender.run();
  }

  // ---- Event registration ----

  /// The bounds for this Component's fiber, recorded by the parent's `renderChild` call. Returns
  /// empty when called before render (which shouldn't happen — every Component runs inside a
  /// fiber with bounds set).
  public Optional<Rect> area() {
    return events.boundsOf(fiber);
  }

  /// Register a click handler. Fires on mouse-down inside `area`. Bubbles up the fiber tree
  /// (deepest registering fiber gets it first, then ancestors). Use [MouseEvent#stopPropagation]
  /// to prevent ancestors from also receiving it.
  public void onClick(Rect area, Consumer<MouseEvent> handler) {
    events.addClick(fiber, area, handler);
  }

  /// Convenience: register a click handler for the whole area of this Component. Equivalent to
  /// `area().ifPresent(r -> onClick(r, handler))`.
  public void onClick(Consumer<MouseEvent> handler) {
    events.boundsOf(fiber).ifPresent(r -> events.addClick(fiber, r, handler));
  }

  /// Convenience overload: handler ignores the [MouseEvent].
  public void onClick(Rect area, Runnable handler) {
    events.addClick(fiber, area, e -> handler.run());
  }

  public void onClick(Runnable handler) {
    events.boundsOf(fiber).ifPresent(r -> events.addClick(fiber, r, e -> handler.run()));
  }

  /// Register a scroll handler. Fires on mouse-wheel events inside `area`. Distinguish direction
  /// via the [MouseEvent#kind] (`SCROLL_UP` / `SCROLL_DOWN`). Bubbles like click.
  public void onScroll(Rect area, Consumer<MouseEvent> handler) {
    events.addScroll(fiber, area, handler);
  }

  public void onScroll(Consumer<MouseEvent> handler) {
    events.boundsOf(fiber).ifPresent(r -> events.addScroll(fiber, r, handler));
  }

  /// Register a hover handler. Fires on mouse-move (`MOVE`) over `area` — i.e. whenever the
  /// pointer, not pressing any button, is over this widget. Route it to `useState` to drive a
  /// hover-highlight. Bubbles like click; the topmost widget under the pointer gets it first.
  ///
  /// Hover is delivered per move, not as enter/leave edges: on each move the widget under the
  /// pointer is notified. Track "currently hovered" yourself (set state on hover, and the next
  /// move over a different widget updates it).
  public void onHover(Rect area, Consumer<MouseEvent> handler) {
    events.addHover(fiber, area, handler);
  }

  public void onHover(Consumer<MouseEvent> handler) {
    events.boundsOf(fiber).ifPresent(r -> events.addHover(fiber, r, handler));
  }

  public void onHover(Rect area, Runnable handler) {
    events.addHover(fiber, area, e -> handler.run());
  }

  public void onHover(Runnable handler) {
    events.boundsOf(fiber).ifPresent(r -> events.addHover(fiber, r, e -> handler.run()));
  }

  /// Register a drag handler. Fires on `DRAG` (pointer moved with a button held) over `area`. Read
  /// [MouseEvent#x] / [MouseEvent#y] to track the pointer — this is what scrubs a timeline or
  /// drags a splitter. Bubbles like click.
  ///
  /// Drag is area-scoped like every other mouse handler: it fires for drags whose current point is
  /// inside `area`. For a scrub/resize interaction that must keep tracking when the pointer strays,
  /// register the handler on the enclosing panel's area (via the no-arg overload) rather than on a
  /// narrow handle, so the whole panel receives the drag.
  public void onDrag(Rect area, Consumer<MouseEvent> handler) {
    events.addDrag(fiber, area, handler);
  }

  public void onDrag(Consumer<MouseEvent> handler) {
    events.boundsOf(fiber).ifPresent(r -> events.addDrag(fiber, r, handler));
  }

  /// Register a drag-end handler. Fires on `UP` (button released) over `area` — the natural place
  /// to commit a scrub/resize or clear a "dragging" flag. Bubbles like click.
  public void onDragEnd(Rect area, Consumer<MouseEvent> handler) {
    events.addDragEnd(fiber, area, handler);
  }

  public void onDragEnd(Consumer<MouseEvent> handler) {
    events.boundsOf(fiber).ifPresent(r -> events.addDragEnd(fiber, r, handler));
  }

  public void onDragEnd(Rect area, Runnable handler) {
    events.addDragEnd(fiber, area, e -> handler.run());
  }

  public void onDragEnd(Runnable handler) {
    events.boundsOf(fiber).ifPresent(r -> events.addDragEnd(fiber, r, e -> handler.run()));
  }

  /// Register a key handler. Fires when this fiber (or one of its descendants) is focused AND the
  /// matcher accepts the key. Bubbles up the focus chain to root.
  public void onKey(Object keyMatcher, Consumer<KeyEvent> handler) {
    events.addKey(fiber, keyMatcher, handler);
  }

  /// Convenience overload: handler ignores the [KeyEvent].
  public void onKey(Object keyMatcher, Runnable handler) {
    events.addKey(fiber, keyMatcher, e -> handler.run());
  }

  /// Register a global key handler — fires AFTER the focus-chain bubble, regardless of focus.
  /// Use for app-wide shortcuts like Ctrl-Q. To intercept first, register at the root component
  /// (which is at the top of every focus chain).
  public void onGlobalKey(Object keyMatcher, Consumer<KeyEvent> handler) {
    events.addGlobalKey(keyMatcher, handler);
  }

  public void onGlobalKey(Object keyMatcher, Runnable handler) {
    events.addGlobalKey(keyMatcher, e -> handler.run());
  }

  // ---- Internals ----

  private static boolean arraysEqual(Object[] a, Object[] b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
      if (!java.util.Objects.equals(a[i], b[i])) return false;
    }
    return true;
  }
}
