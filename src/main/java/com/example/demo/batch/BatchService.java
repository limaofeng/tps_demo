package com.example.demo.batch;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface BatchService<T, R> {

  /**
   * 生成批量提交服务
   *
   * @param saver 保存方法
   * @param batchSize 批处理大小
   * @param works 工人数量
   * @param <T> 类型
   * @return BatchService<T>
   */
  static <T, R> BatchService<T, R> create(
      Consumer<List<Cargo<T, R>>> saver, int batchSize, int works) {
    return new DefaultBatchService<>(saver, batchSize, works);
  }

  CompletableFuture<R> submit(T entity);
}
