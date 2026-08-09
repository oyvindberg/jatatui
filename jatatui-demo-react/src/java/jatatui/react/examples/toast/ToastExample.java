package jatatui.react.examples.toast;

import static jatatui.components.Components.toastsProvider;
import static jatatui.react.Components.*;

import jatatui.components.toast.ToastApi;
import jatatui.core.layout.Margin;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import java.io.IOException;
import tui.crossterm.KeyCode;

/// Demonstrates ToastsProvider.
///
/// Press `i` for an info toast, `s` for success, `w` for warning, `e` for error. Each
/// auto-dismisses after 3s. Press `c` to clear all.
public final class ToastExample {

  public static void main(String[] args) throws IOException {
    ReactApp.run(toastsProvider(content()));
  }

  static Element content() {
    return component(
        ctx -> {
          ToastApi toasts = ToastApi.useToasts(ctx);

          ctx.onGlobalKey(new KeyCode.Char('i'), () -> toasts.info("Info: just letting you know"));
          ctx.onGlobalKey(new KeyCode.Char('s'), () -> toasts.success("Success: it worked"));
          ctx.onGlobalKey(new KeyCode.Char('w'), () -> toasts.warn("Warning: be careful"));
          ctx.onGlobalKey(new KeyCode.Char('e'), () -> toasts.error("Error: something broke"));
          ctx.onGlobalKey(new KeyCode.Char('c'), toasts::dismissAll);

          return column(
                  length(
                      1,
                      text(
                          " toast demo  —  i / s / w / e to add a toast, c to clear, Esc to quit ",
                          Style.empty().withFg(Color.WHITE).withBg(Color.BLUE))),
                  length(
                      2,
                      text(
                          "Toasts auto-dismiss after 3 seconds.",
                          Style.empty().withFg(Color.GRAY))),
                  fill(
                      1,
                      text(
                          "Press i, s, w, e to add. Press c to clear all.",
                          Style.empty().withFg(Color.WHITE))))
              .with(p -> p.withSpacing(1).withMargin(new Margin(2, 1)));
        });
  }
}
