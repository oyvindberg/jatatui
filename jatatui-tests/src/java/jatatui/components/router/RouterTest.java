package jatatui.components.router;

import static jatatui.components.Components.router;
import static jatatui.react.Components.component;
import static jatatui.react.Components.text;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.react.Element;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RouterTest {

  @Test
  void initial_screen_is_base() throws IOException {
    AtomicReference<RouterApi> apiRef = new AtomicReference<>();
    Element baseScreen =
        component(
            ctx -> {
              apiRef.set(RouterApi.useRouter(ctx));
              return text("base");
            });
    Element app = router(Screen.of("home", baseScreen));
    new TestHarness(40, 12).render(app);
    assertEquals("home", apiRef.get().current().label());
    assertEquals(1, apiRef.get().depth());
  }

  @Test
  void push_increases_depth() throws IOException {
    AtomicReference<RouterApi> apiRef = new AtomicReference<>();
    Element baseScreen =
        component(
            ctx -> {
              apiRef.set(RouterApi.useRouter(ctx));
              return text("base");
            });
    Element app = router(Screen.of("home", baseScreen));
    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    apiRef.get().push("settings", text("settings screen"));
    h.render(app);
    assertEquals("settings", apiRef.get().current().label());
    assertEquals(2, apiRef.get().depth());
  }

  @Test
  void pop_returns_to_previous() throws IOException {
    AtomicReference<RouterApi> apiRef = new AtomicReference<>();
    Element baseScreen =
        component(
            ctx -> {
              apiRef.set(RouterApi.useRouter(ctx));
              return text("base");
            });
    Element app = router(Screen.of("home", baseScreen));
    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    apiRef.get().push("settings", text("s"));
    h.render(app);
    apiRef.get().pop();
    h.render(app);
    assertEquals("home", apiRef.get().current().label());
    assertEquals(1, apiRef.get().depth());
  }

  @Test
  void pop_at_base_is_noop() throws IOException {
    AtomicReference<RouterApi> apiRef = new AtomicReference<>();
    Element baseScreen =
        component(
            ctx -> {
              apiRef.set(RouterApi.useRouter(ctx));
              return text("base");
            });
    Element app = router(Screen.of("home", baseScreen));
    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    apiRef.get().pop();
    h.render(app);
    assertEquals(1, apiRef.get().depth());
    assertEquals("home", apiRef.get().current().label());
  }

  @Test
  void replace_keeps_depth() throws IOException {
    AtomicReference<RouterApi> apiRef = new AtomicReference<>();
    Element baseScreen =
        component(
            ctx -> {
              apiRef.set(RouterApi.useRouter(ctx));
              return text("base");
            });
    Element app = router(Screen.of("home", baseScreen));
    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    apiRef.get().push("a", text("a"));
    h.render(app);
    apiRef.get().replace("b", text("b"));
    h.render(app);
    assertEquals("b", apiRef.get().current().label());
    assertEquals(2, apiRef.get().depth());
  }

  /// Router transitions clear focus before the new screen renders, so the new screen's
  /// `useFocus(autoFocus=true)` claims SAME-frame via the eager-claim path. Without this, the
  /// previous screen's focused id would still be set, blocking eager-claim, and the new
  /// screen's first frame would paint as nothing-focused (one-frame flicker).
  @Test
  void push_clears_focus_so_new_screen_focuses_first_frame() throws IOException {
    AtomicReference<RouterApi> apiRef = new AtomicReference<>();
    java.util.List<Boolean> screenAFlags = new java.util.ArrayList<>();
    java.util.List<Boolean> screenBFlags = new java.util.ArrayList<>();

    Element screenA =
        component(
            ctx -> {
              apiRef.set(RouterApi.useRouter(ctx));
              screenAFlags.add(ctx.useFocus(java.util.Optional.of("a-field"), true));
              return text("a");
            });
    Element screenB =
        component(
            ctx -> {
              apiRef.set(RouterApi.useRouter(ctx));
              screenBFlags.add(ctx.useFocus(java.util.Optional.of("b-field"), true));
              return text("b");
            });

    Element app = router(Screen.of("a", screenA));
    TestHarness h = new TestHarness(40, 12);

    h.render(app);
    assertEquals(java.util.List.of(true), screenAFlags, "screen A's autoFocus visible same-frame");

    apiRef.get().push("b", screenB);
    h.render(app);
    assertEquals(
        java.util.List.of(true),
        screenBFlags,
        "screen B's autoFocus visible same-frame (router cleared stale focus from A)");

    screenAFlags.clear();
    apiRef.get().pop();
    h.render(app);
    assertEquals(
        java.util.List.of(true),
        screenAFlags,
        "back to screen A: autoFocus visible same-frame again");
  }

  @Test
  void reset_back_to_base() throws IOException {
    AtomicReference<RouterApi> apiRef = new AtomicReference<>();
    Element baseScreen =
        component(
            ctx -> {
              apiRef.set(RouterApi.useRouter(ctx));
              return text("base");
            });
    Element app = router(Screen.of("home", baseScreen));
    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    apiRef.get().push("a", text("a"));
    apiRef.get().push("b", text("b"));
    h.render(app);
    apiRef.get().reset();
    h.render(app);
    assertEquals(1, apiRef.get().depth());
    assertEquals("home", apiRef.get().current().label());
  }
}
