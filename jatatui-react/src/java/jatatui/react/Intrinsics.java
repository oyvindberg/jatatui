package jatatui.react;

import jatatui.core.layout.Constraint;
import jatatui.core.layout.Flex;
import jatatui.core.layout.Layout;
import jatatui.core.layout.Margin;
import jatatui.core.layout.Rect;
import jatatui.core.layout.Spacing;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.core.text.Line;
import jatatui.widgets.Borders;
import jatatui.widgets.block.Block;
import jatatui.widgets.paragraph.Paragraph;
import jatatui.widgets.paragraph.Wrap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/// Built-in Components and their props records.
///
/// Each Component is a `static final` constant — stable identity across renders, which is what
/// auto-memoization on [Element.Of] keys off of. Construct via the [Components] factories rather
/// than by hand.
///
/// Note that there is no special "intrinsic" status visible to users — these are just Components
/// that ship with the framework. The few that genuinely need to paint cells return an
/// [Element.Host] wrapping an [Intrinsic]; the rest compose other Elements.
public final class Intrinsics {
  private Intrinsics() {}

  // -------------------- Empty --------------------

  public record EmptyProps() {}

  public static final Component<EmptyProps> EMPTY = (props, ctx) -> new Element.Host((c, a) -> {});

  // -------------------- Text --------------------

  public record TextProps(String content, Style style) {}

  public static final Component<TextProps> TEXT =
      (props, ctx) ->
          new Element.Host(
              (c, area) ->
                  Paragraph.of(props.content()).withStyle(props.style()).render(area, c.buffer()));

  // -------------------- Paragraph (multi-line wrapped) --------------------

  public record ParagraphProps(String content, Style style, boolean wrap) {}

  public static final Component<ParagraphProps> PARAGRAPH =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                var p = Paragraph.of(props.content()).withStyle(props.style());
                if (props.wrap()) p = p.withWrap(new Wrap(true));
                p.render(area, c.buffer());
              });

  // -------------------- Button --------------------

  /// Holds a `Consumer<MouseEvent>` so handlers that care can `stopPropagation` or read modifiers.
  /// The `Runnable`-flavored `Components.button(...)` factory wraps a Runnable into a Consumer
  /// that ignores the event.
  public record ButtonProps(
      String label, Style style, java.util.function.Consumer<MouseEvent> onClick) {}

  public static final Component<ButtonProps> BUTTON =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                Paragraph.of(props.label()).withStyle(props.style()).render(area, c.buffer());
                c.onClick(area, props.onClick());
              });

  // -------------------- Box (block-with-borders + vertical content) --------------------

  public record BoxProps(
      String title, Borders borders, Flex flex, Spacing spacing, List<Element> children) {
    public BoxProps withFlex(Flex f) {
      return new BoxProps(title, borders, f, spacing, children);
    }

    public BoxProps withSpacing(int cells) {
      return new BoxProps(title, borders, flex, Spacing.fromSigned(cells), children);
    }
  }

  public static final Component<BoxProps> BOX =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                Block block =
                    Block.empty().withTitle(Line.from(props.title())).withBorders(props.borders());
                block.render(area, c.buffer());
                Rect inner = block.inner(area);
                renderChildrenVertical(c, inner, props.children(), props.flex(), props.spacing());
              });

  // -------------------- Column / Row (vertical / horizontal layout) --------------------

  public record ColumnProps(List<Element> children, Flex flex, Spacing spacing, Margin margin) {
    public ColumnProps withFlex(Flex f) {
      return new ColumnProps(children, f, spacing, margin);
    }

    public ColumnProps withSpacing(int cells) {
      return new ColumnProps(children, flex, Spacing.fromSigned(cells), margin);
    }

    public ColumnProps withMargin(Margin m) {
      return new ColumnProps(children, flex, spacing, m);
    }
  }

  public static final Component<ColumnProps> COLUMN =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                Rect inner = applyMargin(area, props.margin());
                renderChildrenVertical(c, inner, props.children(), props.flex(), props.spacing());
              });

  public record RowProps(List<Element> children, Flex flex, Spacing spacing, Margin margin) {
    public RowProps withFlex(Flex f) {
      return new RowProps(children, f, spacing, margin);
    }

    public RowProps withSpacing(int cells) {
      return new RowProps(children, flex, Spacing.fromSigned(cells), margin);
    }

    public RowProps withMargin(Margin m) {
      return new RowProps(children, flex, spacing, m);
    }
  }

  public static final Component<RowProps> ROW =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                if (props.children().isEmpty()) return;
                Rect inner = applyMargin(area, props.margin());
                Constraint[] cs = Element.constraintsOf(props.children());
                Layout layout =
                    Layout.horizontal(cs).withFlex(props.flex()).withSpacing(props.spacing());
                Rect[] split = inner.layout(layout, props.children().size());
                for (int i = 0; i < props.children().size(); i++) {
                  c.renderChild(i, props.children().get(i), split[i]);
                }
              });

  // -------------------- Tabs --------------------

  public record TabsProps(int selected, List<Tab> tabs) {}

  public record Tab(String label, Element body) {}

  public static final Component<TabsProps> TABS =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                Rect[] split =
                    area.layout(
                        Layout.vertical(new Constraint.Length(1), new Constraint.Fill(1)), 2);
                StringBuilder header = new StringBuilder();
                for (int i = 0; i < props.tabs().size(); i++) {
                  if (i > 0) header.append(" │ ");
                  header.append(
                      i == props.selected()
                          ? "[" + props.tabs().get(i).label() + "]"
                          : props.tabs().get(i).label());
                }
                Paragraph.of(header.toString())
                    .withStyle(Style.empty().withFg(Color.GRAY))
                    .render(split[0], c.buffer());
                if (props.selected() >= 0 && props.selected() < props.tabs().size()) {
                  c.renderChild(
                      props.selected(), props.tabs().get(props.selected()).body(), split[1]);
                }
              });

  // -------------------- ForEach (keyed list) --------------------

  public record ForEachProps<T>(
      List<T> items, Function<T, String> keyFn, Function<T, Element> render, int rowHeight) {}

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static final Component<ForEachProps<?>> FOR_EACH =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                ForEachProps p = (ForEachProps) props;
                if (p.items().isEmpty()) return;
                Constraint[] cs = new Constraint[p.items().size()];
                for (int i = 0; i < cs.length; i++) cs[i] = new Constraint.Length(p.rowHeight());
                Rect[] split = area.layout(Layout.vertical(cs), p.items().size());
                for (int i = 0; i < p.items().size(); i++) {
                  Object item = p.items().get(i);
                  c.renderChild(
                      ((Function<Object, String>) p.keyFn()).apply(item),
                      ((Function<Object, Element>) p.render()).apply(item),
                      split[i]);
                }
              });

  // -------------------- When / IfElse --------------------

  public record WhenProps(boolean condition, Element child) {}

  public static final Component<WhenProps> WHEN =
      (props, ctx) ->
          props.condition()
              ? props.child()
              : new Element.Of<>(EMPTY, new EmptyProps(), Optional.empty());

  public record IfElseProps(boolean condition, Element thenChild, Element elseChild) {}

  public static final Component<IfElseProps> IF_ELSE =
      (props, ctx) -> props.condition() ? props.thenChild() : props.elseChild();

  // -------------------- Stack (children layered in same area) --------------------

  /// Children render in declaration order, all into the SAME area. Last paints over earlier.
  /// Useful for overlays (modal box on top of a Clear; tooltip on top of content).
  public record StackProps(List<Element> children) {}

  public static final Component<StackProps> STACK =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                for (int i = 0; i < props.children().size(); i++) {
                  c.renderChild(i, props.children().get(i), area);
                }
              });

  // -------------------- Provider (Context) --------------------

  public record ProviderProps<T>(Context<T> context, T value, Element child) {}

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static final Component<ProviderProps<?>> PROVIDER =
      (props, ctx) ->
          new Element.Host(
              (c, area) -> {
                ProviderProps p = (ProviderProps) props;
                c.pushContext(p.context(), p.value());
                try {
                  c.renderChild("provider", p.child(), area);
                } finally {
                  c.popContext(p.context());
                }
              });

  // -------------------- Portal (deferred render to absolute area) --------------------

  public record PortalProps(Element child, Rect area) {}

  public static final Component<PortalProps> PORTAL =
      (props, ctx) -> new Element.Host((c, area) -> c.queuePortal(props.child(), props.area()));

  // -------------------- Widget wrap --------------------

  public record WidgetWrapProps(jatatui.core.widgets.Widget widget) {}

  public static final Component<WidgetWrapProps> WIDGET_WRAP =
      (props, ctx) -> new Element.Host((c, area) -> props.widget().render(area, c.buffer()));

  // -------------------- Memo / PureComponent shims --------------------

  /// `Memo` — explicit deps-keyed memoization. Builds a Component<List<Object>> whose body is the
  /// lambda; deps comparison happens at the Element.Of auto-memo layer.
  ///
  /// NOTE: each call to `memo(...)` creates a fresh Component lambda whose identity differs from
  /// the previous render's. Auto-memo on (type, props) won't hit. So `memo` keys via the per-Fiber
  /// memo cache directly — see [RenderContext.HookStore.memoOrCompute].
  public record MemoProps(Object[] deps, Supplier<Element> body) {}

  public static final Component<MemoProps> MEMO =
      (props, ctx) -> ctx.hooks.memoOrCompute(ctx.fiber, props.deps(), props.body());

  /// `PureComponent` — wraps a (props, body) pair into an Element.Of. Like `memo` but the deps
  /// are a single user-record whose `equals()` decides invalidation.
  public record PureProps<P>(P userProps, Function<P, Element> body) {}

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static final Component<PureProps<?>> PURE =
      (props, ctx) -> {
        PureProps p = (PureProps) props;
        return ctx.hooks.memoOrCompute(
            ctx.fiber,
            new Object[] {p.userProps()},
            () -> ((Function<Object, Element>) p.body()).apply(p.userProps()));
      };

  /// Function-component shim: `component(ctx -> body)`. Body has a fresh closure each render so
  /// auto-memo never hits — the body just runs every render. Use `apply(MyComp, props)` for
  /// memoized user components.
  public record FunctionProps(Function<RenderContext, Element> body) {}

  public static final Component<FunctionProps> FUNCTION = (props, ctx) -> props.body().apply(ctx);

  // -------------------- Shared helpers --------------------

  static void renderChildrenVertical(
      RenderContext ctx, Rect area, List<Element> kids, Flex flex, Spacing spacing) {
    if (kids.isEmpty()) return;
    Constraint[] cs = Element.constraintsOf(kids);
    Layout layout = Layout.vertical(cs).withFlex(flex).withSpacing(spacing);
    Rect[] split = area.layout(layout, kids.size());
    for (int i = 0; i < kids.size(); i++) {
      ctx.renderChild(i, kids.get(i), split[i]);
    }
  }

  static Rect applyMargin(Rect area, Margin m) {
    if (m.horizontal() == 0 && m.vertical() == 0) return area;
    int x = area.x() + m.horizontal();
    int y = area.y() + m.vertical();
    int w = Math.max(0, area.width() - 2 * m.horizontal());
    int h = Math.max(0, area.height() - 2 * m.vertical());
    return new Rect(x, y, w, h);
  }
}
