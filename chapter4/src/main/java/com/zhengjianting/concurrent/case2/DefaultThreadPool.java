package com.zhengjianting.concurrent.case2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultThreadPool<Job extends Runnable> implements ThreadPool<Job> {
    private static final int MAX_WORKER_NUMBERS = 10; // 线程池最大限制数
    private static final int DEFAULT_WORKER_NUMBERS = 5; // 线程池默认的数量
    private static final int MIN_WORKER_NUMBERS = 1; // 线程池最小的数量

    private final List<Worker> workers = Collections.synchronizedList(new ArrayList<>()); // 工作者列表
    private int workerNum = DEFAULT_WORKER_NUMBERS; // 工作者线程的数量

    private final LinkedList<Job> jobs = new LinkedList<>(); // 这是一个工作列表, 将会向里面插入工作

    private AtomicLong threadNum = new AtomicLong(); // 线程编号生成

    public DefaultThreadPool() {
        initializeWorkers(DEFAULT_WORKER_NUMBERS);
    }

    public DefaultThreadPool(int num) {
        workerNum = num > MAX_WORKER_NUMBERS ? MAX_WORKER_NUMBERS : Math.max(num, MIN_WORKER_NUMBERS);
        initializeWorkers(workerNum);
    }

    private void initializeWorkers(int num) {
        Worker worker = new Worker();
        workers.add(worker);
        Thread thread = new Thread(worker, "ThreadPool-Worker-" + threadNum.incrementAndGet());
        thread.start();
    }

    @Override
    public void execute(Job job) {
        if (job != null) {
            synchronized (jobs) { // 1. 加锁
                jobs.addLast(job); // 2. 改变条件
                jobs.notify(); // 3. 通知
            }
        }
    }

    @Override
    public void shutdown() {
        for (Worker worker : workers) {
            worker.shutdown();
        }
    }

    @Override
    public void addWorkers(int num) {
        synchronized (jobs) {
            if (num + this.workerNum > MAX_WORKER_NUMBERS) {
                num = MAX_WORKER_NUMBERS - this.workerNum;
            }
            initializeWorkers(num);
            this.workerNum += num;
        }
    }

    @Override
    public void removeWorkers(int num) {
        synchronized (jobs) {
            if (num >= this.workerNum) {
                throw new IllegalArgumentException("beyond workNum");
            }
            // 按照给定的数量停止 Worker, 原书的实现貌似有误
            int cnt = num;
            while (cnt-- > 0) {
                Worker worker = workers.get(0);
                if (workers.remove(worker)) {
                    worker.shutdown();
                }
            }
            this.workerNum -= num;
        }
    }

    @Override
    public int getJobSize() {
        return jobs.size();
    }

    class Worker implements Runnable {
        private volatile boolean running = true; // 是否工作

        @Override
        public void run() {
            while (running) {
                Job job = null;
                synchronized (jobs) { // 1. 加锁
                    while (jobs.isEmpty()) { // 2. 条件不满足则等待
                        try {
                            jobs.wait();
                        } catch (InterruptedException e) {
                            // 感知到外部对 WorkerThread 的中断操作, 返回
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    job = jobs.removeFirst(); // 3. 处理逻辑
                }

                if (job != null) {
                    try {
                        job.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void shutdown() {
            running = false;
        }
    }
}