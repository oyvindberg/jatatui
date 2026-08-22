package jatatui.react.examples.form;

import static jatatui.components.Components.formProvider;
import static jatatui.components.Components.titledTextInput;
import static jatatui.react.Components.*;

import jatatui.components.form.FieldApi;
import jatatui.components.form.FormApi;
import jatatui.core.layout.Margin;
import jatatui.core.style.Color;
import jatatui.core.style.Style;
import jatatui.react.Element;
import jatatui.react.ReactApp;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import tui.crossterm.KeyCode;

/// Demonstrates FormProvider + useField.
///
/// Two text inputs (name + email) with validation. Press `Ctrl-S` to submit (only fires when
/// valid). Press `Ctrl-R` to reset. Errors show beneath each input.
public final class FormExample {

  public static void main(String[] args) throws IOException {
    Map<String, Object> initial = new HashMap<>();
    initial.put("name", "");
    initial.put("email", "");

    ReactApp.run(
        formProvider(
            initial,
            FormExample::validate,
            values -> {
              // In a real app you'd persist or navigate. Here we just log to a state-held banner.
              System.err.println("submitted: " + values);
            },
            content()));
  }

  static Map<String, String> validate(Map<String, Object> values) {
    Map<String, String> errs = new HashMap<>();
    String name = (String) values.getOrDefault("name", "");
    String email = (String) values.getOrDefault("email", "");
    if (name.isEmpty()) errs.put("name", "name is required");
    if (email.isEmpty()) errs.put("email", "email is required");
    else if (!email.contains("@")) errs.put("email", "must contain @");
    return errs;
  }

  static Element content() {
    return component(
        ctx -> {
          FormApi form = FormApi.useForm(ctx);
          FieldApi<String> name = FormApi.useField(ctx, "name", "");
          FieldApi<String> email = FormApi.useField(ctx, "email", "");

          ctx.onGlobalKey(
              new KeyCode.Char('s'),
              e -> {
                if ((e.modifiers().bits() & tui.crossterm.KeyModifiers.CONTROL) != 0) {
                  form.submit();
                  e.stopPropagation();
                }
              });
          ctx.onGlobalKey(
              new KeyCode.Char('r'),
              e -> {
                if ((e.modifiers().bits() & tui.crossterm.KeyModifiers.CONTROL) != 0) {
                  form.reset();
                  e.stopPropagation();
                }
              });

          return column(
                  length(
                      1,
                      text(
                          " form demo  —  Tab to cycle, Ctrl-S submit, Ctrl-R reset, Esc to quit ",
                          Style.empty().withFg(Color.WHITE).withBg(Color.BLUE))),
                  length(
                      3,
                      titledTextInput(
                          "Name" + (name.hasError() ? " (!)" : ""),
                          name.value(),
                          name.setValue(),
                          "your name",
                          "name")),
                  length(1, text(name.error().orElse(""), Style.empty().withFg(Color.RED))),
                  length(
                      3,
                      titledTextInput(
                          "Email" + (email.hasError() ? " (!)" : ""),
                          email.value(),
                          email.setValue(),
                          "you@example.com",
                          "email")),
                  length(1, text(email.error().orElse(""), Style.empty().withFg(Color.RED))),
                  length(
                      2,
                      text(
                          form.hasErrors()
                              ? "Form has errors — fix before submitting."
                              : "Form OK — Ctrl-S to submit.",
                          Style.empty().withFg(form.hasErrors() ? Color.RED : Color.GREEN))),
                  fill(1, text("")))
              .with(p -> p.withSpacing(1).withMargin(new Margin(2, 1)));
        });
  }
}
