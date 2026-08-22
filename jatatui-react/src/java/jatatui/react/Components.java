package jatatui.react;

import jatatui.core.layout.Constraint;
import jatatui.core.layout.Flex;
import jatatui.core.layout.Margin;
import jatatui.core.layout.Rect;
import jatatui.core.layout.Spacing;
import jatatui.core.style.Style;
import jatatui.widgets.Borders;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/// Static factories for [Element]s. Mirrors React's `React.createElement` / JSX role.
///
/// Import statically: `import static jatatui.react.Components.*;`
///
/// Every factory ultimately produces an [Element.Of] applied to a built-in [Component] from
/// [Intrinsics]. User components are constructed via [#apply(Component, Object)].
public final class Components {
  private Components() {}

  // ---- User components ----

  /// Apply a user-defined Component to its props. Auto-memoized: across renders, if the
  /// `(component reference, props.equals())` is stable, body is skipped.
  public static <P> Element.Of<P> apply(Component<P> type, P props) {
    return new Element.Of<>(type, props, Optional.empty());
  }

  public static <P> Element.Of<P> apply(Component<P> type, P props, String key) {
    return new Element.Of<>(type, props, Optional.of(key));
  }

  /// Quick-and-dirty function component for ad-hoc closures with `useState` etc. The body lambda
  /// has a fresh identity each render so auto-memo never hits — for memoized stuff, define a
  /// `static final Component<MyProps>` and use [#apply(Component, Object)].
  public static Element.Of<Intrinsics.FunctionProps> component(
      Function<RenderContext, Element> body) {
    return new Element.Of<>(
        Intrinsics.FUNCTION, new Intrinsics.FunctionProps(body), Optional.empty());
  }

  /// Explicit deps-keyed memoization. `body` is skipped when `deps` are element-wise equal to the
  /// previous render at this fiber.
  public static Element.Of<Intrinsics.MemoProps> memo(Object[] deps, Supplier<Element> body) {
    return new Element.Of<>(
        Intrinsics.MEMO, new Intrinsics.MemoProps(deps, body), Optional.empty());
  }

  /// Convenience for `memo` deps lists.
  public static Object[] deps(Object... values) {
    return values;
  }

  /// "Pure" component — props record's `equals()` decides re-render.
  public static <P> Element.Of<Intrinsics.PureProps<?>> pureComponent(
      P props, Function<P, Element> body) {
    return new Element.Of<>(
        Intrinsics.PURE, new Intrinsics.PureProps<>(props, body), Optional.empty());
  }

  // ---- Leaves ----

  public static Element.Of<Intrinsics.EmptyProps> empty() {
    return new Element.Of<>(Intrinsics.EMPTY, new Intrinsics.EmptyProps(), Optional.empty());
  }

  public static Element.Of<Intrinsics.TextProps> text(String content) {
    return new Element.Of<>(
        Intrinsics.TEXT, new Intrinsics.TextProps(content, Style.empty()), Optional.empty());
  }

  public static Element.Of<Intrinsics.TextProps> text(String content, Style style) {
    return new Element.Of<>(
        Intrinsics.TEXT, new Intrinsics.TextProps(content, style), Optional.empty());
  }

  public static Element.Of<Intrinsics.ParagraphProps> paragraph(String content, Style style) {
    return new Element.Of<>(
        Intrinsics.PARAGRAPH,
        new Intrinsics.ParagraphProps(content, style, true),
        Optional.empty());
  }

  public static Element.Of<Intrinsics.ButtonProps> button(
      String label, Style style, Runnable onClick) {
    return new Element.Of<>(
        Intrinsics.BUTTON,
        new Intrinsics.ButtonProps(label, style, e -> onClick.run()),
        Optional.empty());
  }

  /// Button overload that delivers the [MouseEvent] — useful for `e.stopPropagation()` or to
  /// inspect modifiers.
  public static Element.Of<Intrinsics.ButtonProps> button(
      String label, Style style, java.util.function.Consumer<MouseEvent> onClick) {
    return new Element.Of<>(
        Intrinsics.BUTTON, new Intrinsics.ButtonProps(label, style, onClick), Optional.empty());
  }

  // ---- Containers ----

  public static Element.Of<Intrinsics.BoxProps> box(
      String title, Borders borders, Element... children) {
    return new Element.Of<>(
        Intrinsics.BOX,
        new Intrinsics.BoxProps(title, borders, Flex.Legacy, Spacing.DEFAULT, List.of(children)),
        Optional.empty());
  }

  public static Element.Of<Intrinsics.ColumnProps> column(Element... children) {
    return new Element.Of<>(
        Intrinsics.COLUMN,
        new Intrinsics.ColumnProps(
            List.of(children), Flex.Legacy, Spacing.DEFAULT, new Margin(0, 0)),
        Optional.empty());
  }

  public static Element.Of<Intrinsics.RowProps> row(Element... children) {
    return new Element.Of<>(
        Intrinsics.ROW,
        new Intrinsics.RowProps(List.of(children), Flex.Legacy, Spacing.DEFAULT, new Margin(0, 0)),
        Optional.empty());
  }

  /// Layer children in the same area. Children render in declaration order; later children paint
  /// over earlier ones. Use for overlays — e.g. `stack(widget(Clear.instance()), box(...))` to
  /// make the box opaque over whatever was painted under it.
  public static Element.Of<Intrinsics.StackProps> stack(Element... children) {
    return new Element.Of<>(
        Intrinsics.STACK, new Intrinsics.StackProps(List.of(children)), Optional.empty());
  }

  public static Element.Of<Intrinsics.TabsProps> tabs(int selected, Intrinsics.Tab... tabs) {
    return new Element.Of<>(
        Intrinsics.TABS, new Intrinsics.TabsProps(selected, List.of(tabs)), Optional.empty());
  }

  public static Intrinsics.Tab tab(String label, Element body) {
    return new Intrinsics.Tab(label, body);
  }

  // ---- Per-child sizing ----

  public static Element sized(Constraint c, Element child) {
    return new Element.Sized(c, child);
  }

  public static Element length(int n, Element child) {
    return new Element.Sized(new Constraint.Length(n), child);
  }

  public static Element fill(int weight, Element child) {
    return new Element.Sized(new Constraint.Fill(weight), child);
  }

  public static Element min(int n, Element child) {
    return new Element.Sized(new Constraint.Min(n), child);
  }

  public static Element max(int n, Element child) {
    return new Element.Sized(new Constraint.Max(n), child);
  }

  public static Element percent(int p, Element child) {
    return new Element.Sized(new Constraint.Percentage(p), child);
  }

  public static Element ratio(int num, int den, Element child) {
    return new Element.Sized(new Constraint.Ratio(num, den), child);
  }

  // ---- Conditional / list ----

  public static Element when(boolean condition, Element then_) {
    return new Element.Of<>(
        Intrinsics.WHEN, new Intrinsics.WhenProps(condition, then_), Optional.empty());
  }

  public static Element ifElse(boolean condition, Element then_, Element else_) {
    return new Element.Of<>(
        Intrinsics.IF_ELSE, new Intrinsics.IfElseProps(condition, then_, else_), Optional.empty());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <T> Element forEach(
      List<T> items, Function<T, String> keyFn, Function<T, Element> render) {
    return new Element.Of<>(
        Intrinsics.FOR_EACH,
        new Intrinsics.ForEachProps(items, keyFn, render, 1),
        Optional.empty());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <T> Element forEach(
      List<T> items, Function<T, String> keyFn, Function<T, Element> render, int rowHeight) {
    return new Element.Of<>(
        Intrinsics.FOR_EACH,
        new Intrinsics.ForEachProps(items, keyFn, render, rowHeight),
        Optional.empty());
  }

  // ---- Key matchers ----

  /// Matcher for `ctx.onKey` that fires on every key press. Useful for text inputs that need to
  /// observe all keys (printable chars, backspace, arrows, etc.).
  public static final java.util.function.Predicate<tui.crossterm.KeyCode> ANY_KEY = code -> true;

  /// Matcher: any printable single character (`KeyCode.Char` regardless of which char).
  public static final java.util.function.Predicate<tui.crossterm.KeyCode> ANY_CHAR =
      code -> code instanceof tui.crossterm.KeyCode.Char;

  // ---- Context ----

  /// Provide `value` for `context` to all components rendered under `child`. Calls to
  /// `ctx.useContext(context)` inside `child` will see this value (or, if nested deeper, the
  /// closest enclosing provider's value).
  public static <T> Element provide(Context<T> context, T value, Element child) {
    return new Element.Of<>(
        Intrinsics.PROVIDER,
        new Intrinsics.ProviderProps<>(context, value, child),
        Optional.empty());
  }

  // ---- Portal ----

  /// Render `child` into `area` (in absolute screen coordinates), AFTER the main pass completes.
  /// Used for modals, tooltips, dropdowns, toasts — anything that needs to escape the parent's
  /// layout and paint over the rest of the UI. Events still bubble through the declaring fiber's
  /// parent chain (not the visual parent at `area`).
  public static Element portal(Element child, Rect area) {
    return new Element.Of<>(
        Intrinsics.PORTAL, new Intrinsics.PortalProps(child, area), Optional.empty());
  }

  // ---- Escape hatch ----

  public static Element widget(jatatui.core.widgets.Widget widget) {
    return new Element.Of<>(
        Intrinsics.WIDGET_WRAP, new Intrinsics.WidgetWrapProps(widget), Optional.empty());
  }
}
