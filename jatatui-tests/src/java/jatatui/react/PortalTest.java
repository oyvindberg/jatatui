package jatatui.react;

import static jatatui.react.Components.*;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.core.backend.TestBackend;
import jatatui.core.layout.Rect;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.core.terminal.Terminal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PortalTest {

  @Test
  void portal_renders_after_main_pass() throws IOException {
    // Main pass writes "BG" to the buffer; portal writes "FG" at same area. Portal must win.
    Element child = text("FG", Style.empty().withFg(Color.WHITE).withBg(Color.BLUE));
    Element app = column(text("BG"), portal(child, new Rect(0, 0, 5, 1)));
    String[] rows = renderToText(app, 10, 3);
    // The first row should now be "FG" (portal overpainted), not "BG".
    assertTrue(
        rows[0].startsWith("FG"), "portal must paint over main pass; got: '" + rows[0] + "'");
  }

  @Test
  void portal_event_bubbles_through_declaring_parent() throws IOException {
    AtomicBoolean parentSawClick = new AtomicBoolean(false);

    Element portalChild =
        component(
            ctx -> {
              ctx.onClick(e -> {});
              return text("portal-content");
            });

    Element parent =
        component(
            parentCtx -> {
              parentCtx.onClick(e -> parentSawClick.set(true));
              return column(text("parent"), portal(portalChild, new Rect(0, 5, 10, 2)));
            });

    EventRegistry events = new EventRegistry();
    HookStore hooks = new HookStore();
    FocusManager focus = new FocusManager();
    TestBackend backend = new TestBackend(40, 12);
    Terminal<TestBackend> terminal = Terminal.create(backend);
    terminal.draw(
        frame -> {
          events.clear();
          focus.clearFrame();
          RenderContext ctx = new RenderContext(frame, events, hooks, focus, () -> {});
          events.recordBounds(Fiber.root(), frame.area());
          parent.render(ctx, frame.area());
          ctx.drainPortals();
        });

    // Click inside the portal area
    MouseEvent click =
        new MouseEvent(2, 5, new tui.crossterm.KeyModifiers(0), MouseEvent.Kind.DOWN);
    events.dispatchMouse(click);
    assertTrue(parentSawClick.get(), "click on portal must bubble to declaring parent");
  }

  @Test
  void multiple_portals_render_in_declaration_order() throws IOException {
    // Two portals at the same area; second overwrites first.
    Element app =
        column(portal(text("AA"), new Rect(0, 0, 5, 1)), portal(text("BB"), new Rect(0, 0, 5, 1)));
    String[] rows = renderToText(app, 10, 3);
    assertTrue(rows[0].startsWith("BB"), "second portal wins; got: '" + rows[0] + "'");
  }

  @Test
  void empty_portal_queue_no_op() throws IOException {
    Element app = text("hello");
    String[] rows = renderToText(app, 10, 3);
    assertTrue(rows[0].startsWith("hello"));
  }

  static String[] renderToText(Element root, int w, int h) throws IOException {
    EventRegistry events = new EventRegistry();
    HookStore hooks = new HookStore();
    FocusManager focus = new FocusManager();
    TestBackend backend = new TestBackend(w, h);
    Terminal<TestBackend> terminal = Terminal.create(backend);
    terminal.draw(
        frame -> {
          events.clear();
          focus.clearFrame();
          RenderContext ctx = new RenderContext(frame, events, hooks, focus, () -> {});
          events.recordBounds(Fiber.root(), frame.area());
          root.render(ctx, frame.area());
          ctx.drainPortals();
        });

    List<String> lines = new ArrayList<>();
    var buf = backend.buffer();
    for (int y = 0; y < h; y++) {
      StringBuilder sb = new StringBuilder();
      for (int x = 0; x < w; x++) {
        sb.append(buf.cellAt(x, y).symbol());
      }
      lines.add(sb.toString());
    }
    return lines.toArray(new String[0]);
  }
}
