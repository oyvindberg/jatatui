package jatatui.components;

import jatatui.components.button.Button;
import jatatui.components.chrome.BackButton;
import jatatui.components.chrome.ScreenFrame;
import jatatui.components.dropdown.Dropdown;
import jatatui.components.dropdown.DropdownProps;
import jatatui.components.form.FormProvider;
import jatatui.components.gauge.GaugeComponent;
import jatatui.components.gauge.GaugeProps;
import jatatui.components.gauge.LineGaugeComponent;
import jatatui.components.gauge.LineGaugeProps;
import jatatui.components.link.Link;
import jatatui.components.list.ListComponent;
import jatatui.components.list.ListProps;
import jatatui.components.modal.ConfirmDialog;
import jatatui.components.modal.Modal;
import jatatui.components.modal.ModalProps;
import jatatui.components.picker.Picker;
import jatatui.components.picker.PickerProps;
import jatatui.components.router.Router;
import jatatui.components.router.Screen;
import jatatui.components.scrollable.Scrollable;
import jatatui.components.selectablelist.SelectableList;
import jatatui.components.selectablelist.SelectableListProps;
import jatatui.components.table.Table;
import jatatui.components.table.TableProps;
import jatatui.components.textinput.TextInputComponent;
import jatatui.components.textinput.TextInputProps;
import jatatui.components.theme.Theme;
import jatatui.components.theme.ThemeProvider;
import jatatui.components.toast.ToastsProvider;
import jatatui.core.layout.Constraint;
import jatatui.core.style.Style;
import jatatui.react.Element;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntConsumer;

/// Static factories for the higher-level components in `jatatui-components`. Mirrors the role of
/// [jatatui.react.Components] but for components that wrap concrete widgets rather than the
/// react-layer primitives.
///
/// Import statically: `import static jatatui.components.Components.*;`
public final class Components {
  private Components() {}

  // ---------- Table ----------

  /// Typed-row factory. See [TableProps].
  public static <T> Element table(TableProps<T> props) {
    return Table.of(props);
  }

  /// Quick-path factory taking pre-stringified rows.
  public static Element table(
      String title,
      List<String> headers,
      List<List<String>> rows,
      List<Constraint> columnWidths,
      int selectedRow,
      IntConsumer onSelectChange,
      Optional<IntConsumer> onActivate,
      Optional<String> focusId,
      boolean autoFocus) {
    return Table.ofStrings(
        title,
        headers,
        rows,
        columnWidths,
        selectedRow,
        onSelectChange,
        onActivate,
        focusId,
        autoFocus);
  }

  // ---------- List ----------

  /// Typed-item factory. See [ListProps].
  public static <T> Element list(ListProps<T> props) {
    return ListComponent.of(props);
  }

  /// Quick-path factory taking string items, with no activation callback. Auto-focused.
  public static Element list(
      String title, List<String> items, int selected, IntConsumer onSelectChange) {
    return ListComponent.ofStrings(title, items, selected, onSelectChange);
  }

  /// Quick-path factory taking string items, with an activation callback. Auto-focused.
  public static Element list(
      String title,
      List<String> items,
      int selected,
      IntConsumer onSelectChange,
      Runnable onActivate) {
    return ListComponent.ofStrings(title, items, selected, onSelectChange, onActivate);
  }

  /// Typed convenience: arbitrary `T` items rendered via `labelFn`. Auto-focused.
  public static <T> Element list(
      String title,
      List<T> items,
      Function<T, String> labelFn,
      int selected,
      IntConsumer onSelectChange,
      Runnable onActivate) {
    return ListComponent.of(
        new ListProps<>(
            title,
            items,
            labelFn,
            selected,
            onSelectChange,
            Optional.of(onActivate),
            Optional.empty(),
            true));
  }

  // ---------- Gauge ----------

  /// Full-control factory for the block-style gauge. See [GaugeProps].
  public static Element gauge(GaugeProps props) {
    return GaugeComponent.of(props);
  }

  /// Quick-path: just a ratio in `[0, 1]`. No title, no custom label, default styles.
  public static Element gauge(double ratio) {
    return GaugeComponent.of(GaugeProps.of(ratio));
  }

  /// Quick-path: title + ratio + custom label + filled-bar style.
  public static Element gauge(String title, double ratio, String label, Style gaugeStyle) {
    return GaugeComponent.of(
        GaugeProps.of(ratio).withTitle(title).withLabel(label).withGaugeStyle(gaugeStyle));
  }

  /// Full-control factory for the single-line gauge. See [LineGaugeProps].
  public static Element lineGauge(LineGaugeProps props) {
    return LineGaugeComponent.of(props);
  }

  /// Quick-path: just a ratio in `[0, 1]`.
  public static Element lineGauge(double ratio) {
    return LineGaugeComponent.of(LineGaugeProps.of(ratio));
  }

  /// Quick-path: title + ratio + filled-bar style.
  public static Element lineGauge(String title, double ratio, Style filledStyle) {
    return LineGaugeComponent.of(
        LineGaugeProps.of(ratio).withTitle(title).withFilledStyle(filledStyle));
  }

  // ---------- Router ----------

  /// Mount a stack-based navigation router with a base [Screen]. Descendants get a
  /// `RouterApi.useRouter(ctx)` for `push` / `pop` / `replace`.
  public static Element router(Screen baseScreen) {
    return Router.of(baseScreen);
  }

  /// Quick-path: mount router with a labeled base.
  public static Element router(String baseLabel, Element baseBody) {
    return Router.of(Screen.of(baseLabel, baseBody));
  }

  // ---------- Form ----------

  /// Mount a form-state provider. Descendants read field state via `FormApi.useField(ctx, name,
  /// default)`. `validate` runs after every change; `onSubmit` only runs when there are no errors.
  public static Element formProvider(
      java.util.Map<String, ?> initialValues,
      java.util.function.Function<java.util.Map<String, Object>, java.util.Map<String, String>>
          validate,
      java.util.function.Consumer<java.util.Map<String, Object>> onSubmit,
      Element child) {
    return FormProvider.of(toObjectMap(initialValues), validate, onSubmit, child);
  }

  /// Quick-path: form provider with no validation.
  public static Element formProvider(
      java.util.Map<String, ?> initialValues,
      java.util.function.Consumer<java.util.Map<String, Object>> onSubmit,
      Element child) {
    return FormProvider.of(toObjectMap(initialValues), FormProvider.NO_VALIDATION, onSubmit, child);
  }

  private static java.util.Map<String, Object> toObjectMap(java.util.Map<String, ?> m) {
    java.util.Map<String, Object> out = new java.util.HashMap<>(m.size());
    m.forEach(out::put);
    return java.util.Map.copyOf(out);
  }

  // ---------- Theme ----------

  /// Provide the given [Theme] to descendants. Read it inside any component with
  /// `Theme.useTheme(ctx)`.
  public static Element themeProvider(Theme theme, Element child) {
    return ThemeProvider.of(theme, child);
  }

  // ---------- Toasts ----------

  /// Mount near the app root. Provides a [jatatui.components.toast.ToastApi] via Context to
  /// children, and renders active toasts in the bottom-right corner via a Portal.
  public static Element toastsProvider(Element child) {
    return ToastsProvider.of(child);
  }

  // ---------- Modal ----------

  /// Full-control factory. See [ModalProps].
  public static Element modal(ModalProps props) {
    return Modal.of(props);
  }

  /// Quick-path: open + title + body + onDismiss. 40x12 default size.
  public static Element modal(boolean open, String title, Element body, Runnable onDismiss) {
    return Modal.of(ModalProps.of(open, title, body, onDismiss));
  }

  // ---------- Button ----------

  /// Bordered, Tab-focusable button. Enter or click activates. `primary=true` is intended for
  /// the dominant action on a form.
  public static Element button(String label, String focusId, boolean primary, Runnable onClick) {
    return Button.of(label, focusId, primary, onClick);
  }

  // ---------- Chrome (BackButton + ScreenFrame) ----------

  /// "← label" chip in the upper-left. Clickable; not Tab-focusable (host owns the Esc / b
  /// keyboard binding).
  public static Element backButton(String label, Runnable back) {
    return BackButton.of(label, back);
  }

  /// Standard top-of-screen chrome: BackButton + gutter + content.
  public static Element screenFrame(String backLabel, Runnable back, Element content) {
    return ScreenFrame.of(backLabel, back, content);
  }

  /// Variant with a brand/title strip alongside the back button.
  public static Element screenFrame(
      String backLabel, Runnable back, Element title, Element content) {
    return ScreenFrame.withTitle(backLabel, back, title, content);
  }

  // ---------- ConfirmDialog ----------

  /// Yes/No confirmation modal. `danger=true` paints the box red for destructive actions.
  public static Element confirmDialog(
      boolean open,
      String title,
      String message,
      String confirmLabel,
      String cancelLabel,
      boolean danger,
      Runnable onConfirm,
      Runnable onCancel) {
    return ConfirmDialog.of(
        open, title, message, confirmLabel, cancelLabel, danger, onConfirm, onCancel);
  }

  // ---------- Link ----------

  /// Focusable + clickable activation target. `onActivate` runs on Enter (when focused) or on
  /// click. The most common use is `() -> router.push(screen)`, but any Runnable works.
  public static Element link(
      boolean autoFocus,
      Runnable onActivate,
      java.util.function.Function<Boolean, Element> content) {
    return Link.focusable(autoFocus, onActivate, content);
  }

  /// Link with an explicit focus id.
  public static Element link(
      String focusId,
      boolean autoFocus,
      Runnable onActivate,
      java.util.function.Function<Boolean, Element> content) {
    return Link.focusable(focusId, autoFocus, onActivate, content);
  }

  // ---------- Scrollable ----------

  /// Wheel-scrollable column. Max offset clamped against the assigned area's height. For
  /// selectable lists where Up/Down should move selection, use [#selectableList] instead.
  public static Element scrollable(java.util.List<Element> children) {
    return Scrollable.column(children);
  }

  // ---------- Picker ----------

  /// Search-input + ranked-list modal. See [PickerProps]. Host renders this conditionally
  /// (when their search hotkey fires); the picker owns query / cursor state and the modal
  /// chrome but doesn't auto-close — the host decides whether `onSelect` / `onCancel` should
  /// hide it.
  public static <T> Element picker(PickerProps<T> props) {
    return Picker.of(props);
  }

  // ---------- SelectableList ----------

  /// Heterogeneous-row list with skip-non-activatable Up/Down navigation. See
  /// [SelectableListProps]. Sibling of [#list] (which takes labelled strings); this one takes
  /// arbitrary Elements per row plus a predicate that controls which rows participate in
  /// keyboard / click navigation.
  public static <T> Element selectableList(SelectableListProps<T> props) {
    return SelectableList.of(props);
  }

  // ---------- Dropdown ----------

  /// Full-control dropdown / select. See [DropdownProps].
  public static <T> Element dropdown(DropdownProps<T> props) {
    return Dropdown.of(props);
  }

  /// Quick-path for the common `List<String>` case: label + items + selected + onChange +
  /// focusId. Auto-focused. For typed options (enums, records), build a [DropdownProps] via
  /// [DropdownProps#of] with a `labelFn` and pass it to [#dropdown(DropdownProps)].
  public static Element dropdown(
      String label,
      List<String> items,
      int selectedIndex,
      java.util.function.IntConsumer onChange,
      String focusId) {
    return Dropdown.of(
        DropdownProps.ofStrings(label, items, selectedIndex, onChange)
            .withFocusId(focusId)
            .withAutoFocus(true));
  }

  // ---------- TextInput ----------

  /// Full-control factory for the single-line text input. See [TextInputProps].
  public static Element textInput(TextInputProps props) {
    return TextInputComponent.of(props);
  }

  /// Quick-path: controlled value + onChange. Auto-focused with the given id.
  public static Element textInput(
      String value, java.util.function.Consumer<String> onChange, String focusId) {
    return TextInputComponent.of(
        TextInputProps.of(value, onChange).withFocusId(focusId).withAutoFocus(true));
  }

  /// Quick-path: controlled value + onChange + placeholder. Auto-focused with the given id.
  public static Element textInput(
      String value,
      java.util.function.Consumer<String> onChange,
      String placeholder,
      String focusId) {
    return TextInputComponent.of(
        TextInputProps.of(value, onChange)
            .withPlaceholder(placeholder)
            .withFocusId(focusId)
            .withAutoFocus(true));
  }

  /// Quick-path with a focus-aware bordered title — the field shows a yellow border when focused,
  /// dark gray otherwise. Use this when a labeled bordered field is what you want (most forms).
  public static Element titledTextInput(
      String title,
      String value,
      java.util.function.Consumer<String> onChange,
      String placeholder,
      String focusId) {
    return TextInputComponent.of(
        TextInputProps.of(value, onChange)
            .withTitle(title)
            .withPlaceholder(placeholder)
            .withFocusId(focusId)
            .withAutoFocus(true));
  }
}
