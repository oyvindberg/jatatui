package jatatui.components.modal;

import static org.junit.jupiter.api.Assertions.*;

import jatatui.react.Element;
import jatatui.react.KeyEvent;
import jatatui.react.MouseEvent;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyModifiers;

class ConfirmDialogTest {

  @Test
  void esc_fires_oncancel() throws IOException {
    AtomicInteger confirmCount = new AtomicInteger();
    AtomicInteger cancelCount = new AtomicInteger();
    Element app =
        ConfirmDialog.of(
            true,
            " Delete ",
            "Are you sure?",
            "Delete",
            "Keep",
            true,
            confirmCount::incrementAndGet,
            cancelCount::incrementAndGet);

    TestHarness h = new TestHarness(100, 30);
    h.render(app);

    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Esc(), new KeyModifiers(0)));
    assertEquals(1, cancelCount.get());
    assertEquals(0, confirmCount.get());
  }

  @Test
  void backdrop_click_fires_oncancel() throws IOException {
    AtomicInteger cancelCount = new AtomicInteger();
    Element app =
        ConfirmDialog.of(
            true, " Delete ", "?", "Yes", "No", false, () -> {}, cancelCount::incrementAndGet);

    TestHarness h = new TestHarness(100, 30);
    h.render(app);
    // Dialog is 60x11 centered in 100x30 → box at (20..79, 9..19). Click in the backdrop area.
    h.renderer.dispatchMouse(new MouseEvent(5, 2, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertEquals(1, cancelCount.get());
  }

  @Test
  void closed_dialog_does_not_intercept_clicks() throws IOException {
    AtomicInteger cancelCount = new AtomicInteger();
    Element app =
        ConfirmDialog.of(
            false, " Delete ", "?", "Yes", "No", false, () -> {}, cancelCount::incrementAndGet);

    TestHarness h = new TestHarness(100, 30);
    h.render(app);
    h.renderer.dispatchMouse(new MouseEvent(50, 15, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertEquals(0, cancelCount.get(), "closed dialog renders nothing → no backdrop → no cancel");
  }
}
