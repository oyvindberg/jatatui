package jatatui.components.modal;

import static jatatui.react.Components.*;

import jatatui.components.button.Button;
import jatatui.core.layout.Size;
import jatatui.core.style.Color;
import jatatui.core.style.Modifier;
import jatatui.core.style.Style;
import jatatui.react.Element;

/// Yes/No confirmation modal for destructive or commit actions. Built on [Modal]; the host
/// owns the open/closed state (typically a `useState<Optional<...>>` whose presence drives
/// `open`).
///
/// `danger=true` paints the box border red and gives the confirm button its primary-green
/// styling overridden to red — used for delete / drop / wipe. `danger=false` gives a neutral
/// cyan box (e.g. "apply changes?").
///
/// Layout: title bar (modal chrome), message, two side-by-side buttons (`confirm` primary,
/// `cancel` secondary), hint line at the bottom.
public final class ConfirmDialog {
  private ConfirmDialog() {}

  public static final Size DEFAULT_SIZE = new Size(60, 11);

  public static Element of(
      boolean open,
      String title,
      String message,
      String confirmLabel,
      String cancelLabel,
      boolean danger,
      Runnable onConfirm,
      Runnable onCancel) {
    Element body =
        column(
            length(1, empty()),
            length(1, text("  " + message, Style.empty().withFg(new Color.White()))),
            length(1, empty()),
            length(
                3,
                row(
                    fill(1, empty()),
                    length(16, Button.of(confirmLabel, "confirm-yes", true, onConfirm)),
                    length(2, empty()),
                    length(14, Button.of(cancelLabel, "confirm-no", false, onCancel)),
                    fill(1, empty()))),
            fill(1, empty()),
            length(
                1,
                text("  enter confirm · esc cancel", Style.empty().withFg(new Color.DarkGray()))));

    Style boxStyle =
        Style.empty()
            .withFg(danger ? new Color.Red() : new Color.Cyan())
            .withAddModifier(Modifier.BOLD);
    return Modal.of(
        ModalProps.of(open, title, body, onCancel).withSize(DEFAULT_SIZE).withBoxStyle(boxStyle));
  }
}
