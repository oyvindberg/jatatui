package jatatui.components.dropdown;

import static jatatui.components.Components.dropdown;
import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.layout.Margin;
import jatatui.react.Element;
import jatatui.react.MouseEvent;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyModifiers;

class DropdownTest {

  /// Open the dropdown by clicking the trigger; click an option row; the selection commits and
  /// the dropdown closes. Without per-row click handlers, the only way to commit was Enter,
  /// which made the dropdown effectively keyboard-only.
  @Test
  void click_on_option_row_commits_selection_and_closes() throws IOException {
    AtomicInteger selected = new AtomicInteger(0);
    AtomicInteger changeCount = new AtomicInteger(0);

    Element app =
        column(
                length(
                    3,
                    dropdown(
                        "Color",
                        List.of("red", "green", "blue", "yellow"),
                        selected.get(),
                        i -> {
                          selected.set(i);
                          changeCount.incrementAndGet();
                        },
                        "color")),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 20);
    h.render(app);

    // Click the trigger (top of the column, rows 0..2). Anywhere in row 1 lands inside the box.
    h.events.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);

    // Now the option list is open just below the trigger. Default size: 4 items + 2 borders =
    // 6 rows starting at y=3. Row content (after the top border at y=3) starts at y=4.
    // Click on the third item ("blue") which sits at y=6.
    h.events.dispatchMouse(new MouseEvent(5, 6, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);

    assertEquals(1, changeCount.get(), "click on option row should fire onChange exactly once");
    assertEquals(2, selected.get(), "blue (index 2) selected");

    // After committing, dropdown should be closed — re-render shouldn't paint the option list,
    // and clicking where the option list was should NOT re-commit.
    int countBefore = changeCount.get();
    h.events.dispatchMouse(new MouseEvent(5, 6, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);
    assertEquals(countBefore, changeCount.get(), "dropdown is closed; click does not commit");
  }

  /// Up/Down move the highlight and Enter commits — keys are registered on the dropdown's own
  /// fiber (not on the overlay's portal subtree, which is descendant of the dropdown and so
  /// outside the focused-bubble chain).
  @Test
  void keyboard_navigation_works_when_open() throws IOException {
    AtomicInteger selected = new AtomicInteger(0);
    AtomicInteger changeCount = new AtomicInteger(0);

    Element app =
        column(
                length(
                    3,
                    dropdown(
                        "Color",
                        List.of("red", "green", "blue"),
                        selected.get(),
                        i -> {
                          selected.set(i);
                          changeCount.incrementAndGet();
                        },
                        "color")),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 20);
    h.render(app);

    // Open with click — also focuses the dropdown.
    h.events.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);

    // Press Down twice: highlight 0 → 1 → 2 (blue).
    h.renderer.dispatchKey(
        new jatatui.react.KeyEvent(new tui.crossterm.KeyCode.Down(), new KeyModifiers(0)));
    h.render(app);
    h.renderer.dispatchKey(
        new jatatui.react.KeyEvent(new tui.crossterm.KeyCode.Down(), new KeyModifiers(0)));
    h.render(app);

    // Commit with Enter.
    h.renderer.dispatchKey(
        new jatatui.react.KeyEvent(new tui.crossterm.KeyCode.Enter(), new KeyModifiers(0)));
    h.render(app);

    assertEquals(1, changeCount.get(), "Enter commits exactly once");
    assertEquals(2, selected.get(), "highlight moved 0 → 1 → 2 (blue)");
  }

  /// Dropdown is generic over `T` — typed options (here an enum) work with a labelFn.
  enum Color {
    RED,
    GREEN,
    BLUE
  }

  @Test
  void typed_options_via_labelfn() throws IOException {
    AtomicInteger selected = new AtomicInteger(0);
    AtomicInteger changeCount = new AtomicInteger(0);

    Element app =
        column(
                length(
                    3,
                    jatatui.components.Components.dropdown(
                        DropdownProps.of(
                                "Color",
                                java.util.Arrays.asList(Color.RED, Color.GREEN, Color.BLUE),
                                c -> c.name().toLowerCase(),
                                selected.get(),
                                i -> {
                                  selected.set(i);
                                  changeCount.incrementAndGet();
                                })
                            .withFocusId("color")
                            .withAutoFocus(true))),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 20);
    h.render(app);

    // Open with click, then click the second option (GREEN at y=5).
    h.events.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);
    h.events.dispatchMouse(new MouseEvent(5, 5, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);

    assertEquals(1, changeCount.get());
    assertEquals(1, selected.get(), "GREEN = index 1");
  }

  /// Cursor-then-Tab commits: navigating with Up/Down and then Tab'ing away takes the current
  /// highlight as the selection (rather than discarding it). Cancel paths — Esc, backdrop
  /// click — go through different branches and stay non-committing.
  @Test
  void tab_with_moved_highlight_commits_selection() throws IOException {
    AtomicInteger selected = new AtomicInteger(0);
    AtomicInteger changeCount = new AtomicInteger(0);

    Element app =
        column(
                length(
                    3,
                    dropdown(
                        "A",
                        List.of("red", "green", "blue"),
                        selected.get(),
                        i -> {
                          selected.set(i);
                          changeCount.incrementAndGet();
                        },
                        "a")),
                length(3, dropdown("B", List.of("p", "q"), 0, i -> {}, "b")),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 20);
    h.render(app);

    // Open A.
    h.events.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);

    // Move highlight 0 → 1 → 2 (blue).
    h.renderer.dispatchKey(
        new jatatui.react.KeyEvent(new tui.crossterm.KeyCode.Down(), new KeyModifiers(0)));
    h.render(app);
    h.renderer.dispatchKey(
        new jatatui.react.KeyEvent(new tui.crossterm.KeyCode.Down(), new KeyModifiers(0)));
    h.render(app);

    // Tab to B (in real app, ReactApp does focus.tab; here we call it directly). The auto-
    // close branch should commit the current highlight (blue, index 2).
    h.renderer.tab();
    h.render(app);

    assertEquals(1, changeCount.get(), "Tab away with moved highlight commits exactly once");
    assertEquals(2, selected.get(), "blue is the selection (index 2)");
  }

  /// Opening then Tab'ing away without moving the highlight does NOT fire onChange (no real
  /// change to commit).
  @Test
  void tab_without_moving_highlight_does_not_fire_onchange() throws IOException {
    AtomicInteger changeCount = new AtomicInteger(0);

    Element app =
        column(
                length(
                    3,
                    dropdown(
                        "A",
                        List.of("red", "green", "blue"),
                        1, // start with green selected
                        i -> changeCount.incrementAndGet(),
                        "a")),
                length(3, dropdown("B", List.of("p", "q"), 0, i -> {}, "b")),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 20);
    h.render(app);

    // Open A (highlight initialized to selectedIndex = 1).
    h.events.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);

    // Tab away — same highlight as selection, no onChange.
    h.renderer.tab();
    h.render(app);

    assertEquals(0, changeCount.get(), "no movement, no commit");
  }

  /// Tab while the dropdown is open: ReactApp's loop moves focus to the next focusable. The
  /// dropdown's auto-close-on-focus-loss closes the open list. (Focus arrives at the next
  /// dropdown via FocusManager.tab in a real loop; here we exercise the auto-close branch.)
  @Test
  void losing_focus_closes_open_dropdown() throws IOException {
    Element app =
        column(
                length(3, dropdown("A", List.of("x", "y"), 0, i -> {}, "a")),
                length(3, dropdown("B", List.of("p", "q"), 0, i -> {}, "b")),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 20);
    h.render(app);

    // Open the first dropdown.
    h.events.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);
    assertEquals(
        java.util.Optional.of("a"), h.focus.currentlyFocused(), "click on A's trigger focused A");

    // Move focus to B (Tab in ReactApp would do focus.tab(); we call it directly for the test).
    h.renderer.tab();
    h.render(app);
    assertEquals(java.util.Optional.of("b"), h.focus.currentlyFocused(), "focus moved to B");

    // The first dropdown's open list should no longer respond — Down on B doesn't change A.
    // We can't easily inspect openState directly, but the symptom would be: clicking where A's
    // open-list used to be re-fires onChange. After auto-close, that click does nothing.
    h.events.dispatchMouse(new MouseEvent(5, 5, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);
    // No commit happened on either dropdown — implicit assertion (no exception, focus still B).
    assertEquals(java.util.Optional.of("b"), h.focus.currentlyFocused());
  }

  /// Backdrop click outside the option list closes the dropdown WITHOUT committing.
  @Test
  void backdrop_click_closes_without_committing() throws IOException {
    AtomicInteger changeCount = new AtomicInteger(0);

    Element app =
        column(
                length(
                    3,
                    dropdown(
                        "Color",
                        List.of("red", "green", "blue"),
                        0,
                        i -> changeCount.incrementAndGet(),
                        "color")),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 20);
    h.render(app);

    // Open it.
    h.events.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);

    // Click far below the option list (somewhere on the backdrop, outside both trigger and
    // option list). Default list at y=3..7-ish; click at y=18 is well below.
    h.events.dispatchMouse(new MouseEvent(20, 18, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);

    assertEquals(0, changeCount.get(), "backdrop click does not change selection");

    // Verify list closed by clicking where an option used to be — no commit.
    h.events.dispatchMouse(new MouseEvent(5, 5, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    h.render(app);
    assertEquals(0, changeCount.get());
  }
}
