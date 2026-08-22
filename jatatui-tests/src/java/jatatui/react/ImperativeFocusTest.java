package jatatui.react;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ImperativeFocusTest {

  /// `ctx.focus(id)` from inside a render body changes which focusable is focused on the next
  /// render. (Mirrors the real-world pattern: a click handler / validation callback / list-row
  /// selector imperatively focuses something specific.)
  @Test
  void focus_id_takes_effect_next_render() throws IOException {
    TestHarness h = new TestHarness(40, 12);

    AtomicReference<RenderContext> ctxRef = new AtomicReference<>();
    List<Boolean> flags = new ArrayList<>();

    Element app =
        component(
            ctx -> {
              ctxRef.set(ctx);
              flags.clear();
              flags.add(ctx.useFocus(Optional.of("a"), true));
              flags.add(ctx.useFocus(Optional.of("b"), false));
              flags.add(ctx.useFocus(Optional.of("c"), false));
              return text("3 focusables");
            });

    // Two renders: first registers, second sees autoFocus committed.
    h.render(app);
    h.render(app);
    assertEquals(List.of(true, false, false), flags);

    // Imperatively focus "c".
    ctxRef.get().focus("c");
    h.render(app);
    assertEquals(List.of(false, false, true), flags);

    // Imperatively focus "b".
    ctxRef.get().focus("b");
    h.render(app);
    assertEquals(List.of(false, true, false), flags);
  }

  /// `blur()` is transient — it drops the current focus, but the next render's autoFocus
  /// candidate (if any) reclaims same-frame thanks to eager-claim. Without an autoFocus
  /// candidate, nothing reclaims and the field stays unfocused.
  @Test
  void blur_clears_focus_until_next_autofocus_claim() throws IOException {
    TestHarness h = new TestHarness(40, 12);
    AtomicReference<RenderContext> ctxRef = new AtomicReference<>();
    List<Boolean> flags = new ArrayList<>();

    Element app =
        component(
            ctx -> {
              ctxRef.set(ctx);
              flags.clear();
              // autoFocus=false — nothing should reclaim after blur.
              flags.add(ctx.useFocus(Optional.of("a"), false));
              return text("one");
            });

    // Force-focus "a" first so it's the active focus.
    h.render(app);
    ctxRef.get().focus("a");
    h.render(app);
    assertEquals(List.of(true), flags);

    ctxRef.get().blur();
    h.render(app);
    assertEquals(List.of(false), flags, "blur clears focus and nothing reclaims");
  }

  /// First-frame autoFocus: the eager-claim in FocusManager.register makes useFocus return
  /// true on the SAME frame as the autoFocus registration — no need for a second render pass
  /// to make the focused element visible.
  @Test
  void auto_focus_visible_on_first_frame() throws IOException {
    TestHarness h = new TestHarness(40, 12);
    List<Boolean> flags = new ArrayList<>();

    Element app =
        component(
            ctx -> {
              flags.clear();
              flags.add(ctx.useFocus(Optional.of("a"), true));
              flags.add(ctx.useFocus(Optional.of("b"), false));
              flags.add(ctx.useFocus(Optional.of("c"), false));
              return text("3 focusables");
            });

    h.render(app);
    assertEquals(
        List.of(true, false, false),
        flags,
        "first autoFocus is focused on the SAME frame; no second render needed");
  }

  /// Screen-change case: when the previously focused id isn't re-registered (e.g. router
  /// pushes a different screen), commit picks a new winner AFTER render, so the first paint of
  /// the new screen shows nothing focused. Renderer requests a re-render so the host's next
  /// loop tick paints with the new focus visible.
  @Test
  void screen_change_requests_rerender_so_new_focus_paints_next_tick() throws IOException {
    TestHarness h = new TestHarness(40, 12);

    Element screenA =
        component(
            ctx -> {
              ctx.useFocus(Optional.of("A"), true);
              return text("A");
            });
    Element screenB =
        component(
            ctx -> {
              ctx.useFocus(Optional.of("B"), true);
              return text("B");
            });

    java.util.concurrent.atomic.AtomicBoolean showA =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    Element wrapper = component(ctx -> showA.get() ? screenA : screenB);

    h.render(wrapper);
    h.renderer.clearDirty();
    assertEquals(Optional.of("A"), h.focus.currentlyFocused());

    // Swap to screen B. focused still points at "A" going INTO this render. B registers; the
    // eager-claim only fires when focused.isEmpty(), so it doesn't fire here. After commit,
    // "A" is no longer registered → commit picks "B". Renderer notices the change and flips
    // dirty — host's next iteration re-renders with focus visible.
    showA.set(false);
    h.render(wrapper);
    assertTrue(h.renderer.isDirty(), "commit changed focus → request re-render");
    assertEquals(Optional.of("B"), h.focus.currentlyFocused());
  }

  /// `ctx.focus(id)` from a click handler — the canonical "click to focus" pattern.
  @Test
  void click_handler_can_focus_other_field() throws IOException {
    TestHarness h = new TestHarness(40, 12);

    List<Boolean> flags = new ArrayList<>();

    Element app =
        component(
            ctx -> {
              flags.clear();
              flags.add(ctx.useFocus(Optional.of("a"), true));
              boolean bFocused = ctx.useFocus(Optional.of("b"), false);
              flags.add(bFocused);
              ctx.onClick(e -> ctx.focus("b"));
              return text("clickable-area");
            });

    h.render(app);
    h.render(app);
    assertEquals(List.of(true, false), flags);

    // Click anywhere — handler calls ctx.focus("b").
    h.events.dispatchMouse(
        new MouseEvent(2, 0, new tui.crossterm.KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);
    assertEquals(List.of(false, true), flags);
  }
}
