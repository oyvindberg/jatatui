package jatatui.react.examples.router;

import static jatatui.components.Components.router;
import static jatatui.react.Components.*;

import jatatui.components.router.RouterApi;
import jatatui.components.router.Screen;
import jatatui.core.layout.Margin;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import jatatui.widgets.Borders;
import java.io.IOException;
import tui.crossterm.KeyCode;

/// Demonstrates Router (stack-based navigation).
///
/// On Home: press `s` to go to Settings, `a` to go to About. On Settings/About: press
/// `Backspace` (or click "Back") to pop. The breadcrumb at the top shows the current stack.
public final class RouterExample {

  public static void main(String[] args) throws IOException {
    ReactApp.run(router(Screen.of("home", home())));
  }

  static Element home() {
    return component(
        ctx -> {
          RouterApi r = RouterApi.useRouter(ctx);
          ctx.onGlobalKey(new KeyCode.Char('s'), () -> r.push("settings", settings()));
          ctx.onGlobalKey(new KeyCode.Char('a'), () -> r.push("about", about()));
          return chrome(
              r,
              column(
                  length(1, text("Welcome to Home.", Style.empty().withFg(Color.WHITE))),
                  length(
                      2,
                      text(
                          "Press 's' for Settings, 'a' for About.",
                          Style.empty().withFg(Color.GRAY))),
                  fill(1, text(""))));
        });
  }

  static Element settings() {
    return component(
        ctx -> {
          RouterApi r = RouterApi.useRouter(ctx);
          ctx.onGlobalKey(new KeyCode.Backspace(), r::pop);
          return chrome(
              r,
              column(
                  length(1, text("Settings page.", Style.empty().withFg(Color.WHITE))),
                  length(
                      2,
                      text(
                          "Press 'Backspace' to go back, 'a' to push About on top.",
                          Style.empty().withFg(Color.GRAY))),
                  whenInline(ctx, r),
                  fill(1, text(""))));
        });
  }

  static Element about() {
    return component(
        ctx -> {
          RouterApi r = RouterApi.useRouter(ctx);
          ctx.onGlobalKey(new KeyCode.Backspace(), r::pop);
          return chrome(
              r,
              column(
                  length(1, text("About this app.", Style.empty().withFg(Color.WHITE))),
                  length(2, text("Built with jatatui-react.", Style.empty().withFg(Color.GRAY))),
                  fill(1, text(""))));
        });
  }

  /// Push About onto a settings screen — small "open about from settings" affordance.
  static Element whenInline(jatatui.react.RenderContext ctx, RouterApi r) {
    ctx.onGlobalKey(new KeyCode.Char('a'), () -> r.push("about", about()));
    return length(
        1,
        text(
            "(or press 'a' to push About on top of Settings)",
            Style.empty().withFg(Color.DARK_GRAY)));
  }

  /// Common chrome: breadcrumb on top + the screen body.
  static Element chrome(RouterApi r, Element body) {
    StringBuilder bc = new StringBuilder(" ");
    for (int i = 0; i < r.stack().size(); i++) {
      if (i > 0) bc.append(" / ");
      bc.append(r.stack().get(i).label());
    }
    return column(
            length(1, text(bc.toString(), Style.empty().withFg(Color.WHITE).withBg(Color.BLUE))),
            length(1, text(" Backspace=pop  Esc=quit  ", Style.empty().withFg(Color.GRAY))),
            fill(1, box(" Screen ", Borders.ALL, body)))
        .with(p -> p.withSpacing(0).withMargin(new Margin(1, 1)));
  }
}
