package jatatui.components.modal;

import jatatui.core.layout.Size;
import jatatui.core.style.Style;
import jatatui.react.Element;
import java.util.Optional;

/// Props for [Modal]. [#size] is the requested cell-count; the modal centers itself on the
/// screen, shrinking to fit if the screen is smaller than the requested size.
public record ModalProps(
    boolean open,
    String title,
    Element body,
    Runnable onDismiss,
    Size size,
    Optional<Style> backdropStyle,
    Style boxStyle) {

  public static final Size DEFAULT_SIZE = new Size(40, 12);

  /// Default-styled factory: 40×12 modal, no backdrop dimming, Esc / click-outside dismiss.
  public static ModalProps of(boolean open, String title, Element body, Runnable onDismiss) {
    return new ModalProps(
        open, title, body, onDismiss, DEFAULT_SIZE, Optional.empty(), Style.empty());
  }

  public ModalProps withSize(Size size) {
    return new ModalProps(open, title, body, onDismiss, size, backdropStyle, boxStyle);
  }

  /// Quick-path that takes width / height as cell counts.
  public ModalProps withSize(int width, int height) {
    return withSize(new Size(width, height));
  }

  public ModalProps withBackdropStyle(Style backdropStyle) {
    return new ModalProps(open, title, body, onDismiss, size, Optional.of(backdropStyle), boxStyle);
  }

  public ModalProps withBoxStyle(Style boxStyle) {
    return new ModalProps(open, title, body, onDismiss, size, backdropStyle, boxStyle);
  }
}
