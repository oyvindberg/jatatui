package jatatui.components.chrome;

import static jatatui.react.Components.*;

import jatatui.react.Element;

/// Standard top-of-screen chrome: a [BackButton] chip in the upper-left, a blank gutter line,
/// then `content` filling the rest of the area.
///
/// Tab order is unaffected — BackButton is intentionally non-focusable, so the focus chain
/// starts with whatever focusable `content` registers first.
public final class ScreenFrame {
  private ScreenFrame() {}

  public static Element of(String backLabel, Runnable back, Element content) {
    return component(
        ctx ->
            column(
                length(BackButton.HEIGHT, BackButton.of(backLabel, back)),
                length(1, empty()),
                fill(1, content)));
  }

  /// Variant with a brand/title strip next to the back button (e.g. main-menu header). The
  /// back chip is fixed-width on the left; the title fills the remainder.
  public static Element withTitle(String backLabel, Runnable back, Element title, Element content) {
    return component(
        ctx ->
            column(
                length(
                    BackButton.HEIGHT,
                    row(length(20, BackButton.of(backLabel, back)), fill(1, title))),
                length(1, empty()),
                fill(1, content)));
  }
}
