package cv.igrp.platform.access_management.security_audit.application.export;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Turns a Spring Data page-fetching function into a lazily-evaluated
 * {@link Stream}. Pages are pulled one chunk at a time as the stream is
 * consumed, so an export of an arbitrarily large result set never holds more
 * than one chunk of entities in memory (requirements.md N2/N3 — flat memory up
 * to 100k rows). Each chunk is an independent query, so this works with
 * {@code spring.jpa.open-in-view=false} and off the request thread.
 */
public final class PagedStream {

    private PagedStream() {
    }

    /**
     * @param fetchPage  fetches one page for the given {@link Pageable}
     * @param mapper     maps each entity to the streamed element (applied lazily)
     * @param firstPage  page request for the first chunk; subsequent chunks
     *                   advance the page number, preserving the sort and size
     */
    public static <E, T> Stream<T> of(
            Function<Pageable, Page<E>> fetchPage,
            Function<E, T> mapper,
            Pageable firstPage) {

        Iterator<T> iterator = new Iterator<>() {
            private Pageable next = firstPage;
            private Iterator<E> current = Collections.emptyIterator();
            private boolean exhausted = false;

            private void ensureLoaded() {
                while (!current.hasNext() && !exhausted) {
                    Page<E> page = fetchPage.apply(next);
                    current = page.getContent().iterator();
                    if (page.hasNext()) {
                        next = page.nextPageable();
                    } else {
                        exhausted = true;
                    }
                }
            }

            @Override
            public boolean hasNext() {
                ensureLoaded();
                return current.hasNext();
            }

            @Override
            public T next() {
                ensureLoaded();
                if (!current.hasNext()) {
                    throw new NoSuchElementException();
                }
                return mapper.apply(current.next());
            }
        };

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                false);
    }
}
