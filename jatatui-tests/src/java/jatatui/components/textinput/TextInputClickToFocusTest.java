package jatatui.components.textinput;

import static jatatui.components.Components.titledTextInput;
import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.layout.Margin;
import jatatui.react.Element;
import jatatui.react.MouseEvent;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyModifiers;

/// Click-to-focus on TextInputComponent. Two titled inputs in a column; clicking the second
/// transfers focus from the first.
class TextInputClickToFocusTest {

  @Test
  void click_focuses_input() throws IOException {
    TestHarness h = new TestHarness(60, 12);

    List<String> values = new ArrayList<>(List.of("", ""));
    List<Boolean> focusFlags = new ArrayList<>();

    Element app =
        component(
            ctx ->
                column(
                        length(3, namedField(ctx, focusFlags, values, 0, "alpha")),
                        length(3, namedField(ctx, focusFlags, values, 1, "beta")),
                        fill(1, text("")))
                    .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0))));

    h.render(app);
    h.render(app);
    // Clear and read fresh — render invokes the inner components which mutate focusFlags.
    focusFlags.clear();
    h.render(app);
    assertEquals(List.of(true, false), focusFlags, "first input is auto-focused");

    // Click in the second input's area (rows 3..5 of the column).
    h.events.dispatchMouse(new MouseEvent(5, 4, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    focusFlags.clear();
    h.render(app);
    assertEquals(List.of(false, true), focusFlags, "click on input b transfers focus");
  }

  /// `withFocusOnClick(false)` opts out: clicking the input does NOT focus it.
  @Test
  void focus_on_click_can_be_disabled() throws IOException {
    TestHarness h = new TestHarness(60, 12);

    List<String> values = new ArrayList<>(List.of("", ""));
    List<Boolean> focusFlags = new ArrayList<>();

    Element app =
        component(
            ctx ->
                column(
                        length(3, namedField(ctx, focusFlags, values, 0, "alpha")),
                        length(3, namedFieldNoClick(ctx, focusFlags, values, 1, "beta")),
                        fill(1, text("")))
                    .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0))));

    h.render(app);
    h.render(app);
    focusFlags.clear();
    h.render(app);
    assertEquals(List.of(true, false), focusFlags);

    h.events.dispatchMouse(new MouseEvent(5, 4, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    focusFlags.clear();
    h.render(app);
    assertEquals(
        List.of(true, false), focusFlags, "focus stays on first when click-to-focus is disabled");
  }

  static Element namedField(
      jatatui.react.RenderContext outerCtx,
      List<Boolean> flags,
      List<String> values,
      int idx,
      String id) {
    return component(
        ctx -> {
          flags.add(ctx.useFocus(java.util.Optional.of(id), idx == 0));
          int captured = idx;
          return titledTextInput(id, values.get(captured), v -> values.set(captured, v), id, id);
        });
  }

  static Element namedFieldNoClick(
      jatatui.react.RenderContext outerCtx,
      List<Boolean> flags,
      List<String> values,
      int idx,
      String id) {
    return component(
        ctx -> {
          flags.add(ctx.useFocus(java.util.Optional.of(id), idx == 0));
          int captured = idx;
          return jatatui.components.Components.textInput(
              jatatui.components.textinput.TextInputProps.of(
                      values.get(captured), v -> values.set(captured, v))
                  .withTitle(id)
                  .withFocusId(id)
                  .withAutoFocus(false)
                  .withFocusOnClick(false));
        });
  }
}
