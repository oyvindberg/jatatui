package jatatui.components.chrome;

import static jatatui.react.Components.*;

import jatatui.core.layout.Rect;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.core.widgets.Widget;
import jatatui.react.Element;
import jatatui.widgets.Borders;
import jatatui.widgets.block.Block;
import jatatui.widgets.block.BorderType;
import jatatui.widgets.paragraph.Paragraph;

/// Bordered "← label" chip anchored to the top-left of its assigned area. Clickable; not
/// Tab-focusable on purpose — the keyboard binding (Esc / b / whatever) lives on the host
/// screen so it can compose with screen-local state. This component is the visual half.
///
/// Height: [#HEIGHT] cells (3 = top border + content + bottom border).
/// Width: roughly `label.length + 5` (`" ← " + label + 2 borders`). Anything past the chip on
/// the right side is left blank — pair with `length(BackButton.HEIGHT, ...)` in a column and
/// drop additional chrome alongside it.
///
/// On click, the component blurs focus before invoking `back` so the destination screen's
/// first `useFocus(autoFocus=true)` claims same-frame via the eager-claim path. Without this,
/// the destination would flash one frame with stale focus before commit re-picks.
public final class BackButton {
  private BackButton() {}

  /// Standard height of the chip in cells.
  public static final int HEIGHT = 3;

  public static Element of(String label, Runnable back) {
    return component(
        ctx -> {
          int buttonWidth = label.length() + 5; // "← " + label + 2 borders + 1 space

          // Restrict the click target to the visible chip — clicks past the right edge fall
          // through to whatever's underneath.
          ctx.area()
              .ifPresent(
                  a -> {
                    int width = Math.min(buttonWidth, a.width());
                    Rect hit = new Rect(a.x(), a.y(), width, Math.min(HEIGHT, a.height()));
                    ctx.onClick(
                        hit,
                        e -> {
                          ctx.blur();
                          back.run();
                        });
                  });

          Style borderStyle = Style.empty().withFg(new Color.Gray());
          Style textStyle = Style.empty().withFg(new Color.Gray());
          Style hintStyle = Style.empty().withFg(new Color.DarkGray());

          Widget w =
              (area, buffer) -> {
                int width = Math.min(buttonWidth, area.width());
                if (width <= 0 || area.height() <= 0) return;
                Rect buttonArea =
                    new Rect(area.x(), area.y(), width, Math.min(HEIGHT, area.height()));

                Block block =
                    Block.empty()
                        .withBorders(Borders.ALL)
                        .withBorderType(BorderType.Rounded)
                        .withBorderStyle(borderStyle);
                block.render(buttonArea, buffer);

                Paragraph.of(" ← " + label)
                    .withStyle(textStyle)
                    .render(block.inner(buttonArea), buffer);

                int hintX = buttonArea.x() + buttonArea.width() + 1;
                int hintRoom = area.x() + area.width() - hintX;
                if (hintRoom >= 5 && area.height() >= 2) {
                  Rect hintArea = new Rect(hintX, area.y() + 1, Math.min(5, hintRoom), 1);
                  Paragraph.of("(esc)").withStyle(hintStyle).render(hintArea, buffer);
                }
              };
          return widget(w);
        });
  }
}
