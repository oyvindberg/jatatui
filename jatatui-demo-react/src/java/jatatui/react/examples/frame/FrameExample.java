package jatatui.react.examples.frame;

import static jatatui.react.Components.*;

import jatatui.core.layout.Rect;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import jatatui.widgets.Borders;
import java.io.IOException;
import java.util.Optional;

/// End-to-end demo of the animation + full-mouse additions to jatatui-react:
///
///   - **`useFrame(60, dt)`** drives a flowing gradient bar with NO hand-rolled render loop — the
///     declarative layer schedules the ticks and shortens the poll while mounted.
///   - **`onHover`** highlights the file row under the pointer (move the mouse over the list).
///   - **`onDrag` / `onDragEnd`** scrub the timeline at the bottom: press and drag to move the
///     caret; release to stop. Reads the live pointer x each drag.
///
/// Run: `jatatui-demo-react` launcher → `frame`. Quit with `q` / `Esc` / Ctrl-C.
public final class FrameExample {

  // "Unicorn" palette (matches the dfmt design brief).
  private static final int[] IRIS = {0xa7, 0x8b, 0xfa};
  private static final int[] ROSE = {0xfb, 0x71, 0x85};
  private static final Color MINT = new Color.Rgb(0x34, 0xd3, 0x99);
  private static final Color LILAC = new Color.Rgb(0x6d, 0x5c, 0x9e);
  private static final Color ASH = new Color.Rgb(0x3b, 0x35, 0x50);

  private static final String[] FILES = {
    "models/staging/stg_orders.sql",
    "models/staging/stg_customers.sql",
    "models/marts/fct_orders.sql",
    "models/marts/dim_customers.sql",
    "models/metrics/revenue.sql",
  };

  public static void main(String[] args) throws IOException {
    ReactApp.run(app());
  }

  static Element app() {
    return component(
        ctx -> {
          // Animation clock, advanced by real elapsed ms each frame → frame-rate independent.
          var phaseMs = ctx.useState(() -> 0.0);
          var hoveredRow = ctx.useState(() -> -1);
          var scrub = ctx.useState(() -> 0.35); // 0..1
          var dragging = ctx.useState(() -> false);

          ctx.useFrame(60, elapsed -> phaseMs.update(p -> p + elapsed));

          return box(
              " useFrame + mouse demo ",
              Borders.ALL,
              length(1, text("")),
              length(
                  1,
                  text(
                      "  flowing gradient — animated at 60fps via useFrame (no manual loop)",
                      Style.empty().withFg(LILAC))),
              length(1, flowBar(phaseMs.get())),
              length(1, text("")),
              length(
                  1,
                  text(
                      "  hover a file row (move the mouse over it):", Style.empty().withFg(LILAC))),
              fill(1, fileList(hoveredRow.get(), i -> hoveredRow.set(i))),
              length(1, text("")),
              length(
                  1,
                  text(
                      "  drag to scrub the timeline (press + drag, release to stop):",
                      Style.empty().withFg(LILAC))),
              length(1, scrubBar(scrub, dragging)),
              length(1, footer(scrub.get(), dragging.get())));
        });
  }

  // ---- Animated flow bar (useFrame) ----

  static Element flowBar(double phaseMs) {
    return widget(
        (Rect area, jatatui.core.buffer.Buffer buf) -> {
          int w = area.width();
          for (int i = 0; i < w; i++) {
            double t = w <= 1 ? 0.0 : (double) i / (w - 1);
            // A travelling wave: position drives hue phase, the frame clock streams it L→R.
            double wave = 0.5 + 0.5 * Math.sin(t * Math.PI * 3.0 - phaseMs * 0.006);
            double brightness = 0.35 + 0.65 * wave;
            Color c = lerp(IRIS, ROSE, t, brightness);
            buf.setString(area.x() + i, area.y(), "█", Style.empty().withFg(c));
          }
        });
  }

  // ---- Hover-highlighted file list ----

  static Element fileList(int hovered, java.util.function.IntConsumer onHover) {
    Element[] rows = new Element[FILES.length];
    for (int i = 0; i < FILES.length; i++) {
      rows[i] = length(1, fileRow(i, FILES[i], hovered == i, onHover));
    }
    return column(rows);
  }

  static Element fileRow(
      int index, String name, boolean hovered, java.util.function.IntConsumer onHover) {
    return component(
        ctx -> {
          ctx.onHover(() -> onHover.accept(index));
          Style style =
              hovered ? Style.empty().withFg(Color.WHITE).withBg(ASH) : Style.empty().withFg(MINT);
          String marker = hovered ? "  ▸ " : "    ";
          return text(marker + name + "   +3 -1", style);
        });
  }

  // ---- Drag-scrubbable timeline ----

  static Element scrubBar(
      jatatui.react.State<Double> scrub, jatatui.react.State<Boolean> dragging) {
    return component(
        ctx -> {
          Optional<Rect> areaOpt = ctx.area();
          ctx.onDrag(
              e ->
                  areaOpt.ifPresent(
                      r -> {
                        int span = Math.max(1, r.width() - 1);
                        double frac = (double) (e.x() - r.x()) / span;
                        scrub.set(clamp01(frac));
                        dragging.set(true);
                      }));
          ctx.onDragEnd(() -> dragging.set(false));

          double pos = scrub.get();
          boolean isDragging = dragging.get();
          return widget(
              (Rect area, jatatui.core.buffer.Buffer buf) -> {
                int w = area.width();
                for (int i = 0; i < w; i++) {
                  buf.setString(area.x() + i, area.y(), "─", Style.empty().withFg(ASH));
                }
                int caret = (int) Math.round(pos * Math.max(0, w - 1));
                Color caretColor =
                    isDragging
                        ? new Color.Rgb(ROSE[0], ROSE[1], ROSE[2])
                        : new Color.Rgb(IRIS[0], IRIS[1], IRIS[2]);
                buf.setString(area.x() + caret, area.y(), "◆", Style.empty().withFg(caretColor));
              });
        });
  }

  static Element footer(double scrub, boolean dragging) {
    String pct = String.format("%3d%%", (int) Math.round(scrub * 100));
    String state = dragging ? "dragging" : "idle";
    return text(
        "  scrub " + pct + "  (" + state + ")     Esc / Ctrl-C to quit",
        Style.empty().withFg(dragging ? new Color.Rgb(ROSE[0], ROSE[1], ROSE[2]) : LILAC));
  }

  // ---- Color helpers ----

  /// Interpolate between two RGB triples at `t` (0..1), then scale toward black by `brightness`.
  private static Color lerp(int[] a, int[] b, double t, double brightness) {
    int r = (int) ((a[0] + (b[0] - a[0]) * t) * brightness);
    int g = (int) ((a[1] + (b[1] - a[1]) * t) * brightness);
    int bb = (int) ((a[2] + (b[2] - a[2]) * t) * brightness);
    return new Color.Rgb(clamp8(r), clamp8(g), clamp8(bb));
  }

  private static int clamp8(int v) {
    return Math.max(0, Math.min(255, v));
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  private FrameExample() {}
}
