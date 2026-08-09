package jatatui.components.form;

import static jatatui.components.Components.formProvider;
import static jatatui.react.Components.component;
import static jatatui.react.Components.text;
import static org.junit.jupiter.api.Assertions.*;

import jatatui.react.Element;
import jatatui.react.TestHarness;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FormTest {

  @Test
  void initial_values_present() throws IOException {
    AtomicReference<FormApi> apiRef = new AtomicReference<>();
    Element child =
        component(
            ctx -> {
              apiRef.set(FormApi.useForm(ctx));
              return text("ok");
            });
    Element app = formProvider(Map.of("name", "Alice", "age", 30), values -> {}, child);
    new TestHarness(40, 12).render(app);
    assertEquals("Alice", apiRef.get().getValue("name").orElse(null));
    assertEquals(30, apiRef.get().getValue("age").orElse(null));
  }

  @Test
  void set_value_updates_and_re_renders() throws IOException {
    AtomicReference<FormApi> apiRef = new AtomicReference<>();
    Element child =
        component(
            ctx -> {
              apiRef.set(FormApi.useForm(ctx));
              return text("ok");
            });
    Element app = formProvider(Map.of("name", "Alice"), values -> {}, child);
    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    apiRef.get().setValue("name", "Bob");
    h.render(app);
    assertEquals("Bob", apiRef.get().getValue("name").orElse(null));
  }

  @Test
  void validate_runs_after_set() throws IOException {
    AtomicReference<FormApi> apiRef = new AtomicReference<>();
    Element child =
        component(
            ctx -> {
              apiRef.set(FormApi.useForm(ctx));
              return text("ok");
            });
    Map<String, Object> initial = new HashMap<>();
    initial.put("email", "");
    Element app =
        formProvider(
            initial,
            values -> {
              Map<String, String> errs = new HashMap<>();
              String email = (String) values.get("email");
              if (email == null || email.isEmpty()) errs.put("email", "required");
              else if (!email.contains("@")) errs.put("email", "invalid");
              return errs;
            },
            values -> {},
            child);
    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    assertEquals("required", apiRef.get().getError("email").orElse(null));
    apiRef.get().setValue("email", "no-at-sign");
    h.render(app);
    assertEquals("invalid", apiRef.get().getError("email").orElse(null));
    apiRef.get().setValue("email", "ok@example.com");
    h.render(app);
    assertTrue(apiRef.get().getError("email").isEmpty());
  }

  @Test
  void submit_does_not_run_with_errors() throws IOException {
    AtomicBoolean submitted = new AtomicBoolean(false);
    AtomicReference<FormApi> apiRef = new AtomicReference<>();
    Element child =
        component(
            ctx -> {
              apiRef.set(FormApi.useForm(ctx));
              return text("ok");
            });
    Element app =
        formProvider(
            Map.of("name", ""),
            values -> {
              String n = (String) values.get("name");
              return (n == null || n.isEmpty()) ? Map.of("name", "required") : Map.of();
            },
            values -> submitted.set(true),
            child);
    new TestHarness(40, 12).render(app);
    apiRef.get().submit();
    assertFalse(submitted.get(), "submit must not run when errors present");
  }

  @Test
  void submit_runs_when_clean() throws IOException {
    AtomicBoolean submitted = new AtomicBoolean(false);
    AtomicReference<FormApi> apiRef = new AtomicReference<>();
    Element child =
        component(
            ctx -> {
              apiRef.set(FormApi.useForm(ctx));
              return text("ok");
            });
    Element app = formProvider(Map.of("name", "Alice"), values -> submitted.set(true), child);
    new TestHarness(40, 12).render(app);
    apiRef.get().submit();
    assertTrue(submitted.get());
  }

  @Test
  void reset_restores_initial_values() throws IOException {
    AtomicReference<FormApi> apiRef = new AtomicReference<>();
    Element child =
        component(
            ctx -> {
              apiRef.set(FormApi.useForm(ctx));
              return text("ok");
            });
    Element app = formProvider(Map.of("name", "Alice"), values -> {}, child);
    TestHarness h = new TestHarness(40, 12);
    h.render(app);
    apiRef.get().setValue("name", "Bob");
    h.render(app);
    apiRef.get().reset();
    h.render(app);
    assertEquals("Alice", apiRef.get().getValue("name").orElse(null));
  }
}
