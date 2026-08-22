package jatatui.components.chrome;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.react.Element;
import jatatui.react.MouseEvent;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyModifiers;

class ScreenFrameTest {

  /// Body content's first useFocus(autoFocus=true) should claim focus — ScreenFrame's
  /// BackButton is intentionally not focusable, so the focus chain starts with content.
  @Test
  void body_first_focusable_claims_focus() throws IOException {
    AtomicBoolean innerFocused = new AtomicBoolean();
    Element body =
        component(
            ctx -> {
              boolean f = ctx.useFocus(Optional.of("inner"), true);
              innerFocused.set(f);
              return text("body");
            });

    Element app = ScreenFrame.of("home", () -> {}, body);

    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    assertTrue(innerFocused.get(), "body's autoFocus claims focus same-frame (eager-claim)");
  }

  @Test
  void clicking_back_chip_invokes_back() throws IOException {
    AtomicInteger backCount = new AtomicInteger();
    Element app = ScreenFrame.of("home", backCount::incrementAndGet, text("body"));

    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    h.renderer.dispatchMouse(new MouseEvent(2, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertEquals(1, backCount.get());
  }
}
