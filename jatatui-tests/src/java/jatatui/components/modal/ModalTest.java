package jatatui.components.modal;

import static jatatui.components.Components.modal;
import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.layout.Margin;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.MouseEvent;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ModalTest {

  /// Modal default 40x12 in an 80x24 screen → centered box at (20, 6, 40, 12). The button at
  /// rows 0-2 is OUTSIDE the box. Click on the button area must hit backdrop, not button.
  @Test
  void backdrop_blocks_clicks_to_underlying_button_when_open() throws IOException {
    AtomicBoolean buttonFired = new AtomicBoolean(false);
    AtomicBoolean dismissed = new AtomicBoolean(false);

    Element app =
        column(
                length(3, button("  [ Open Modal ]  ", Style.empty(), () -> buttonFired.set(true))),
                fill(1, text("background")),
                modal(true, " Modal ", text("body"), () -> dismissed.set(true)))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(80, 24);
    h.render(app);

    // Click in the button row (5, 1) — well above the centered modal box at (20, 6, 40, 12).
    MouseEvent click =
        new MouseEvent(5, 1, new tui.crossterm.KeyModifiers(0), MouseEvent.Kind.DOWN);
    h.events.dispatchMouse(click);

    assertFalse(buttonFired.get(), "underlying button must NOT fire when modal backdrop covers it");
    assertTrue(dismissed.get(), "click on backdrop should call onDismiss");
  }

  @Test
  void box_clicks_do_not_dismiss_and_do_not_reach_underlying() throws IOException {
    AtomicBoolean buttonFired = new AtomicBoolean(false);
    AtomicBoolean dismissed = new AtomicBoolean(false);

    Element app =
        column(
                length(3, button("  [ btn ]  ", Style.empty(), () -> buttonFired.set(true))),
                fill(1, text("bg")),
                modal(true, " Modal ", text("body"), () -> dismissed.set(true)))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(80, 24);
    h.render(app);

    // Click inside the modal box area (centered 40x12 at 20,6) — at (40, 12).
    MouseEvent click =
        new MouseEvent(40, 12, new tui.crossterm.KeyModifiers(0), MouseEvent.Kind.DOWN);
    h.events.dispatchMouse(click);

    assertFalse(dismissed.get(), "clicks inside the modal box must not dismiss");
    assertFalse(buttonFired.get(), "clicks inside the modal box must not reach underlying widgets");
  }

  @Test
  void modal_closed_renders_nothing_extra() throws IOException {
    AtomicBoolean buttonFired = new AtomicBoolean(false);

    Element app =
        column(
                length(3, button("  [ Open Modal ]  ", Style.empty(), () -> buttonFired.set(true))),
                fill(1, text("bg")),
                modal(false, " Modal ", text("body"), () -> {}))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(80, 24);
    h.render(app);

    MouseEvent click =
        new MouseEvent(5, 1, new tui.crossterm.KeyModifiers(0), MouseEvent.Kind.DOWN);
    h.events.dispatchMouse(click);

    assertTrue(buttonFired.get(), "with modal closed, button must fire normally");
  }
}
