package jatatui.components.chrome;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.layout.Margin;
import jatatui.react.Element;
import jatatui.react.MouseEvent;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyModifiers;

class BackButtonTest {

  @Test
  void click_inside_chip_invokes_back() throws IOException {
    AtomicInteger backCount = new AtomicInteger();
    Element app =
        column(
                length(BackButton.HEIGHT, BackButton.of("sources", backCount::incrementAndGet)),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(60, 12);
    h.render(app);

    // Chip width = "sources".length() + 5 = 12. Click in the middle of the chip.
    h.renderer.dispatchMouse(new MouseEvent(5, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertEquals(1, backCount.get());
  }

  @Test
  void click_past_chip_edge_does_not_invoke_back() throws IOException {
    AtomicInteger backCount = new AtomicInteger();
    Element app =
        column(
                length(BackButton.HEIGHT, BackButton.of("x", backCount::incrementAndGet)),
                fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(60, 12);
    h.render(app);

    // Chip width = "x".length() + 5 = 6. Click far past it at x=40 — should fall through.
    h.renderer.dispatchMouse(new MouseEvent(40, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertEquals(0, backCount.get());
  }
}
