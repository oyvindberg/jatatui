package jatatui.components.form;

import static jatatui.react.Components.component;
import static jatatui.react.Components.provide;

import jatatui.react.Element;
import jatatui.react.Ref;
import jatatui.react.State;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/// Mount around a form's fields. Provides a [FormApi] via Context to descendants. The component
/// owns the values + errors state via `useState`; runs `validate` after every change; runs
/// `onSubmit` only when there are no errors.
///
/// `validate` is a pure function: takes the current values, returns a map of `field → error
/// message`. Empty / missing keys mean "no error".
public final class FormProvider {
  private FormProvider() {}

  public static Element of(
      Map<String, Object> initialValues,
      Function<Map<String, Object>, Map<String, String>> validate,
      Consumer<Map<String, Object>> onSubmit,
      Element child) {
    Map<String, Object> initial = Map.copyOf(initialValues);
    return component(
        ctx -> {
          State<Map<String, Object>> valuesState = ctx.useState(() -> initial);
          State<Map<String, String>> errorsState = ctx.useState(() -> validate.apply(initial));
          State<Boolean> submitting = ctx.useState(() -> false);
          Ref<Function<Map<String, Object>, Map<String, String>>> validateRef =
              ctx.useRef(() -> validate);

          FormApi api =
              new FormApi() {
                @Override
                public Optional<Object> getValue(String field) {
                  return Optional.ofNullable(valuesState.latest().get(field));
                }

                @Override
                public void setValue(String field, Object value) {
                  Map<String, Object> next = new HashMap<>(valuesState.latest());
                  next.put(field, value);
                  Map<String, Object> nextValues = Map.copyOf(next);
                  valuesState.set(nextValues);
                  errorsState.set(Map.copyOf(validateRef.get().apply(nextValues)));
                }

                @Override
                public Optional<String> getError(String field) {
                  return Optional.ofNullable(errorsState.latest().get(field));
                }

                @Override
                public Map<String, Object> values() {
                  return valuesState.latest();
                }

                @Override
                public Map<String, String> errors() {
                  return errorsState.latest();
                }

                @Override
                public boolean hasErrors() {
                  return !errorsState.latest().isEmpty();
                }

                @Override
                public void submit() {
                  Map<String, String> errs = validateRef.get().apply(valuesState.latest());
                  errorsState.set(Map.copyOf(errs));
                  if (errs.isEmpty()) {
                    submitting.set(true);
                    try {
                      onSubmit.accept(valuesState.latest());
                    } finally {
                      submitting.set(false);
                    }
                  }
                }

                @Override
                public void reset() {
                  valuesState.set(initial);
                  errorsState.set(Map.copyOf(validateRef.get().apply(initial)));
                }

                @Override
                public boolean isSubmitting() {
                  return submitting.latest();
                }
              };

          return provide(FormApi.CONTEXT, api, child);
        });
  }

  /// Validator that returns no errors. Use as a default when validation isn't needed.
  public static final Function<Map<String, Object>, Map<String, String>> NO_VALIDATION =
      values -> Map.of();
}
