package jatatui.components.search;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FuzzyMatchTest {

  @Test
  void word_starts_simple() {
    assertEquals(java.util.Set.of(0), FuzzyMatch.wordStarts("hello"));
    assertEquals(java.util.Set.of(0, 6), FuzzyMatch.wordStarts("hello world"));
    assertEquals(java.util.Set.of(0, 6), FuzzyMatch.wordStarts("hello_world"));
    assertEquals(java.util.Set.of(0, 6), FuzzyMatch.wordStarts("hello.world"));
  }

  @Test
  void word_starts_camel_case() {
    // h(0), W(5) — camel boundary between l and W
    assertEquals(java.util.Set.of(0, 5), FuzzyMatch.wordStarts("helloWorld"));
  }

  @Test
  void word_starts_letter_digit_boundary() {
    // c(0), 1(3) — boundary letter→digit
    assertEquals(java.util.Set.of(0, 3), FuzzyMatch.wordStarts("abc123"));
    // 1(0), a(3) — boundary digit→letter
    assertEquals(java.util.Set.of(0, 3), FuzzyMatch.wordStarts("123abc"));
  }

  @Test
  void score_empty_query_is_negative_length_proxy() {
    // Shorter strings should rank above longer for the empty query.
    int shortS = FuzzyMatch.score("", "a").orElseThrow();
    int longS = FuzzyMatch.score("", "aaaaaaaaaa").orElseThrow();
    assertTrue(shortS > longS, "shorter target ranks higher for empty query");
  }

  @Test
  void score_subsequence_required() {
    assertTrue(FuzzyMatch.score("xyz", "hello").isEmpty());
    assertTrue(FuzzyMatch.score("hel", "hello").isPresent());
  }

  @Test
  void score_word_start_beats_in_word() {
    // "cn" with word-start hits on each side ("customer_name") should score higher than
    // "cu" with one word-start + one in-word hit ("customer").
    int snake = FuzzyMatch.score("cn", "customer_name").orElseThrow();
    int inWord = FuzzyMatch.score("cu", "customer").orElseThrow();
    assertTrue(snake > inWord, "word-start hits on both chars > word-start + in-word");
  }

  @Test
  void score_camel_word_start() {
    int snake = FuzzyMatch.score("cn", "customer_name").orElseThrow();
    int camel = FuzzyMatch.score("cn", "customerName").orElseThrow();
    assertEquals(snake, camel, "snake and camel forms score the same");
  }

  @Test
  void rank_filters_and_orders() {
    List<String> items = List.of("customer_name", "customer_id", "order_total", "shipping_address");
    var indexed = FuzzyMatch.index(items, s -> s);
    List<String> ranked = FuzzyMatch.rank("cn", indexed);
    assertTrue(ranked.contains("customer_name"));
    assertEquals(
        "customer_name", ranked.get(0), "highest-scoring (word-start on both chars) ranks first");
    assertFalse(ranked.contains("order_total"), "non-matching items dropped");
  }

  @Test
  void rank_empty_query_returns_all_in_original_order() {
    List<String> items = List.of("zebra", "apple", "mango");
    var indexed = FuzzyMatch.index(items, s -> s);
    assertEquals(items, FuzzyMatch.rank("", indexed));
  }

  /// Hot-path API: scoreWith returns identical results to score for the same input — confirms
  /// the precomputed-wordStarts shortcut doesn't drift from the convenience entry point.
  @Test
  void score_with_matches_score() {
    String target = "customerName";
    Optional<Integer> a = FuzzyMatch.score("cn", target);
    Optional<Integer> b =
        FuzzyMatch.scoreWith("cn", target, target.toLowerCase(), FuzzyMatch.wordStarts(target));
    assertEquals(a, b);
  }
}
