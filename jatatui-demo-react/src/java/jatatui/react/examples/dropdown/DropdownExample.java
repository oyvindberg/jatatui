package jatatui.react.examples.dropdown;

import static jatatui.components.Components.dropdown;
import static jatatui.react.Components.*;

import jatatui.core.layout.Margin;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import java.io.IOException;
import java.util.List;

/// Demonstrates Dropdown / Select.
///
/// Tab between three dropdowns. Enter / Space / Down opens. Up/Down navigate. Enter selects.
/// Esc closes without committing. Click outside also closes.
public final class DropdownExample {

  public static void main(String[] args) throws IOException {
    ReactApp.run(app());
  }

  static Element app() {
    return component(
        ctx -> {
          var color = ctx.useState(() -> 0);
          var size = ctx.useState(() -> 1);
          var lang = ctx.useState(() -> 2);

          List<String> colors = List.of("red", "green", "blue", "yellow", "magenta");
          List<String> sizes = List.of("small", "medium", "large", "xlarge");
          List<String> langs = List.of("Java", "Scala", "Kotlin", "Rust", "Go");

          return column(
                  length(
                      1,
                      text(
                          " dropdown demo  —  Tab to cycle, Enter to open, Esc to quit ",
                          Style.empty().withFg(Color.WHITE).withBg(Color.BLUE))),
                  length(3, dropdown("Color", colors, color.get(), color::set, "color")),
                  length(3, dropdown("Size", sizes, size.get(), size::set, "size")),
                  length(3, dropdown("Lang", langs, lang.get(), lang::set, "lang")),
                  length(
                      2,
                      text(
                          "Selected: "
                              + colors.get(color.get())
                              + ", "
                              + sizes.get(size.get())
                              + ", "
                              + langs.get(lang.get()),
                          Style.empty().withFg(Color.GRAY))),
                  fill(1, text("")))
              .with(p -> p.withSpacing(1).withMargin(new Margin(2, 1)));
        });
  }
}
