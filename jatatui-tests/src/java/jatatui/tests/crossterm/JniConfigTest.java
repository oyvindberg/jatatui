package jatatui.tests.crossterm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import tui.crossterm.CrosstermJni;

/// Guards the GraalVM native-image JNI reachability metadata shipped by the `crossterm` artifact.
///
/// The Rust side constructs event/command classes by name through JNI `FindClass`. native-image
/// only keeps a class that is either referenced from reachable Java code or listed in reachability
/// metadata — an unlisted, unreferenced class is stripped and the `FindClass` fails at runtime with
/// `NoClassDefFoundError`. Nothing in the Java sources references e.g. `MouseEventKind$ScrollLeft`,
/// so the metadata is the only thing keeping it alive.
///
/// The bundled config used to be a copy carried over from tui-scala and drifted behind the binding:
/// it registered 121 of the 125 `tui.crossterm` classes, and horizontal scroll events crashed
/// native-image builds. This test makes the drift a build failure instead of a user's stack trace.
public class JniConfigTest {

  private static final String CONFIG_RESOURCE =
      "META-INF/native-image/com.olvind.jatatui/crossterm/jni-config.json";

  private static final String CLASS_PREFIX = "tui.crossterm.";

  /// `"name": "..."` in the flat jni-config.json array. The file has no other string fields, so a
  /// regex beats pulling in a JSON parser for a single build-time assertion.
  private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

  @Test
  void every_crossterm_class_is_registered_for_jni() {
    Set<String> onDisk = crosstermClasses();
    Set<String> registered =
        registeredNames().stream()
            .filter(name -> name.startsWith(CLASS_PREFIX))
            .collect(Collectors.toCollection(TreeSet::new));

    assertTrue(
        onDisk.size() > 100,
        "expected to find the whole binding, found " + onDisk.size() + " classes");

    Set<String> missing = new TreeSet<>(onDisk);
    missing.removeAll(registered);
    Set<String> stale = new TreeSet<>(registered);
    stale.removeAll(onDisk);

    assertEquals(
        Set.of(),
        missing,
        () ->
            "classes present in the crossterm binding but missing from "
                + CONFIG_RESOURCE
                + " — native-image will strip them and JNI FindClass will fail at runtime. Add:\n"
                + missing.stream().map(JniConfigTest::entry).collect(Collectors.joining()));

    assertEquals(
        Set.of(),
        stale,
        () ->
            "classes registered in "
                + CONFIG_RESOURCE
                + " that no longer exist in the binding: "
                + stale);
  }

  /// The two JDK types the binding hands back and forth over JNI must stay registered too.
  @Test
  void jdk_types_used_over_jni_are_registered() {
    Set<String> registered = registeredNames();
    assertTrue(registered.contains("java.lang.Enum"), "java.lang.Enum must stay registered");
    assertTrue(registered.contains("java.util.List"), "java.util.List must stay registered");
  }

  private static String entry(String name) {
    return """
      {
        "name": "%s",
        "allDeclaredConstructors": true,
        "allPublicConstructors": true,
        "allDeclaredMethods": true,
        "allDeclaredFields": true
      },
    """
        .formatted(name);
  }

  private static Set<String> registeredNames() {
    URL url = JniConfigTest.class.getClassLoader().getResource(CONFIG_RESOURCE);
    assertNotNull(url, CONFIG_RESOURCE + " is not on the classpath");
    String json;
    try (var in = url.openStream()) {
      json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Set<String> names = new TreeSet<>();
    Matcher m = NAME.matcher(json);
    while (m.find()) {
      names.add(m.group(1));
    }
    return names;
  }

  /// Every class the `crossterm` project compiles into the `tui.crossterm` package, read back from
  /// wherever that project ended up on the classpath — a directory when built locally, a jar when
  /// consumed as a published artifact.
  private static Set<String> crosstermClasses() {
    Path root = codeSourceOf(CrosstermJni.class);
    if (Files.isDirectory(root)) {
      Path pkg = root.resolve("tui").resolve("crossterm");
      try (Stream<Path> files = Files.list(pkg)) {
        return files
            .map(p -> p.getFileName().toString())
            .filter(n -> n.endsWith(".class"))
            .map(n -> CLASS_PREFIX + n.substring(0, n.length() - ".class".length()))
            .collect(Collectors.toCollection(TreeSet::new));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    try (ZipFile zip = new ZipFile(root.toFile())) {
      List<String> entries = zip.stream().map(ZipEntry::getName).toList();
      return entries.stream()
          .filter(n -> n.startsWith("tui/crossterm/") && n.endsWith(".class"))
          .filter(n -> n.indexOf('/', "tui/crossterm/".length()) < 0)
          .map(n -> n.substring(0, n.length() - ".class".length()).replace('/', '.'))
          .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path codeSourceOf(Class<?> cls) {
    var codeSource = cls.getProtectionDomain().getCodeSource();
    assertNotNull(codeSource, "no code source for " + cls.getName());
    URL location = codeSource.getLocation();
    assertNotNull(location, "no code source location for " + cls.getName());
    try {
      return Path.of(URI.create(location.toString()));
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("unexpected code source location " + location, e);
    }
  }
}
