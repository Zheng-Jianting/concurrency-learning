package com.zhengjianting.concurrent.case2;

public interface ThreadPool<Job extends Runnable> {
    void execute(Job job); // 执行一个 Job, 这个 Job 需要实现 Runnable
    void shutdown(); // 关闭线程池
    void addWorkers(int num); // 增加工作者线程
    void removeWorkers(int num); // 减少工作者线程
    int getJobSize(); // 得到正在等待执行的任务数量
}