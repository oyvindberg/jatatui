package jatatui.components.picker;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.KeyEvent;
import jatatui.react.MouseEvent;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyModifiers;

class PickerTest {

  /// Helper: trivial substring filter over a fixed list.
  static PickerProps.Filter<String> substring(List<String> items) {
    return q ->
        items.stream()
            .filter(s -> q.isEmpty() || s.toLowerCase().contains(q.toLowerCase()))
            .toList();
  }

  /// Helper: row renderer that returns text with a marker for selection.
  static PickerProps.RowRenderer<String> textRow() {
    return (item, selected) -> text((selected ? "> " : "  ") + item, Style.empty());
  }

  /// Helper: build a picker rendered alone, big enough that the modal sits in the middle.
  static Element pickerApp(PickerProps<String> props) {
    return props == null ? text("") : Picker.of(props);
  }

  @Test
  void enter_fires_onselect_with_highlighted_item() throws IOException {
    AtomicReference<String> selected = new AtomicReference<>();
    AtomicInteger cancelCount = new AtomicInteger();

    PickerProps<String> props =
        PickerProps.of(
            " Pick ",
            substring(List.of("apple", "banana", "cherry")),
            textRow(),
            selected::set,
            cancelCount::incrementAndGet);

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(props));

    // Enter on the first item (highlight defaults to 0).
    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Enter(), new KeyModifiers(0)));
    assertEquals("apple", selected.get());
    assertEquals(0, cancelCount.get(), "Enter does not cancel");
  }

  @Test
  void down_then_enter_picks_second_item() throws IOException {
    AtomicReference<String> selected = new AtomicReference<>();
    PickerProps<String> props =
        PickerProps.of(
            " Pick ",
            substring(List.of("apple", "banana", "cherry")),
            textRow(),
            selected::set,
            () -> {});

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(props));

    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Down(), new KeyModifiers(0)));
    h.render(pickerApp(props));
    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Enter(), new KeyModifiers(0)));
    assertEquals("banana", selected.get());
  }

  @Test
  void up_clamps_at_zero() throws IOException {
    AtomicReference<String> selected = new AtomicReference<>();
    PickerProps<String> props =
        PickerProps.of(
            " Pick ", substring(List.of("a", "b", "c")), textRow(), selected::set, () -> {});

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(props));

    // Press Up multiple times — selection clamps at 0.
    for (int i = 0; i < 5; i++) {
      h.renderer.dispatchKey(new KeyEvent(new KeyCode.Up(), new KeyModifiers(0)));
      h.render(pickerApp(props));
    }
    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Enter(), new KeyModifiers(0)));
    assertEquals("a", selected.get());
  }

  @Test
  void down_clamps_at_size_minus_one() throws IOException {
    AtomicReference<String> selected = new AtomicReference<>();
    PickerProps<String> props =
        PickerProps.of(
            " Pick ", substring(List.of("a", "b", "c")), textRow(), selected::set, () -> {});

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(props));

    for (int i = 0; i < 10; i++) {
      h.renderer.dispatchKey(new KeyEvent(new KeyCode.Down(), new KeyModifiers(0)));
      h.render(pickerApp(props));
    }
    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Enter(), new KeyModifiers(0)));
    assertEquals("c", selected.get());
  }

  @Test
  void esc_fires_oncancel() throws IOException {
    AtomicInteger cancelCount = new AtomicInteger();
    PickerProps<String> props =
        PickerProps.of(
            " Pick ",
            substring(List.of("a", "b")),
            textRow(),
            s -> {},
            cancelCount::incrementAndGet);

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(props));

    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Esc(), new KeyModifiers(0)));
    assertEquals(1, cancelCount.get());
  }

  @Test
  void click_outside_modal_fires_oncancel() throws IOException {
    AtomicInteger cancelCount = new AtomicInteger();
    PickerProps<String> props =
        PickerProps.of(
            " Pick ",
            substring(List.of("a", "b")),
            textRow(),
            s -> {},
            cancelCount::incrementAndGet);

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(props));

    // Picker is 80x20 centered in 120x30 → modal covers (20..99, 5..24). Click at (5, 1) is
    // outside.
    h.renderer.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertEquals(1, cancelCount.get());
  }

  @Test
  void click_on_row_fires_onselect_with_that_row() throws IOException {
    AtomicReference<String> selected = new AtomicReference<>();
    PickerProps<String> props =
        PickerProps.of(
            " Pick ",
            substring(List.of("apple", "banana", "cherry")),
            textRow(),
            selected::set,
            () -> {});

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(props));

    // Modal centered in 120x30 with size 80x20 → top-left at (20, 5). Inside the box border
    // the textinput takes 3 rows, then results start at y = 5 + 1 (top border) + 3 (input).
    // First result row is at y = 9. Click on it.
    h.renderer.dispatchMouse(new MouseEvent(30, 9, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertEquals("apple", selected.get());
  }

  @Test
  void empty_results_renders_no_matches_line_and_enter_is_noop() throws IOException {
    AtomicReference<String> selected = new AtomicReference<>();
    PickerProps<String> props =
        PickerProps.of(
            " Pick ",
            q -> List.of(), // always empty
            textRow(),
            selected::set,
            () -> {});

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(props));

    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Enter(), new KeyModifiers(0)));
    assertNull(selected.get(), "Enter on empty results does not commit anything");

    // Spot-check the rendered cell where "  no matches" is painted.
    var buf = h.backend.buffer();
    StringBuilder line = new StringBuilder();
    int y = 5 + 1 + 3; // box top + top border + 3-row input
    for (int x = 21; x < 21 + 12; x++) line.append(buf.cellAt(x, y).symbol());
    assertEquals("  no matches", line.toString());
  }

  /// When the host has another focusable rendered alongside (or before) the picker — typical
  /// pattern: a list underneath, picker overlaid on top via conditional render — the picker
  /// must still grab focus so keystrokes go to its query field instead of leaking to the host.
  /// Without the on-mount useEffect, FocusManager's eager-claim wouldn't fire (focus already
  /// held by the list) and the user's typing would route to the list's onKey handlers.
  @Test
  void picker_steals_focus_on_mount_even_with_competing_focusable() throws IOException {
    java.util.concurrent.atomic.AtomicBoolean showPicker =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    PickerProps<String> props =
        PickerProps.of(" Pick ", substring(List.of("a", "b", "c")), textRow(), s -> {}, () -> {});

    Element app =
        component(
            ctx -> {
              // Host's focusable, registered first → claims focus on first frame.
              ctx.useFocus(java.util.Optional.of("host-list"), true);
              return showPicker.get() ? Picker.of(props) : text("(closed)");
            });

    TestHarness h = new TestHarness(120, 30);
    h.render(app);
    h.render(app);
    assertEquals(
        java.util.Optional.of("host-list"),
        h.focus.currentlyFocused(),
        "host's autoFocus claims focus while picker is closed");

    showPicker.set(true);
    h.render(app);

    assertEquals(
        java.util.Optional.of(Picker.QUERY_FOCUS_ID),
        h.focus.currentlyFocused(),
        "picker's on-mount useEffect steals focus from the host's list");
  }

  @Test
  void hint_can_be_dropped() throws IOException {
    PickerProps<String> withHint =
        PickerProps.of(" Pick ", substring(List.of("a")), textRow(), s -> {}, () -> {});
    PickerProps<String> withoutHint = withHint.withoutHint();

    TestHarness h = new TestHarness(120, 30);
    h.render(pickerApp(withoutHint));

    // The hint line in the default props starts with "  up/down navigate". Verify it's NOT
    // painted in the box's bottom-content row.
    var buf = h.backend.buffer();
    int y = 5 + 20 - 2; // box top + height - 1 (bottom border) - 1 (hint row, only when present)
    StringBuilder line = new StringBuilder();
    for (int x = 21; x < 21 + 16; x++) line.append(buf.cellAt(x, y).symbol());
    assertNotEquals("  up/down navi", line.toString().substring(0, 14));
    assertEquals(Optional.empty(), withoutHint.hint());
  }
}
