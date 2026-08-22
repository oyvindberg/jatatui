package jatatui.react;

import jatatui.core.layout.Constraint;
import jatatui.core.layout.Rect;
import java.util.List;
import java.util.Optional;

/// React-style element — the data your components return.
///
/// Three permits, deliberately minimal:
///   - [Of] — application of a [Component] to its props (the universal "user component" form)
///   - [Host] — a leaf that paints directly via an [Intrinsic] (escape hatch; rarely
///     authored by users)
///   - [Sized] — pure layout metadata: a [Constraint] attached to a child, read by parent layouts
///
/// All built-in factories (`text`, `box`, `column`, `row`, `tabs`, `forEach`, `button`, etc.)
/// produce [Of] values whose Component is a built-in constant in [Intrinsics]. User-defined
/// components likewise produce [Of] values. Built-ins and user code are uniform.
public sealed interface Element permits Element.Of, Element.Host, Element.Sized {

  void render(RenderContext ctx, Rect area);

  // -------------------- The three permits --------------------

  /// Application of a [Component] to its props. Memoized automatically: across renders, if the
  /// `(type, props.equals())` is the same as last frame at this fiber, the previous output is
  /// reused and `type.apply` is skipped.
  ///
  /// `key` is optional — used as the fiber identity in keyed lists; leave empty for index-keyed.
  record Of<P>(Component<P> type, P props, Optional<String> key) implements Element {
    public Of<P> withKey(String key) {
      return new Of<>(type, props, Optional.of(key));
    }

    /// Map the props through `fn`, returning a new Of with the same type and key. Useful for
    /// fluent chaining when the props record exposes its own `withFoo` builders, e.g.
    /// `column(...).with(p -> p.withSpacing(1).withMargin(m))`.
    public Of<P> with(java.util.function.UnaryOperator<P> fn) {
      return new Of<>(type, fn.apply(props), key);
    }

    @Override
    public void render(RenderContext ctx, Rect area) {
      Element produced = ctx.applyAndMemo(this);
      ctx.renderChild(0, produced, area);
    }
  }

  /// Leaf primitive — paints cells directly to the buffer (or registers events / calls
  /// `renderChild` for layout children). Construct via Components factories — most callers should
  /// never wrap an Intrinsic by hand.
  record Host(Intrinsic renderer) implements Element {
    @Override
    public void render(RenderContext ctx, Rect area) {
      renderer.render(ctx, area);
    }
  }

  /// Layout metadata — attaches a [Constraint] to `child`. Containers (column / row / box) read
  /// `Sized` children to feed the [jatatui.core.layout.Layout] solver; bare children default to
  /// `Constraint.Fill(1)`.
  ///
  /// At render time `Sized` is transparent — it delegates to its child.
  record Sized(Constraint constraint, Element child) implements Element {
    @Override
    public void render(RenderContext ctx, Rect area) {
      child.render(ctx, area);
    }
  }

  // -------------------- Helpers used by built-in layouts --------------------

  /// Extract a `Constraint[]` from a list of children, defaulting bare children to `Fill(1)`.
  /// Used by all layout intrinsics (column, row, box's content, tabs' body, forEach).
  static Constraint[] constraintsOf(List<Element> kids) {
    Constraint[] cs = new Constraint[kids.size()];
    for (int i = 0; i < kids.size(); i++) {
      cs[i] = (kids.get(i) instanceof Sized s) ? s.constraint() : new Constraint.Fill(1);
    }
    return cs;
  }
}
