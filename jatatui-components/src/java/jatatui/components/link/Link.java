package jatatui.components.link;

import static jatatui.react.Components.*;

import jatatui.react.Element;
import java.util.Optional;
import java.util.function.Function;
import tui.crossterm.KeyCode;

/// Hyperlink-style focusable + clickable activation target. Wraps a body Element with a
/// `useFocus` / `onKey(Enter)` / `onClick` triad so each link doesn't reimplement the same
/// three lines. The body is a function of the live focus state so the caller can paint the
/// focused/unfocused variant without re-querying.
///
/// Router-agnostic: takes a [Runnable] `onActivate`. The most common use is
/// `() -> router.push(screen)` (matches the typr.cli.app.components.Link original), but any
/// callback works (open a modal, open a URL, etc.).
public final class Link {
  private Link() {}

  /// Tab-focusable link. Activation = Enter when focused OR mouse click anywhere in the body's
  /// area. `content(focused)` receives the live focus state.
  public static Element focusable(
      boolean autoFocus, Runnable onActivate, Function<Boolean, Element> content) {
    return component(
        ctx -> {
          boolean focused = ctx.useFocus(Optional.empty(), autoFocus);
          if (focused) ctx.onKey(new KeyCode.Enter(), onActivate);
          ctx.onClick(e -> onActivate.run());
          return content.apply(focused);
        });
  }

  /// Tab-focusable link with an explicit focus id (for imperative `ctx.focus(id)` / Tab order
  /// stability across reorders).
  public static Element focusable(
      String focusId, boolean autoFocus, Runnable onActivate, Function<Boolean, Element> content) {
    return component(
        ctx -> {
          boolean focused = ctx.useFocus(Optional.of(focusId), autoFocus);
          if (focused) ctx.onKey(new KeyCode.Enter(), onActivate);
          ctx.onClick(e -> onActivate.run());
          return content.apply(focused);
        });
  }

  /// Bare click-to-activate. NOT focusable — activate by mouse only. Useful for icons / chips
  /// where a Tab stop would be intrusive.
  public static Element click(Runnable onActivate, Element content) {
    return component(
        ctx -> {
          ctx.onClick(e -> onActivate.run());
          return content;
        });
  }
}
