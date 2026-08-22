package jatatui.react.examples;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/// Single entry point for the React-style examples. Pass the example name as the first argument;
/// remaining args are forwarded to the example's `main`.
///
/// Examples are looked up reflectively so the launcher compiles even when an individual example
/// isn't present yet — handy during parallel development.
///
/// Run with no argument (or `--list` / `-l`) to see all known names.
public final class Launcher {

  private static final Map<String, String> EXAMPLES = new LinkedHashMap<>();

  static {
    EXAMPLES.put("bubble", "jatatui.react.examples.bubble.BubbleExample");
    EXAMPLES.put("frame", "jatatui.react.examples.frame.FrameExample");
    EXAMPLES.put("counter", "jatatui.react.examples.counter.CounterExample");
    EXAMPLES.put("list", "jatatui.react.examples.list.ListExample");
    EXAMPLES.put("table", "jatatui.react.examples.table.TableExample");
    EXAMPLES.put("gauge", "jatatui.react.examples.gauge.GaugeExample");
    EXAMPLES.put("sparkline", "jatatui.react.examples.sparkline.SparklineExample");
    EXAMPLES.put("scrollbar", "jatatui.react.examples.scrollbar.ScrollbarExample");
    EXAMPLES.put("barchart", "jatatui.react.examples.barchart.BarChartExample");
    EXAMPLES.put("showcase", "jatatui.react.examples.showcase.ShowcaseExample");
    EXAMPLES.put("textinput", "jatatui.react.examples.textinput.TextInputExample");
    EXAMPLES.put("modal", "jatatui.react.examples.modal.ModalExample");
    EXAMPLES.put("toast", "jatatui.react.examples.toast.ToastExample");
    EXAMPLES.put("dropdown", "jatatui.react.examples.dropdown.DropdownExample");
    EXAMPLES.put("theme", "jatatui.react.examples.theme.ThemeExample");
    EXAMPLES.put("form", "jatatui.react.examples.form.FormExample");
    EXAMPLES.put("router", "jatatui.react.examples.router.RouterExample");
    EXAMPLES.put("selectablelist", "jatatui.react.examples.selectablelist.SelectableListExample");
  }

  private Launcher() {}

  public static void main(String[] args) throws IOException {
    if (args.length == 0 || args[0].equals("--list") || args[0].equals("-l")) {
      printList();
      return;
    }
    String name = args[0];
    String className = EXAMPLES.get(name);
    if (className == null) {
      System.err.println("Unknown example: " + name);
      printList();
      System.exit(2);
      return;
    }
    String[] rest = Arrays.copyOfRange(args, 1, args.length);
    invoke(className, rest);
  }

  private static void invoke(String className, String[] args) throws IOException {
    try {
      Class<?> c = Class.forName(className);
      var m = c.getMethod("main", String[].class);
      m.invoke(null, (Object) args);
    } catch (ClassNotFoundException e) {
      throw new IOException("Example class not found: " + className + " (still being built?)", e);
    } catch (NoSuchMethodException e) {
      throw new IOException(className + " has no main(String[]) method", e);
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof IOException io) throw io;
      throw new IOException("Failed to invoke " + className + ": " + cause.getMessage(), cause);
    }
  }

  private static void printList() {
    System.out.println("Available React examples:");
    for (String name : EXAMPLES.keySet()) {
      System.out.println("  " + name);
    }
  }
}
