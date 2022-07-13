package com.example.demo.batch;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class Worker<T, R> implements Runnable {
    private final LinkedQueue<Cargo<T, R>> queue = new LinkedQueue<>();
    private final int batchSize;
    private final Consumer<List<Cargo<T, R>>> saver;

    public Worker(Consumer<List<Cargo<T, R>>> saver, int batchSize) {
        this.saver = saver;
        this.batchSize = batchSize;
    }

    public CompletableFuture<R> add(T o) {
        Cargo<T, R> item = Cargo.of(o);
        queue.add(item);
        return item.getHearthstone();
    }

    public List<Cargo<T, R>> getItems() {
        try {
            Cargo<T, R> item = queue.take();
            List<Cargo<T, R>> items = new ArrayList<>(batchSize);
            items.add(item);
            item = queue.poll();
            while (item != null) {
                items.add(item);

                if (items.size() >= batchSize) {
                    return items;
                }

                item = queue.poll();
            }
            return items;
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void run() {
        do {
            List<Cargo<T, R>> items = getItems();
            save(items);
        } while (true);
    }

    private void save(List<Cargo<T, R>> items) {
        try {
            saver.accept(items);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            items.forEach(item -> item.getHearthstone().obtrudeException(e));
        }
    }
}
