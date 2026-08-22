package jatatui.components.picker;

import jatatui.core.layout.Size;
import jatatui.react.Element;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/// Props for [Picker].
///
/// A search-input + ranked-list modal — VSCode's "Quick Pick", IntelliJ's "Go To Symbol",
/// Sublime's "Go to Anything". Text input at top, results below, Up/Down navigates, Enter
/// commits, Esc cancels, click commits.
///
/// **Type parameter `T` is the item type** — it threads through [Filter], [RowRenderer],
/// and `onSelect`, so the compiler enforces that you render and act on the same kind of value
/// you filter for.
///
/// [Filter] is the filter/rank strategy. Given the live query string, return the items to show
/// in order of relevance. Callers plug in whatever scorer fits (substring, regex,
/// IntelliJ-style fuzzy, domain-specific). Empty-query behaviour is the caller's call too:
/// return all items, or an empty list ("type to search"), or recent-history items.
///
/// [RowRenderer] is how each result row paints itself. It receives the selection state so the
/// row can highlight when chosen. The Picker never imposes its own row styling.
///
/// `onSelect` fires when the user commits (Enter on the highlighted row, or click). The Picker
/// does NOT close itself — the host decides whether to hide it (typical) or keep it open for
/// multi-select. Same for `onCancel` (Esc or backdrop click).
///
/// `hint` is the bottom hint line. The default ("up/down navigate · enter select · esc cancel")
/// is shown via [#of]; pass [Optional#empty] to drop the line, or any string to localize.
public record PickerProps<T>(
    String title,
    Filter<T> filter,
    RowRenderer<T> rowRenderer,
    Consumer<T> onSelect,
    Runnable onCancel,
    Size size,
    int maxVisible,
    Optional<String> hint) {

  /// Minimal-args factory: title, filter, rowRenderer, onSelect, onCancel. Defaults to an
  /// 80×20 modal, 100 visible rows, English hint line.
  public static <T> PickerProps<T> of(
      String title,
      Filter<T> filter,
      RowRenderer<T> rowRenderer,
      Consumer<T> onSelect,
      Runnable onCancel) {
    return new PickerProps<>(
        title,
        filter,
        rowRenderer,
        onSelect,
        onCancel,
        new Size(80, 20),
        100,
        Optional.of("  up/down navigate · enter select · esc cancel"));
  }

  public PickerProps<T> withSize(Size size) {
    return new PickerProps<>(
        title, filter, rowRenderer, onSelect, onCancel, size, maxVisible, hint);
  }

  /// Cap on the number of rows rendered. Beyond ~100 the user re-types instead of scrolling;
  /// rendering more just costs frames.
  public PickerProps<T> withMaxVisible(int maxVisible) {
    return new PickerProps<>(
        title, filter, rowRenderer, onSelect, onCancel, size, maxVisible, hint);
  }

  /// Replace the bottom hint line. Pass [Optional#empty] to drop it entirely.
  public PickerProps<T> withHint(Optional<String> hint) {
    return new PickerProps<>(
        title, filter, rowRenderer, onSelect, onCancel, size, maxVisible, hint);
  }

  public PickerProps<T> withHint(String hint) {
    return withHint(Optional.of(hint));
  }

  public PickerProps<T> withoutHint() {
    return withHint(Optional.empty());
  }

  // ---- Type-safe lambda surrogates ----

  /// Given the current query, produce the items to display. Order matters — first comes first.
  /// Empty query semantics is the caller's call.
  @FunctionalInterface
  public interface Filter<T> {
    List<T> apply(String query);
  }

  /// Paint one result row. `selected=true` when this row is highlighted; the renderer chooses
  /// how to indicate selection (bg colour, caret, bold, etc.).
  @FunctionalInterface
  public interface RowRenderer<T> {
    Element render(T item, boolean selected);
  }
}
