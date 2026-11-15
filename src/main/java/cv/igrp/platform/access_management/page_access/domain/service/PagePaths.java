package cv.igrp.platform.access_management.page_access.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Matches a frontend request path to a registered page path (v3-26, #34),
 * with the Next.js App Router rules the page manifests are written in:
 * <ul>
 *   <li>a route group {@code (name)} and a parallel-route slot {@code @name}
 *       are not part of the URL and are ignored;</li>
 *   <li>{@code [param]} matches one segment;</li>
 *   <li>{@code [...param]} matches one or more segments, {@code [[...param]]}
 *       zero or more (both only as the last segment);</li>
 *   <li>every other segment matches itself exactly (case-sensitive).</li>
 * </ul>
 * When several patterns match, the most specific wins, as in Next.js: per
 * segment, static before dynamic before catch-all before optional catch-all,
 * and a page that ends where the path ends before one with a further optional
 * catch-all. Equally specific patterns are ordered by text, so the answer is
 * always the same.
 *
 * <p>Paths are compared without the query, the fragment and a trailing slash.
 * A path a router could resolve to another page ({@code .} or {@code ..}
 * segments, an empty segment, a backslash, an encoded {@code /}, {@code \} or
 * {@code .}) matches nothing, so it is denied.
 */
public final class PagePaths {

    private static final Pattern UNSAFE_ENCODING = Pattern.compile("%(2f|5c|2e)");

    private PagePaths() {
    }

    /** Segment kinds, most specific first. */
    private enum Kind {
        STATIC, DYNAMIC, CATCH_ALL, OPTIONAL_CATCH_ALL
    }

    private record Segment(Kind kind, String text) {

        static Segment of(String raw) {
            if (raw.startsWith("[[...") && raw.endsWith("]]")) {
                return new Segment(Kind.OPTIONAL_CATCH_ALL, raw);
            }
            if (raw.startsWith("[...") && raw.endsWith("]")) {
                return new Segment(Kind.CATCH_ALL, raw);
            }
            if (raw.startsWith("[") && raw.endsWith("]")) {
                return new Segment(Kind.DYNAMIC, raw);
            }
            return new Segment(Kind.STATIC, raw);
        }
    }

    /** A pattern, parsed once. */
    private record Parsed(String pattern, List<Segment> segments) {
    }

    /** The pattern among {@code patterns} that serves {@code path}, the most specific first. */
    public static Optional<String> match(String path, Collection<String> patterns) {
        Optional<List<String>> request = requestSegments(path);
        if (request.isEmpty()) {
            return Optional.empty();
        }
        return patterns.stream()
                .map(pattern -> new Parsed(pattern, parse(pattern)))
                .filter(parsed -> matches(request.get(), parsed.segments()))
                .min(Comparator.comparing(Parsed::segments, PagePaths::bySpecificity)
                        .thenComparing(Parsed::pattern))
                .map(Parsed::pattern);
    }

    /** Whether the pattern is a valid page path: absolute, catch-alls only last. */
    public static boolean isValid(String pattern) {
        if (pattern == null || !pattern.startsWith("/")) {
            return false;
        }
        List<Segment> segments = parse(pattern);
        for (int i = 0; i < segments.size() - 1; i++) {
            Kind kind = segments.get(i).kind();
            if (kind == Kind.CATCH_ALL || kind == Kind.OPTIONAL_CATCH_ALL) {
                return false;
            }
        }
        return true;
    }

    private static List<Segment> parse(String pattern) {
        List<Segment> result = new ArrayList<>();
        for (String segment : pattern.split("/")) {
            boolean group = segment.startsWith("(") && segment.endsWith(")");
            if (!segment.isEmpty() && !group && !segment.startsWith("@")) {
                result.add(Segment.of(segment));
            }
        }
        return result;
    }

    /** The request path's segments, or empty when the path is not one a page can safely serve. */
    private static Optional<List<String>> requestSegments(String path) {
        if (path == null || !path.startsWith("/")) {
            return Optional.empty();
        }
        String clean = path;
        int cut = indexOfAny(clean, '?', '#');
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }
        if (clean.length() > 1 && clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.indexOf('\\') >= 0 || UNSAFE_ENCODING.matcher(clean.toLowerCase(Locale.ROOT)).find()) {
            return Optional.empty();
        }
        List<String> result = new ArrayList<>();
        String[] parts = clean.substring(1).split("/", -1);
        for (String part : parts) {
            if (part.equals(".") || part.equals("..") || (part.isEmpty() && parts.length > 1)) {
                return Optional.empty();
            }
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return Optional.of(result);
    }

    private static int indexOfAny(String text, char... characters) {
        int first = -1;
        for (char character : characters) {
            int at = text.indexOf(character);
            if (at >= 0 && (first < 0 || at < first)) {
                first = at;
            }
        }
        return first;
    }

    private static boolean matches(List<String> request, List<Segment> pattern) {
        for (int i = 0; i < pattern.size(); i++) {
            Segment segment = pattern.get(i);
            Kind kind = segment.kind();
            if (kind == Kind.OPTIONAL_CATCH_ALL) {
                return true;
            }
            if (kind == Kind.CATCH_ALL) {
                return request.size() > i;
            }
            if (request.size() <= i || (kind == Kind.STATIC && !request.get(i).equals(segment.text()))) {
                return false;
            }
        }
        return request.size() == pattern.size();
    }

    /**
     * Next.js order: segment kinds left to right. When one pattern is a prefix
     * of the other (the longer can only add an optional catch-all, since both
     * matched), the shorter, exact page wins.
     */
    private static int bySpecificity(List<Segment> a, List<Segment> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int byKind = a.get(i).kind().compareTo(b.get(i).kind());
            if (byKind != 0) {
                return byKind;
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}
