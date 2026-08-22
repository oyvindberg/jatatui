package jatatui.react;

import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.layout.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyModifiers;

/// Direct unit tests of [EventRegistry] dispatch — builds fake fiber trees, registers handlers,
/// dispatches events, asserts who fires.
class EventRegistryDispatchTest {

  // ===== Key bubbling =====

  @Test
  void key_fires_at_focused_fiber() {
    EventRegistry r = new EventRegistry();
    Fiber a = Fiber.root().child(0);
    List<String> log = new ArrayList<>();
    r.addKey(a, new KeyCode.Char('a'), e -> log.add("a"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    boolean fired = r.dispatchKey(ev, Optional.of(a));

    assertTrue(fired);
    assertEquals(List.of("a"), log);
  }

  @Test
  void key_bubbles_from_focused_through_ancestors_to_root() {
    EventRegistry r = new EventRegistry();
    Fiber outer = Fiber.root().child(0);
    Fiber middle = outer.child(0);
    Fiber inner = middle.child(0);

    List<String> log = new ArrayList<>();
    r.addKey(inner, new KeyCode.Char('a'), e -> log.add("inner"));
    r.addKey(middle, new KeyCode.Char('a'), e -> log.add("middle"));
    r.addKey(outer, new KeyCode.Char('a'), e -> log.add("outer"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    boolean fired = r.dispatchKey(ev, Optional.of(inner));

    assertTrue(fired);
    assertEquals(List.of("inner", "middle", "outer"), log, "must fire deepest-first");
  }

  @Test
  void key_skips_uninterested_ancestors() {
    EventRegistry r = new EventRegistry();
    Fiber outer = Fiber.root().child(0);
    Fiber middle = outer.child(0);
    Fiber inner = middle.child(0);

    List<String> log = new ArrayList<>();
    r.addKey(inner, new KeyCode.Char('a'), e -> log.add("inner-a"));
    r.addKey(middle, new KeyCode.Char('b'), e -> log.add("middle-b")); // wrong key
    r.addKey(outer, new KeyCode.Char('a'), e -> log.add("outer-a"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    r.dispatchKey(ev, Optional.of(inner));

    assertEquals(List.of("inner-a", "outer-a"), log);
  }

  @Test
  void key_stopPropagation_halts_bubble() {
    EventRegistry r = new EventRegistry();
    Fiber outer = Fiber.root().child(0);
    Fiber middle = outer.child(0);
    Fiber inner = middle.child(0);

    List<String> log = new ArrayList<>();
    r.addKey(
        inner,
        new KeyCode.Char('a'),
        e -> {
          log.add("inner");
          e.stopPropagation();
        });
    r.addKey(middle, new KeyCode.Char('a'), e -> log.add("middle"));
    r.addKey(outer, new KeyCode.Char('a'), e -> log.add("outer"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    r.dispatchKey(ev, Optional.of(inner));

    assertEquals(List.of("inner"), log);
  }

  @Test
  void global_key_fires_after_bubble_chain() {
    EventRegistry r = new EventRegistry();
    Fiber outer = Fiber.root().child(0);
    Fiber inner = outer.child(0);

    List<String> log = new ArrayList<>();
    r.addKey(inner, new KeyCode.Char('a'), e -> log.add("inner"));
    r.addKey(outer, new KeyCode.Char('a'), e -> log.add("outer"));
    r.addGlobalKey(new KeyCode.Char('a'), e -> log.add("global"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    r.dispatchKey(ev, Optional.of(inner));

    assertEquals(List.of("inner", "outer", "global"), log);
  }

  @Test
  void global_key_skipped_when_bubble_chain_stops_propagation() {
    EventRegistry r = new EventRegistry();
    Fiber outer = Fiber.root().child(0);

    List<String> log = new ArrayList<>();
    r.addKey(
        outer,
        new KeyCode.Char('q'),
        e -> {
          log.add("outer");
          e.stopPropagation();
        });
    r.addGlobalKey(new KeyCode.Char('q'), e -> log.add("global"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('q'), new KeyModifiers(0));
    r.dispatchKey(ev, Optional.of(outer));

    assertEquals(List.of("outer"), log);
  }

  @Test
  void global_key_fires_with_no_focused_fiber() {
    EventRegistry r = new EventRegistry();

    List<String> log = new ArrayList<>();
    r.addGlobalKey(new KeyCode.Char('q'), e -> log.add("global"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('q'), new KeyModifiers(0));
    r.dispatchKey(ev, Optional.empty());

    assertEquals(List.of("global"), log);
  }

  // ===== Mouse bubbling =====

  @Test
  void click_dispatches_to_deepest_fiber_at_point_then_bubbles() {
    EventRegistry r = new EventRegistry();
    Fiber outer = Fiber.root().child(0);
    Fiber inner = outer.child(0);

    Rect outerArea = new Rect(0, 0, 20, 10);
    Rect innerArea = new Rect(5, 3, 5, 4);

    r.recordBounds(outer, outerArea);
    r.recordBounds(inner, innerArea);

    List<String> log = new ArrayList<>();
    r.addClick(outer, outerArea, e -> log.add("outer"));
    r.addClick(inner, innerArea, e -> log.add("inner"));

    MouseEvent ev = new MouseEvent(7, 5, new KeyModifiers(0), MouseEvent.Kind.DOWN);
    boolean fired = r.dispatchMouse(ev);

    assertTrue(fired);
    assertEquals(List.of("inner", "outer"), log, "deepest first, then bubble");
  }

  @Test
  void click_outside_inner_only_fires_outer() {
    EventRegistry r = new EventRegistry();
    Fiber outer = Fiber.root().child(0);
    Fiber inner = outer.child(0);

    Rect outerArea = new Rect(0, 0, 20, 10);
    Rect innerArea = new Rect(5, 3, 5, 4);

    r.recordBounds(outer, outerArea);
    r.recordBounds(inner, innerArea);

    List<String> log = new ArrayList<>();
    r.addClick(outer, outerArea, e -> log.add("outer"));
    r.addClick(inner, innerArea, e -> log.add("inner"));

    // Click at (15, 8) — inside outer but outside inner.
    MouseEvent ev = new MouseEvent(15, 8, new KeyModifiers(0), MouseEvent.Kind.DOWN);
    r.dispatchMouse(ev);

    assertEquals(List.of("outer"), log);
  }

  @Test
  void click_stopPropagation_halts_bubble() {
    EventRegistry r = new EventRegistry();
    Fiber outer = Fiber.root().child(0);
    Fiber inner = outer.child(0);

    Rect a = new Rect(0, 0, 20, 10);
    r.recordBounds(outer, a);
    r.recordBounds(inner, a);

    List<String> log = new ArrayList<>();
    r.addClick(
        inner,
        a,
        e -> {
          log.add("inner");
          e.stopPropagation();
        });
    r.addClick(outer, a, e -> log.add("outer"));

    MouseEvent ev = new MouseEvent(5, 5, new KeyModifiers(0), MouseEvent.Kind.DOWN);
    r.dispatchMouse(ev);

    assertEquals(List.of("inner"), log);
  }

  @Test
  void scroll_dispatches_to_scroll_bucket_not_click() {
    EventRegistry r = new EventRegistry();
    Fiber f = Fiber.root().child(0);
    Rect a = new Rect(0, 0, 20, 10);
    r.recordBounds(f, a);

    List<String> log = new ArrayList<>();
    r.addClick(f, a, e -> log.add("click"));
    r.addScroll(f, a, e -> log.add("scroll-" + e.kind()));

    MouseEvent ev = new MouseEvent(5, 5, new KeyModifiers(0), MouseEvent.Kind.SCROLL_UP);
    r.dispatchMouse(ev);

    assertEquals(List.of("scroll-SCROLL_UP"), log);
  }

  @Test
  void click_returns_false_when_no_handler_matches() {
    EventRegistry r = new EventRegistry();
    Fiber f = Fiber.root().child(0);
    Rect a = new Rect(0, 0, 20, 10);
    r.recordBounds(f, a);

    MouseEvent ev = new MouseEvent(100, 100, new KeyModifiers(0), MouseEvent.Kind.DOWN);
    boolean fired = r.dispatchMouse(ev);

    assertFalse(fired);
  }

  // Regression: globalKeys path used `h.matcher.equals(ev.code())` directly instead of going
  // through `matches(...)`, so Predicate-based matchers (ANY_KEY, ANY_CHAR, ad-hoc preds) would
  // silently never fire as global handlers — Predicate.equals(KeyCode) is always false.
  @Test
  void global_handler_with_predicate_matcher_fires() {
    EventRegistry r = new EventRegistry();
    List<String> log = new ArrayList<>();
    java.util.function.Predicate<KeyCode> anyKey = code -> true;
    r.addGlobalKey(anyKey, e -> log.add("global"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('z'), new KeyModifiers(0));
    boolean fired = r.dispatchKey(ev, Optional.empty());

    assertTrue(fired, "global Predicate matcher must fire");
    assertEquals(List.of("global"), log);
  }

  @Test
  void global_handler_with_keycode_matcher_still_fires() {
    EventRegistry r = new EventRegistry();
    List<String> log = new ArrayList<>();
    r.addGlobalKey(new KeyCode.Char('q'), e -> log.add("q"));

    KeyEvent ev = new KeyEvent(new KeyCode.Char('q'), new KeyModifiers(0));
    boolean fired = r.dispatchKey(ev, Optional.empty());

    assertTrue(fired);
    assertEquals(List.of("q"), log);
  }

  @Test
  void clear_drops_all_state() {
    EventRegistry r = new EventRegistry();
    Fiber f = Fiber.root().child(0);
    Rect a = new Rect(0, 0, 20, 10);
    r.recordBounds(f, a);
    List<String> log = new ArrayList<>();
    r.addKey(f, new KeyCode.Char('a'), e -> log.add("a"));

    r.clear();

    KeyEvent kev = new KeyEvent(new KeyCode.Char('a'), new KeyModifiers(0));
    boolean fired = r.dispatchKey(kev, Optional.of(f));
    assertFalse(fired);
    assertTrue(log.isEmpty());
  }
}
