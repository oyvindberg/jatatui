package jatatui.react;

import jatatui.core.terminal.Frame;
import java.util.concurrent.atomic.AtomicBoolean;

/// Production-supported render engine for embedding jatatui-react in a host app that owns its own
/// event loop and frame cadence. The companion to [ReactApp]: same render and dispatch semantics,
/// but you call them from your loop rather than handing the loop over.
///
/// Hold a single instance for the lifetime of your app (or per screen if you fully reset between
/// screens — see [#resetState]). Per frame:
///
/// 1. From inside `terminal.draw(frame -> { ... })`: call [#render]
/// 2. After `terminal.draw` returns: dispatch any input events via [#dispatchKey] /
///    [#dispatchMouse], cycle focus via [#tab] / [#shiftTab], check [#takeDirty] for the next
///    iteration.
///
/// Example loop skeleton:
/// ```
/// Renderer r = new Renderer();
/// Element root = ...;
/// while (running) {
///   if (r.takeDirty()) terminal.draw(frame -> r.render(frame, root));
///   if (jni.poll(timeout)) {
///     Event ev = jni.read();
///     // host's own Tab / Esc / quit handling, then:
///     switch (ev) {
///       case Event.Key key when key.kind() == Press -> r.dispatchKey(toReactKey(key));
///       case Event.Mouse m -> r.dispatchMouse(toReactMouse(m));
///       case Event.Resize __ -> r.requestRerender();
///       default -> {}
///     }
///   }
/// }
/// ```
///
/// [ReactApp] is a thin wrapper around Renderer that adds the opinionated bindings: Tab cycles
/// focus, BackTab cycles backward, Ctrl-C and unhandled-Esc quit, mouse capture is enabled for
/// the duration of the loop, poll timeout 100ms. If any of those don't fit your host, use
/// [Renderer] directly.
///
/// **Threading.** [#requestRerender] is threadsafe — call it from background timers / executors
/// (this is what [jatatui.components.toast.ToastsProvider] does for auto-dismiss). [#render] and
/// the dispatch methods MUST run on a single thread (the loop thread); they mutate the hook /
/// event / focus state.
public final class Renderer {

  private final EventRegistry events;
  private final HookStore hooks;
  private final FocusManager focus;
  private final AtomicBoolean dirty;

  public Renderer() {
    this.events = new EventRegistry();
    this.hooks = new HookStore();
    this.focus = new FocusManager();
    this.dirty = new AtomicBoolean(true);
  }

  // ---- Per-frame render ----

  /// Render `root` into `frame`. Call from inside `terminal.draw(frame -> ...)`. The full per-frame
  /// pipeline runs:
  ///
  ///   1. Clear per-frame event registrations + focus candidates.
  ///   2. Build a fresh [RenderContext], record root bounds for hit-testing.
  ///   3. Recurse the Element tree, painting cells + collecting handlers.
  ///   4. Drain queued portals so they paint over the main pass.
  ///   5. Sweep unmounted hook state — runs `useEffect` cleanups for fibers that weren't visited.
  ///   6. Commit focus — promote any pending `useFocus(autoFocus=true)` candidates to focused.
  public void render(Frame frame, Element root) {
    events.clear();
    focus.clearFrame();
    java.util.Optional<String> focusedBefore = focus.currentlyFocused();
    RenderContext ctx = new RenderContext(frame, events, hooks, focus, this::requestRerender);
    events.recordBounds(Fiber.root(), frame.area());
    root.render(ctx, frame.area());
    ctx.drainPortals();
    hooks.sweep();
    focus.commit();
    // Screen-change case: the previously focused id wasn't re-registered this frame so commit
    // chose a new winner (or the previously focused element unmounted). Request a re-render so
    // the new winner is visible — this frame painted as if nothing were focused there.
    // First-render and post-blur cases are handled eagerly inside FocusManager.register, so this
    // re-request only fires when we genuinely need a second pass.
    if (!focus.currentlyFocused().equals(focusedBefore)) {
      requestRerender();
    }
  }

  // ---- Event dispatch ----

  /// Dispatch a mouse event into the most recently rendered tree. Hit-tests to the deepest fiber
  /// containing the point, then bubbles up firing `onClick` / `onScroll` handlers along the
  /// chain. Returns true if any handler fired; on true, the dirty flag is set so the host's next
  /// iteration re-renders.
  public boolean dispatchMouse(MouseEvent ev) {
    boolean fired = events.dispatchMouse(ev);
    if (fired) requestRerender();
    return fired;
  }

  /// Dispatch a key event. Bubbles from the focused fiber to root, then fires global key
  /// handlers if propagation wasn't stopped. Returns true if any handler fired.
  ///
  /// Note: Tab / Shift-Tab / BackTab are NOT focus-cycle bindings here — host code is expected
  /// to call [#tab] / [#shiftTab] explicitly. (ReactApp does this for you.)
  public boolean dispatchKey(KeyEvent ev) {
    boolean fired = events.dispatchKey(ev, focus.focusedFiber());
    if (fired) requestRerender();
    return fired;
  }

  // ---- Focus management ----

  /// Cycle focus to the next focusable fiber in render order. Wraps to the first when at the end.
  public void tab() {
    focus.tab();
    requestRerender();
  }

  /// Cycle focus to the previous focusable fiber. Wraps to the last when at the start.
  public void shiftTab() {
    focus.shiftTab();
    requestRerender();
  }

  /// The focus manager — focus by id, query the focused fiber, etc. Same instance across renders.
  public FocusManager focus() {
    return focus;
  }

  /// The event registry — same instance across renders. Useful for advanced consumers (testing
  /// dispatch directly, registering host-wide handlers outside the Element tree).
  public EventRegistry events() {
    return events;
  }

  // ---- Dirty tracking ----

  /// Mark a re-render pending. Threadsafe. Internal callbacks (`useState.set`, dispatchMouse /
  /// dispatchKey after a handler fires, [#tab] / [#shiftTab]) already call this — you mostly
  /// need it for host-driven repaints (window resize, app-level state changed, timer ticks).
  public void requestRerender() {
    dirty.set(true);
  }

  /// True if a re-render is pending.
  public boolean isDirty() {
    return dirty.get();
  }

  /// Clear the dirty flag.
  public void clearDirty() {
    dirty.set(false);
  }

  /// Atomic test-and-clear. The idiomatic loop body:
  /// `if (r.takeDirty()) terminal.draw(frame -> r.render(frame, root));`
  public boolean takeDirty() {
    return dirty.getAndSet(false);
  }

  // ---- Lifecycle ----

  /// Drop all hook state — `useState`, `useRef`, `useEffect` deps + cleanups, `memo` /
  /// `pureComponent` caches. Cleanups are invoked in arbitrary order.
  ///
  /// Use when the host swaps the entire component tree (router-style host that fully unmounts +
  /// mounts a new screen and wants to release everything). Sets dirty so the next iteration
  /// renders fresh.
  public void resetState() {
    hooks.cleanups.values().forEach(Runnable::run);
    hooks.values.clear();
    hooks.deps.clear();
    hooks.cleanups.clear();
    hooks.memoCache.clear();
    hooks.touched.clear();
    requestRerender();
  }

  // ---- Same-package internals (for ReactApp / TestHarness) ----

  HookStore hooks() {
    return hooks;
  }
}
