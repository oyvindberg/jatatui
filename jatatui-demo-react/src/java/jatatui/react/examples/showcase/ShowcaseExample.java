package jatatui.react.examples.showcase;

import static jatatui.react.Components.*;

import jatatui.core.layout.Margin;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import jatatui.widgets.Borders;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tui.crossterm.KeyCode;

/// "Dashboard" showcase composing several Components into one screen.
///
/// Stress-tests the React API by combining:
///   - top bar (title left, clock right) in a [#row]
///   - tab strip (Home / Stats / About) rendered with a manual selector + [#ifElse]
///   - body that switches per tab — Home has a counter + a messages log; Stats is a quick text;
///     About is a [#paragraph]
///   - bottom hint line
///
/// Demonstrates: [#pureComponent] with a record-props body (the messages log), [#memo] keyed on
/// the current tab index for the tab strip header, [#useState] of [Integer] and [List] of
/// [String], focus-scoped + global key handlers via [jatatui.react.RenderContext#onKey], and
/// per-child sizing using [#length] / [#fill].
public final class ShowcaseExample {

  /// 0 = Home, 1 = Stats, 2 = About
  private static final List<String> TAB_LABELS = List.of("Home", "Stats", "About");

  /// Time formatter used by the top bar; `LocalTime.now()` is sampled at every render.
  /// Without a frame timer this only updates on user input — see report.
  private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  public static void main(String[] args) throws IOException {
    ReactApp.run(app());
  }

  // ---- Root ----

  static Element app() {
    return component(
        ctx -> {
          var tab = ctx.useState(() -> 0);
          var count = ctx.useState(() -> 0);
          var messages = ctx.<List<String>>useState(List::of);

          // Global-ish keys: registered on the root fiber; with no useFocus on root they only fire
          // when nothing else has focus (or — given the current ReactApp routes globally when
          // focusedFiberFromId returns empty — they fire from anywhere).
          ctx.onKey(new KeyCode.Char('+'), () -> count.update(n -> n + 1));
          ctx.onKey(new KeyCode.Char('-'), () -> count.update(n -> n - 1));
          ctx.onKey(new KeyCode.Up(), () -> count.update(n -> n + 1));
          ctx.onKey(new KeyCode.Down(), () -> count.update(n -> n - 1));
          ctx.onKey(new KeyCode.Char('\t'), () -> tab.set((tab.get() + 1) % TAB_LABELS.size()));
          ctx.onKey(
              new KeyCode.Enter(),
              () -> {
                if (tab.get() == 0) {
                  messages.update(prev -> appendMessage(prev, "msg #" + (prev.size() + 1)));
                }
              });

          return column(
                  length(1, topBar()),
                  length(1, tabsHeader(tab.get())),
                  fill(1, body(tab.get(), count.get(), messages)),
                  length(1, hint()))
              .with(p -> p.withSpacing(0).withMargin(new Margin(1, 0)));
        });
  }

  // ---- Top bar ----

  static Element topBar() {
    String now = LocalTime.now().format(CLOCK_FMT);
    return row(
        fill(
            1,
            text(
                " jatatui-react / showcase ",
                Style.empty().withFg(Color.WHITE).withBg(Color.BLUE))),
        length(now.length() + 2, text(" " + now + " ", Style.empty().withFg(Color.YELLOW))));
  }

  // ---- Tab strip ----

  /// Memoized: only rebuilt when `selected` changes. The header is just a [#text] but this
  /// demonstrates `memo` keyed on a primitive.
  static Element tabsHeader(int selected) {
    return memo(
        deps(selected), () -> text(renderTabHeader(selected), Style.empty().withFg(Color.GRAY)));
  }

  private static String renderTabHeader(int selected) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < TAB_LABELS.size(); i++) {
      if (i > 0) sb.append("  ");
      String label = TAB_LABELS.get(i);
      sb.append(i == selected ? "[" + label + "]" : " " + label + " ");
    }
    return sb.toString();
  }

  // ---- Body ----

  static Element body(int selected, int count, jatatui.react.State<List<String>> messages) {
    return switch (selected) {
      case 0 -> homeTab(count, messages);
      case 1 -> statsTab(count, messages.get().size());
      case 2 -> aboutTab();
      default -> empty();
    };
  }

  // ---- Home tab ----

  static Element homeTab(int count, jatatui.react.State<List<String>> messages) {
    Element counterBox =
        box(
            " Counter ",
            Borders.ALL,
            text("count = " + count, Style.empty().withFg(Color.CYAN)),
            text("(use Up/Down or +/-)", Style.empty().withFg(Color.GRAY)),
            row(
                button(
                    "[ + ]",
                    Style.empty().withFg(Color.GREEN),
                    () -> messages.set(appendMessage(messages.get(), "+1"))),
                button(
                    "[ - ]",
                    Style.empty().withFg(Color.RED),
                    () -> messages.set(appendMessage(messages.get(), "-1")))));

    Element msgBox =
        box(
            " Messages (Enter to add) ",
            Borders.ALL,
            messageList(messages.get()),
            button(
                "[ Add message ]",
                Style.empty().withFg(Color.MAGENTA),
                () ->
                    messages.set(
                        appendMessage(messages.get(), "msg #" + (messages.get().size() + 1)))));

    return row(percent(40, counterBox), fill(1, msgBox)).with(p -> p.withSpacing(1));
  }

  // ---- Stats tab ----

  static Element statsTab(int count, int msgCount) {
    return box(
        " Stats ",
        Borders.ALL,
        text("count    = " + count, Style.empty().withFg(Color.CYAN)),
        text("messages = " + msgCount, Style.empty().withFg(Color.MAGENTA)),
        text("(switch back to Home with Tab)", Style.empty().withFg(Color.GRAY)));
  }

  // ---- About tab ----

  static Element aboutTab() {
    return box(
        " About ",
        Borders.ALL,
        paragraph(
            "Showcase composes the built-in jatatui-react primitives — column, row, box, text,"
                + " paragraph, button, tabs, forEach — into a multi-tab dashboard. The Home tab has"
                + " a counter and a small message log, the Stats tab summarises state from a"
                + " sibling, and this About tab is wrapped paragraph text. The whole tree"
                + " re-renders on input; pureComponent + memo demonstrate how to skip work when"
                + " nothing changed.",
            Style.empty().withFg(Color.WHITE)));
  }

  // ---- Messages list (pure component over record props) ----

  /// Props record → structural equals → memoized for free across renders that don't change the
  /// list reference (or its contents).
  record MessageListProps(List<String> messages) {}

  static Element messageList(List<String> messages) {
    return pureComponent(
        new MessageListProps(messages),
        props -> {
          if (props.messages().isEmpty()) {
            return text("(no messages yet — press Enter)", Style.empty().withFg(Color.GRAY));
          }
          return forEach(
              props.messages(),
              /* keyFn */ s -> s,
              /* render */ s -> text("• " + s, Style.empty().withFg(Color.WHITE)));
        });
  }

  // ---- Bottom hint ----

  static Element hint() {
    return text(
        " Tab=cycle tab  |  q/Esc=quit  |  Enter=add message in Home  |  Up/Down or +/-=counter ",
        Style.empty().withFg(Color.BLACK).withBg(Color.GRAY));
  }

  // ---- Helpers ----

  private static List<String> appendMessage(List<String> prev, String msg) {
    List<String> next = new ArrayList<>(prev.size() + 1);
    next.addAll(prev);
    next.add(msg);
    return List.copyOf(next);
  }

  /// Suppress "unused" until the FocusManager wiring lets us scope these to a focusable.
  @SuppressWarnings("unused")
  private static Optional<String> focusId(String id) {
    return Optional.of(id);
  }
}
