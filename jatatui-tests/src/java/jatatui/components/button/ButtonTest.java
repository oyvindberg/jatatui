package jatatui.components.button;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.layout.Margin;
import jatatui.react.Element;
import jatatui.react.KeyEvent;
import jatatui.react.MouseEvent;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tui.crossterm.KeyCode;
import tui.crossterm.KeyModifiers;

class ButtonTest {

  @Test
  void click_activates() throws IOException {
    AtomicInteger n = new AtomicInteger();
    Element app =
        column(length(3, Button.of("Save", "save", true, n::incrementAndGet)), fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 10);
    h.render(app);
    h.renderer.dispatchMouse(new MouseEvent(2, 1, new KeyModifiers(0), MouseEvent.Kind.DOWN));
    assertEquals(1, n.get());
  }

  @Test
  void enter_activates_when_focused() throws IOException {
    AtomicInteger n = new AtomicInteger();
    Element app =
        column(length(3, Button.of("Save", "save", true, n::incrementAndGet)), fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 10);
    h.render(app);
    h.renderer.tab(); // focus the button
    h.render(app);
    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Enter(), new KeyModifiers(0)));
    assertEquals(1, n.get());
  }

  @Test
  void enter_does_nothing_when_not_focused() throws IOException {
    AtomicInteger n = new AtomicInteger();
    Element app =
        column(length(3, Button.of("Save", "save", true, n::incrementAndGet)), fill(1, text("")))
            .with(p -> p.withSpacing(0).withMargin(new Margin(0, 0)));

    TestHarness h = new TestHarness(40, 10);
    h.render(app);
    h.renderer.dispatchKey(new KeyEvent(new KeyCode.Enter(), new KeyModifiers(0)));
    assertEquals(0, n.get(), "no focus → Enter is a noop");
  }
}
