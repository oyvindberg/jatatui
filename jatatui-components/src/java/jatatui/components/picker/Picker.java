package jatatui.components.picker;

import static jatatui.react.Components.*;

import jatatui.components.textinput.TextInputComponent;
import jatatui.components.textinput.TextInputProps;
import jatatui.core.layout.Rect;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.KeyEvent;
import jatatui.react.MouseEvent;
import jatatui.react.State;
import jatatui.widgets.Borders;
import java.util.List;
import tui.crossterm.KeyCode;

/// Search-input + ranked-list modal. The host opens this conditionally (when a search hotkey
/// fires) and drives the result list via [PickerProps.Filter]; this component owns the query
/// state, the selection cursor, and the modal chrome.
///
/// Triggers:
///   - **Text input**: prints into the query field; results re-rank on each keystroke (caller's
///     filter is invoked, so it sees `"c"`, then `"cu"`, …).
///   - **Up/Down**: move the highlight. Bubble-phase + stopPropagation, so a wrapping screen's
///     own arrow handlers don't fire while the picker is open.
///   - **Enter**: commits `onSelect(highlighted)`; doesn't auto-close (host decides).
///   - **Esc**: invokes `onCancel`.
///   - **Click on a row**: commits that row.
///   - **Click outside the modal**: invokes `onCancel`.
///
/// The picker doesn't have a single source of truth for "is the picker open" — render it
/// conditionally on the host side. The picker only deals with what to show while it IS
/// visible. This matches [jatatui.components.modal.Modal]'s approach.
public final class Picker {
  private Picker() {}

  /// Focus id for the internal text input. Stable across renders so that a host that wants to
  /// imperatively focus or blur the picker's input can target it directly.
  public static final String QUERY_FOCUS_ID = "picker:query";

  public static <T> Element of(PickerProps<T> props) {
    return component(
        ctx -> {
          State<String> query = ctx.useState(() -> "");
          State<Integer> selectedIdx = ctx.useState(() -> 0);

          // Forcibly claim focus for the query field on mount. The text input registers with
          // autoFocus=true, but FocusManager's eager-claim only fires when nothing else is
          // focused — if the host has another autoFocus'd element rendered first (e.g. a list
          // underneath the modal), the input never gets focus and keystrokes leak to host
          // handlers. useEffect with empty deps runs once per mount (and again after any
          // unmount/remount — reconciliation handles both).
          ctx.useEffect(() -> ctx.focus(QUERY_FOCUS_ID));

          List<T> results = props.filter().apply(query.get());
          int cap = Math.min(props.maxVisible(), results.size());
          List<T> capped = results.subList(0, cap);

          int sel =
              capped.isEmpty() ? 0 : Math.max(0, Math.min(selectedIdx.get(), capped.size() - 1));

          ctx.onKey(
              new KeyCode.Esc(),
              (KeyEvent e) -> {
                props.onCancel().run();
                e.stopPropagation();
              });
          ctx.onKey(
              new KeyCode.Up(),
              (KeyEvent e) -> {
                if (!capped.isEmpty()) selectedIdx.set(Math.max(0, sel - 1));
                e.stopPropagation();
              });
          ctx.onKey(
              new KeyCode.Down(),
              (KeyEvent e) -> {
                if (!capped.isEmpty()) selectedIdx.set(Math.min(capped.size() - 1, sel + 1));
                e.stopPropagation();
              });
          ctx.onKey(
              new KeyCode.Enter(),
              (KeyEvent e) -> {
                if (!capped.isEmpty()) props.onSelect().accept(capped.get(sel));
                e.stopPropagation();
              });

          Element input =
              length(
                  3,
                  TextInputComponent.of(
                      TextInputProps.of(
                              query.get(),
                              v -> {
                                query.set(v);
                                selectedIdx.set(0); // reset cursor when the query changes
                              })
                          .withTitle("")
                          .withFocusId(QUERY_FOCUS_ID)
                          .withAutoFocus(true)));

          Element resultsPane =
              capped.isEmpty()
                  ? fill(
                      1,
                      text(
                          "  no matches",
                          Style.empty().withFg(new jatatui.core.style.Color.DarkGray())))
                  : fill(1, resultRows(props, capped, sel));

          Element modalBody;
          if (props.hint().isPresent()) {
            modalBody =
                stack(
                    widget(jatatui.widgets.Clear.instance()),
                    box(
                        props.title(),
                        Borders.ALL,
                        input,
                        resultsPane,
                        length(
                            1,
                            text(
                                props.hint().get(),
                                Style.empty().withFg(new jatatui.core.style.Color.DarkGray())))));
          } else {
            modalBody =
                stack(
                    widget(jatatui.widgets.Clear.instance()),
                    box(props.title(), Borders.ALL, input, resultsPane));
          }

          // Two portals layered on screen: backdrop for outside-click cancellation, then the
          // modal box itself. Same pattern as Modal.
          Rect screen = ctx.frame().area();
          Rect modalArea = centerInside(screen, props.size().width(), props.size().height());

          Element backdrop =
              component(
                  c -> {
                    c.onClick(
                        e -> {
                          props.onCancel().run();
                          e.stopPropagation();
                        });
                    return empty();
                  });

          // Eat clicks on the box so they don't bubble to the backdrop.
          Element boxLayer =
              component(
                  c -> {
                    c.onClick(e -> e.stopPropagation());
                    return modalBody;
                  });

          return column(portal(backdrop, screen), portal(boxLayer, modalArea));
        });
  }

  /// One clickable row per result. Click → `onSelect` for that row; the picker does NOT close
  /// itself (host's call).
  private static <T> Element resultRows(PickerProps<T> props, List<T> items, int selectedIdx) {
    Element[] rows = new Element[items.size()];
    for (int i = 0; i < items.size(); i++) {
      final int idx = i;
      final T item = items.get(i);
      final boolean selected = idx == selectedIdx;
      rows[i] =
          length(
              1,
              component(
                  c -> {
                    c.onClick(
                        (MouseEvent e) -> {
                          props.onSelect().accept(item);
                          e.stopPropagation();
                        });
                    return props.rowRenderer().render(item, selected);
                  }));
    }
    return column(rows);
  }

  private static Rect centerInside(Rect outer, int width, int height) {
    int w = Math.min(width, outer.width());
    int h = Math.min(height, outer.height());
    int x = outer.x() + (outer.width() - w) / 2;
    int y = outer.y() + (outer.height() - h) / 2;
    return new Rect(x, y, w, h);
  }
}
