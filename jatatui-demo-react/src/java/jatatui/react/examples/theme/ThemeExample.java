package jatatui.react.examples.theme;

import static jatatui.components.Components.themeProvider;
import static jatatui.react.Components.*;

import jatatui.components.theme.Theme;
import jatatui.core.layout.Margin;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import jatatui.widgets.Borders;
import java.io.IOException;
import tui.crossterm.KeyCode;

/// Demonstrates ThemeProvider.
///
/// Press `t` to toggle between LIGHT and DARK. The same content re-renders with theme styles
/// pulled from Context — no prop drilling.
public final class ThemeExample {

  public static void main(String[] args) throws IOException {
    ReactApp.run(app());
  }

  static Element app() {
    return component(
        ctx -> {
          var dark = ctx.useState(() -> false);
          ctx.onGlobalKey(new KeyCode.Char('t'), () -> dark.update(b -> !b));
          Theme theme = dark.get() ? Theme.DARK : Theme.LIGHT;
          return themeProvider(theme, content());
        });
  }

  static Element content() {
    return component(
        ctx -> {
          Theme theme = Theme.useTheme(ctx);
          return column(
                  length(
                      1, text(" theme demo  —  press 't' to toggle, Esc to quit ", theme.title())),
                  length(3, box(" Title ", Borders.ALL, text("Themed body text", theme.page()))),
                  length(2, text("Accent text", theme.accent())),
                  length(1, text("Success — operation completed", theme.success())),
                  length(1, text("Warning — heads up", theme.warning())),
                  length(1, text("Error — something failed", theme.error())),
                  length(1, text("Muted explanatory text", theme.muted())),
                  fill(1, text("")))
              .with(p -> p.withSpacing(1).withMargin(new Margin(2, 1)));
        });
  }
}
