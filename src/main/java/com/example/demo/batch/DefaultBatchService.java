package com.example.demo.batch;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DefaultBatchService<T, R> implements BatchService<T, R> {

  public AtomicInteger atomicInteger = new AtomicInteger(0);
  public ConcurrentHashMap<Integer, Worker<T, R>> cache = new ConcurrentHashMap<>();

  private final int workerNumber;

  public Executor asyncServiceExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(100);
    executor.setMaxPoolSize(100);
    executor.setQueueCapacity(999);
    executor.setKeepAliveSeconds(30);
    executor.setThreadNamePrefix("bath_service");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  public DefaultBatchService(Consumer<List<Cargo<T, R>>> saver, int batchSize, int works) {
    workerNumber = works;
    for (int i = 0; i < workerNumber; i++) {
      Worker<T, R> task = new Worker<>(saver, batchSize);
      cache.put(i, task);
      Executor executor = asyncServiceExecutor();
      executor.execute(task);
    }
  }

  public final int getAndIncrement() {
    int current;
    int next;
    do {
      current = this.atomicInteger.get();
      next = current >= 214748364 ? 0 : current + 1;
    } while (!this.atomicInteger.compareAndSet(current, next));
    return next;
  }

  @Override
  public CompletableFuture<R> submit(T entity) {
    int count = getAndIncrement();
    int position = count % workerNumber;
    Worker<T, R> queue = cache.get(position);
    return queue.add(entity);
  }
}
