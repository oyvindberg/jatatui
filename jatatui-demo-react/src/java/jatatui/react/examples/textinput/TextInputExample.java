package jatatui.react.examples.textinput;

import static jatatui.components.Components.titledTextInput;
import static jatatui.react.Components.*;

import jatatui.core.layout.Margin;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import java.io.IOException;

/// Demonstrates the textInput Component.
///
/// Tab cycles between two text inputs. Type to edit. Backspace / Delete / Left / Right / Home /
/// End work as expected. The "Echo" line beneath shows both current values.
public final class TextInputExample {

  public static void main(String[] args) throws IOException {
    ReactApp.run(app());
  }

  static Element app() {
    return component(
        ctx -> {
          var name = ctx.useState(() -> "");
          var email = ctx.useState(() -> "");

          return column(
                  length(
                      1,
                      text(
                          " textInput demo  —  Tab to cycle, Esc to quit ",
                          Style.empty().withFg(Color.WHITE).withBg(Color.BLUE))),
                  length(3, titledTextInput("Name", name.get(), name::set, "first name", "name")),
                  length(
                      3,
                      titledTextInput(
                          "Email", email.get(), email::set, "you@example.com", "email")),
                  length(
                      2,
                      text(
                          "Echo: name=\"" + name.get() + "\"  email=\"" + email.get() + "\"",
                          Style.empty().withFg(Color.GRAY))),
                  fill(1, text("")))
              .with(p -> p.withSpacing(1).withMargin(new Margin(2, 1)));
        });
  }
}
