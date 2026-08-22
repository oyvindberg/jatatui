package jatatui.react.examples.modal;

import static jatatui.components.Components.modal;
import static jatatui.react.Components.*;

import jatatui.core.layout.Margin;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import jatatui.widgets.Borders;
import java.io.IOException;
import tui.crossterm.KeyCode;

/// Demonstrates Modal.
///
/// Press `o` (or click "Open Modal") to open. Esc / click outside the box to close.
public final class ModalExample {

  public static void main(String[] args) throws IOException {
    ReactApp.run(app());
  }

  static Element app() {
    return component(
        ctx -> {
          var open = ctx.useState(() -> false);

          ctx.onGlobalKey(new KeyCode.Char('o'), () -> open.set(true));

          return column(
                  length(
                      1,
                      text(
                          " modal demo  —  press 'o' to open, Esc/click outside to close, q to quit"
                              + " ",
                          Style.empty().withFg(Color.WHITE).withBg(Color.BLUE))),
                  length(
                      3,
                      button(
                          "  [ Open Modal ]  ",
                          Style.empty().withFg(Color.YELLOW),
                          () -> open.set(true))),
                  fill(
                      1,
                      box(
                          " Background content ",
                          Borders.ALL,
                          text("This is the underlying UI. The modal will paint over it."),
                          text(""),
                          text("Try clicking through the backdrop — the click is intercepted."),
                          text("Try clicking inside the modal box — the click stays local."))),
                  modal(
                      open.get(),
                      " Modal Title ",
                      column(
                          text("This is the modal body."),
                          text(""),
                          text("Esc dismisses, click outside dismisses,"),
                          text("clicks inside stay local."),
                          text(""),
                          length(
                              1,
                              text(
                                  "  [Press Esc or click outside]",
                                  Style.empty().withFg(Color.GRAY)))),
                      () -> open.set(false)))
              .with(p -> p.withSpacing(1).withMargin(new Margin(2, 1)));
        });
  }
}
