package jatatui.widgets.input;

import jatatui.core.buffer.Buffer;
import jatatui.core.buffer.Cell;
import jatatui.core.layout.Rect;
import jatatui.core.style.Color;
import jatatui.core.style.Modifier;
import jatatui.core.style.Style;
import jatatui.core.widgets.Widget;

/// A single-line text input.
///
/// Stateless widget — the value, cursor position, scroll offset, and focus flag are passed in as
/// props. The companion static helpers ([#insertAt], [#backspaceAt], [#deleteAt], [#moveLeft],
/// [#moveRight], [#moveHome], [#moveEnd]) compute the next state from a key press; the consuming
/// React Component holds the state via `useState` and calls these to update it.
///
/// Rendering: paints the visible window of `value` (after horizontal scroll), with the cursor
/// drawn as a styled cell (reverse video by default) when [#focused]. Placeholder shown when
/// `value` is empty AND not focused.
public final class TextInput implements Widget {

  public final String value;
  public final int cursorPos;
  public final int scrollOffset;
  public final boolean focused;
  public final String placeholder;
  public final Style style;
  public final Style focusedStyle;
  public final Style placeholderStyle;
  public final Style cursorStyle;

  private TextInput(
      String value,
      int cursorPos,
      int scrollOffset,
      boolean focused,
      String placeholder,
      Style style,
      Style focusedStyle,
      Style placeholderStyle,
      Style cursorStyle) {
    this.value = value;
    this.cursorPos = clamp(cursorPos, 0, value.length());
    this.scrollOffset = Math.max(0, scrollOffset);
    this.focused = focused;
    this.placeholder = placeholder;
    this.style = style;
    this.focusedStyle = focusedStyle;
    this.placeholderStyle = placeholderStyle;
    this.cursorStyle = cursorStyle;
  }

  /// Build a TextInput with the given value, cursor at end, no placeholder, default styles.
  public static TextInput of(String value) {
    return new TextInput(
        value,
        value.length(),
        0,
        false,
        "",
        Style.empty(),
        Style.empty().withFg(new Color.White()),
        Style.empty().withFg(new Color.DarkGray()),
        Style.empty().withAddModifier(Modifier.REVERSED));
  }

  public TextInput withValue(String value) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  public TextInput withCursorPos(int cursorPos) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  public TextInput withScrollOffset(int scrollOffset) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  public TextInput withFocused(boolean focused) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  public TextInput withPlaceholder(String placeholder) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  public TextInput withStyle(Style style) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  public TextInput withFocusedStyle(Style focusedStyle) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  public TextInput withPlaceholderStyle(Style placeholderStyle) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  public TextInput withCursorStyle(Style cursorStyle) {
    return new TextInput(
        value,
        cursorPos,
        scrollOffset,
        focused,
        placeholder,
        style,
        focusedStyle,
        placeholderStyle,
        cursorStyle);
  }

  // ---- Render ----

  @Override
  public void render(Rect area, Buffer buf) {
    Rect clipped = area.intersection(buf.area());
    if (clipped.isEmpty()) return;
    int width = clipped.width();
    if (width == 0) return;

    Style baseStyle = focused ? focusedStyle : style;

    // Show placeholder if empty and not focused.
    if (value.isEmpty() && !focused) {
      paintLine(buf, clipped, placeholder, placeholderStyle, width);
      return;
    }

    // Compute scroll so cursor is in view.
    int adjustedScroll = scrollFor(cursorPos, scrollOffset, width);
    int visibleLen = Math.min(value.length() - adjustedScroll, width);
    String visible = value.substring(adjustedScroll, adjustedScroll + Math.max(0, visibleLen));

    paintLine(buf, clipped, visible, baseStyle, width);

    // Draw cursor cell (only when focused).
    if (focused) {
      int cursorScreenX = cursorPos - adjustedScroll;
      if (cursorScreenX >= 0 && cursorScreenX < width) {
        int absX = clipped.left() + cursorScreenX;
        int absY = clipped.top();
        Cell cell = buf.cellAt(absX, absY);
        // Preserve any existing symbol but apply cursor style.
        cell.setStyle(cursorStyle);
      }
    }
  }

  private static void paintLine(Buffer buf, Rect area, String text, Style style, int width) {
    int absY = area.top();
    int absX0 = area.left();
    for (int x = 0; x < width; x++) {
      Cell cell = buf.cellAt(absX0 + x, absY);
      char ch = x < text.length() ? text.charAt(x) : ' ';
      cell.setSymbol(String.valueOf(ch));
      cell.setStyle(style);
    }
  }

  /// Returns the scroll offset so that `cursorPos` is in the visible window of width `width`.
  /// Mirrors the standard "keep cursor on screen" logic: if cursor is left of the window, scroll
  /// left; if past the right edge, scroll so cursor is at the rightmost cell.
  public static int scrollFor(int cursorPos, int currentScroll, int width) {
    if (width <= 0) return 0;
    if (cursorPos < currentScroll) return cursorPos;
    if (cursorPos >= currentScroll + width) return cursorPos - width + 1;
    return currentScroll;
  }

  // ---- State transitions (pure helpers) ----

  /// Result of an editing operation: new value + new cursor position.
  public record TextResult(String value, int cursorPos) {}

  /// Insert `ch` at the current cursor position, advancing the cursor by 1.
  public static TextResult insertAt(String value, int cursorPos, char ch) {
    int p = clamp(cursorPos, 0, value.length());
    String next = value.substring(0, p) + ch + value.substring(p);
    return new TextResult(next, p + 1);
  }

  /// Insert a string at the cursor (for paste).
  public static TextResult insertStringAt(String value, int cursorPos, String s) {
    int p = clamp(cursorPos, 0, value.length());
    String next = value.substring(0, p) + s + value.substring(p);
    return new TextResult(next, p + s.length());
  }

  /// Delete the character before the cursor; cursor moves left by 1. No-op at position 0.
  public static TextResult backspaceAt(String value, int cursorPos) {
    int p = clamp(cursorPos, 0, value.length());
    if (p == 0) return new TextResult(value, 0);
    String next = value.substring(0, p - 1) + value.substring(p);
    return new TextResult(next, p - 1);
  }

  /// Delete the character at the cursor; cursor stays. No-op at end of string.
  public static TextResult deleteAt(String value, int cursorPos) {
    int p = clamp(cursorPos, 0, value.length());
    if (p >= value.length()) return new TextResult(value, p);
    String next = value.substring(0, p) + value.substring(p + 1);
    return new TextResult(next, p);
  }

  public static TextResult moveLeft(String value, int cursorPos) {
    return new TextResult(value, Math.max(0, cursorPos - 1));
  }

  public static TextResult moveRight(String value, int cursorPos) {
    return new TextResult(value, Math.min(value.length(), cursorPos + 1));
  }

  public static TextResult moveHome(String value, int cursorPos) {
    return new TextResult(value, 0);
  }

  public static TextResult moveEnd(String value, int cursorPos) {
    return new TextResult(value, value.length());
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }
}
