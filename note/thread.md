## thread

### 1. 线程简介

#### 1.1 什么是线程

现代操作系统调度的最小单元是线程，也叫轻量级进程 ( Light Weight Process )，在一个进程里可以创建多个线程，这些线程都拥有各自的计数器、堆栈和局部变量等属性，并且能够访问共享的内存变量

如果程序使用多线程技术，将计算逻辑分配到多个处理器核心上，就会显著减少程序的处理时间，并且随着更多处理器核心的加入而变得更有效率

实际上 Java 程序天生就是多线程程序，因为执行 main() 方法的是一个名为 main 的线程，下面使用 JMX 来查看一个普通的 Java 程序包含哪些线程：

```java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class MultiThread {
    public static void main(String[] args) {
        // 获取 Java 线程管理 MXBean
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        // 不需要获取同步的 monitor 和 synchronizer 信息, 仅获取线程和线程堆栈信息
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);
        // 遍历线程信息, 仅打印线程 ID 和线程名称信息
        for (ThreadInfo threadInfo : threadInfos) {
            System.out.println("[" + threadInfo.getThreadId() + "] " + threadInfo.getThreadName());
        }
    }
}
```

输出结果如下所示：

```java
[6] Monitor Ctrl-Break
[5] Attach Listener
[4] Signal Dispatcher
[3] Finalizer
[2] Reference Handler
[1] main
```

#### 1.2 线程优先级

在 Java 线程中，通过一个整形成员变量 priority 来控制优先级，优先级的范围从 1 ~ 10 ( 低 ~ 高)，在线程构建的时候可以通过 setPriority(int) 方法来修改优先级，默认优先级是 5，优先级高的线程分配时间片的数量要多于优先级低的线程

设置线程优先级时：

- 针对频繁阻塞 ( 休眠或者 I/O 操作 ) 的线程需要设置较高优先级 ( 因为使用 CPU 的时间短 )
- 而偏重计算 ( 需要较多 CPU 时间或者偏运算 ) 的线程则设置较低的优先级，确保处理器不会被独占

在不同的 JVM 以及操作系统上，线程规划会存在差异，有些操作系统甚至会忽略对线程优先级的设定

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Priority {
    private static volatile boolean notStart = true;
    private static volatile boolean notEnd = true;

    public static void main(String[] args) throws InterruptedException {
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int priority = i < 5 ? Thread.MIN_PRIORITY : Thread.MAX_PRIORITY;
            Job job = new Job(priority);
            jobs.add(job);
            Thread thread = new Thread(job, "Thread " + i);
            thread.setPriority(priority);
            thread.start();
        }
        notStart = false;
        TimeUnit.SECONDS.sleep(10);
        notEnd = false;
        for (Job job : jobs) {
            System.out.println("Job Priority = " + job.priority + ", Count = " + job.jobCount);
        }
    }

    static class Job implements Runnable {
        private int priority;
        private long jobCount;

        public Job(int priority) {
            this.priority = priority;
        }

        @Override
        public void run() {
            while (notStart) {
                Thread.yield();
            }
            while (notEnd) {
                Thread.yield();
                jobCount++;
            }
        }
    }
}
```

线程优先级不能作为程序正确性的依赖，因为操作系统可以完全不用理会 Java 线程对于优先级的设定

#### 1.3 线程的状态

Java 线程在运行的生命周期中可能处于下表所示的 6 种不同的状态，在给定的一个时刻，线程只能处于其中的一个状态

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220612135029385.png" alt="image-20220612135029385" style="zoom:80%;" />

示例代码：

```java
import java.util.concurrent.TimeUnit;

public class ThreadState {
    public static void main(String[] args) {
        new Thread(new TimeWaiting(), "TimeWaitingThread").start();
        new Thread(new Waiting(), "WaitingThread").start();
        // 使用两个 Blocked 线程, 一个获取锁成功, 另一个被阻塞
        new Thread(new Blocked(), "BlockedThread-1").start();
        new Thread(new Blocked(), "BlockedThread-2").start();
    }

    static class TimeWaiting implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class Waiting implements Runnable {
        @Override
        public void run() {
            while (true) {
                synchronized (Waiting.class) {
                    try {
                        Waiting.class.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    static class Blocked implements Runnable {
        @Override
        public void run() {
            synchronized (Blocked.class) {
                while (true) {
                    try {
                        TimeUnit.SECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
```

运行示例，打开终端键入 jps

```java
47236 ThreadState
```

接着键入 jstack 47236

```java
"BlockedThread-2" #15 prio=5 os_prio=0 tid=0x0000023bc3627000 nid=0xd30c waiting for monitor entry [0x000000f255bfe000]
   java.lang.Thread.State: BLOCKED (on object monitor)
        at com.zhengjianting.concurrent.ThreadState$Blocked.run(ThreadState.java:48)
        - waiting to lock <0x00000000d61123b0> (a java.lang.Class for com.zhengjianting.concurrent.ThreadState$Blocked)
        at java.lang.Thread.run(Thread.java:748)

"BlockedThread-1" #14 prio=5 os_prio=0 tid=0x0000023bc364a800 nid=0xdf0c waiting on condition [0x000000f255aff000]
   java.lang.Thread.State: TIMED_WAITING (sleeping)
        at java.lang.Thread.sleep(Native Method)
        at java.lang.Thread.sleep(Thread.java:340)
        at java.util.concurrent.TimeUnit.sleep(TimeUnit.java:386)
        at com.zhengjianting.concurrent.ThreadState$Blocked.run(ThreadState.java:48)
        - locked <0x00000000d61123b0> (a java.lang.Class for com.zhengjianting.concurrent.ThreadState$Blocked)
        at java.lang.Thread.run(Thread.java:748)

"WaitingThread" #13 prio=5 os_prio=0 tid=0x0000023bc3626000 nid=0xbb8c in Object.wait() [0x000000f2559ff000]
   java.lang.Thread.State: WAITING (on object monitor)
        at java.lang.Object.wait(Native Method)
        - waiting on <0x00000000d610f398> (a java.lang.Class for com.zhengjianting.concurrent.ThreadState$Waiting)
        at java.lang.Object.wait(Object.java:502)
        at com.zhengjianting.concurrent.ThreadState$Waiting.run(ThreadState.java:33)
        - locked <0x00000000d610f398> (a java.lang.Class for com.zhengjianting.concurrent.ThreadState$Waiting)
        at java.lang.Thread.run(Thread.java:748)

"TimeWaitingThread" #12 prio=5 os_prio=0 tid=0x0000023bc3622000 nid=0x106f8 waiting on condition [0x000000f2558ff000]
   java.lang.Thread.State: TIMED_WAITING (sleeping)
        at java.lang.Thread.sleep(Native Method)
        at java.lang.Thread.sleep(Thread.java:340)
        at java.util.concurrent.TimeUnit.sleep(TimeUnit.java:386)
        at com.zhengjianting.concurrent.ThreadState$TimeWaiting.run(ThreadState.java:19)
        at java.lang.Thread.run(Thread.java:748)
```

线程在自身的生命周期中，并不是固定地处于某个状态，而是随着代码的执行在不同的状态之间进行切换，Java 线程状态变迁如图所示：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220612140850567.png" alt="image-20220612140850567" style="zoom:80%;" />

Java 将操作系统中的运行和就绪两个状态合并称为运行状态。阻塞状态是线程阻塞在进入 synchronized 关键字修饰的方法或代码块 ( 获取锁 ) 时的状态，但是阻塞在 java.concurrent 包中的 Lock 接口的线程状态却是等待状态，因为 java.concurrent 包中 Lock 接口对于阻塞的实现均使用了 LockSupport 类中的相关方法

#### 1.4 Daemon 线程

Daemon 线程是一种支持型线程，因为它主要被用作程序中后台调度以及支持性工作。这意味着，当一个 Java 虚拟机中不存在非 Daemon 线程的时候，Java 虚拟机将会退出。可以通过调用 Thread.setDaemon(true) 将线程设置为 Daemon 线程

注意，Daemon 属性需要在启动线程之前设置，不能在启动线程之后设置

Daemon 线程被用作完成支持性工作，但是在 Java 虚拟机退出时 Daemon 线程中的 finally 块并不一定会执行，示例代码：

```java
import java.util.concurrent.TimeUnit;

public class Daemon {
    public static void main(String[] args) {
        Thread thread = new Thread(new DaemonRunner(), "DaemonRunner");
        thread.setDaemon(true);
        thread.start();
    }

    static class DaemonRunner implements Runnable {
        @Override
        public void run() {
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("DaemonThread finally run.");
            }
        }
    }
}
```

运行 Daemon 程序，可以看到在终端或者命令提示符上没有任何输出。main 线程 ( 非 Daemon 线程 ) 在启动了线程 DaemonRunner 之后随着 main 方法执行完毕而终止，而此时 Java 虚拟机中已经没有非 Daemon 线程，虚拟机需要退出。Java 虚拟机中的所有 Daemon 线程都需要立即终止，因此 DaemonRunner 立刻终止，但是 DaemonRunner 中的 finally 块并没有执行

注意，在构建 Daemon 线程时，不能依靠 finally 块中的内容来确保执行关闭或清理资源的逻辑

### 2. 启动和终止线程

通过调用线程的 start() 方法进行启动，随着 run() 方法的执行完毕，线程也随之终止

#### 2.1 构造线程

在运行线程之前首先要构造一个线程对象，线程对象在构造的时候需要提供线程所需要的属性，如线程所属的线程组、线程优先级、是否是 Daemon 线程等信息。以下代码摘自 java.lang.Thread 中对线程进行初始化的部分：

```java
private void init(ThreadGroup g, Runnable target, String name, long stackSize, AccessControlContext acc) {
    if (name == null) {
        throw new NullPointerException("name cannot be null");
    }
    // 当前线程就是该线程的父线程
    Thread parent = currentThread();
    this.group = g;
    // 将 daemon、priority 属性设置为父线程的对于属性
    this.daemon = parent.isDaemon();
    this.priority = parent.getPriority();
    this.name = name.toCharArray();
    this.target = target;
    setPriority(priority);
    // 将父线程的 InheritableThreadLocals 复制过来
    if (parent.inheritableThreadLocals != null)
        this.inheritableThreadLocals = ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
    // 分配一个线程 ID
    tid = nextThreadID();
}
```

在上述过程中，一个新构造的线程对象是由其 parent 线程来进行空间分配的，而 child 线程继承了 parent 是否为 Daemon、优先级和加载资源的 contextClassLoader 以及可继承的 ThreadLocal，同时还会分配一个唯一的 ID 来标识这个 child 线程。至此，一个能够运行的线程对象就初始化好了，在堆内存中等待着运行

#### 2.2 启动线程

线程对象在初始化完成之后，调用 start() 方法就可以启动这个线程。线程 start() 方法的含义是：当前线程 ( 即 parent 线程 ) 同步告知 Java 虚拟机，只要线程规划器空闲，应立即启动调用 start() 方法的线程

#### 2.3 理解中断

中断可以理解为线程的一个标识位属性，它标识一个运行中的线程是否被其它线程进行了中断操作。中断好比其它线程对该线程打了个招呼，其它线程通过调用该线程的 interrupt() 方法对其进行中断操作，线程通过方法 isInterrupted() 来进行判断是否被中断

从 Java 的 API 中可以看到，许多声明抛出 InterruptedException 的方法 ( 例如 Thread.sleep(long millis) 方法 )，这些方法在抛出 InterruptedException 之前，Java 虚拟机会先将该线程的中断标识位清除，然后抛出 InterruptedException，此时调用 isInterrupted() 方法将会返回 false

以下代码首先创建了两个线程，SleepThread 和 BusyThread，前者不停地睡眠，后者一直运行，然后对这两个线程分别进行中断操作，观察二者的中断标识位：

```java
import java.util.concurrent.TimeUnit;

public class Interrupted {
    public static void main(String[] args) throws InterruptedException {
        // sleepThread 不停地尝试睡眠
        Thread sleepThread = new Thread(new SleepRunner(), "SleepThread");
        sleepThread.setDaemon(true);

        // busyThread 不停地运行
        Thread busyThread = new Thread(new BusyRunner(), "BusyThread");
        busyThread.setDaemon(true);

        sleepThread.start();
        busyThread.start();

        // 休眠 5s, 让 sleepThread 和 busyThread 充分运行
        TimeUnit.SECONDS.sleep(5);

        sleepThread.interrupt();
        busyThread.interrupt();

        System.out.println("SleepThread interrupted is " + sleepThread.isInterrupted());
        System.out.println("BusyThread interrupted is " + busyThread.isInterrupted());

        // 防止 sleepThread 和 busyThread 立即退出
        TimeUnit.SECONDS.sleep(2);
    }

    static class SleepRunner implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class BusyRunner implements Runnable {
        @Override
        public void run() {
            while (true) {

            }
        }
    }
}
```

输出如下：

```java
SleepThread interrupted is false
BusyThread interrupted is true
```

从结果可以看出，抛出 InterruptedException 的线程 SleepThread，其中断标识位被清除了，而一直忙碌运作的线程 BusyThread，中断标识位没有被清除

#### 2.4 安全地终止线程

中断状态是线程的一个标识位，而中断操作是一种简便的线程间交互方式，而这种交互方式最适合用来取消或停止任务

除了中断以外，还可以利用一个 boolean 变量来控制是否需要停止任务并终止该线程

以下示例中，创建了一个线程 CountThread，它不断地进行变量累加，而主线程尝试用上述两种方式对其进行中断操作和停止操作

```java
import java.util.concurrent.TimeUnit;

public class Shutdown {
    public static void main(String[] args) throws Exception {
        Runner one = new Runner();
        Thread countThread = new Thread(one, "CountThread");
        countThread.start();

        // 睡眠 1s, main 线程对 CountThread 进行中断, 使 CountThread 能够感知中断而结束
        TimeUnit.SECONDS.sleep(1);
        countThread.interrupt();

        Runner two = new Runner();
        countThread = new Thread(two, "CountThread");
        countThread.start();

        // 睡眠 1s, main 线程对 Runner two 进行取消, 使 CountThread 能够感知 on 为 false 而结束
        TimeUnit.SECONDS.sleep(1);
        two.cancel();
    }

    private static class Runner implements Runnable {
        private long i;
        private volatile boolean on = true;

        @Override
        public void run() {
            while (on && !Thread.currentThread().isInterrupted()) {
                i++;
            }
            System.out.println("Count i = " + i);
        }

        public void cancel() {
            on = false;
        }
    }
}
```

输出结果：

```java
Count i = 741015088
Count i = 771871002
```

示例在执行过程中，main 线程通过中断操作和 cancel() 方法均可使 CountThread 得以终止。这种通过标识位或者中断操作的方式能够使线程在终止时有机会去清理资源，而不是武断地将线程停止，因此这种终止线程的做法显得更加安全和优雅

### 3. 线程间通信

#### 3.1 volatile 和 synchronized 关键字

关键字 volatile 可以用来修饰字段 ( 成员变量 )，就是告知程序任何对该变量的访问均需要从共享内存中获取，而对它的改变必须同步刷新回共享内存，它能保证所有线程对共享变量访问的可见性

关键字 synchronized 可以修饰方法或者以同步块的形式来进行使用，它主要确保多个线程在同一个时刻，只能有一个线程处于方法或者同步块中，它保证了线程对变量访问的可见性和排他性

任意一个对象都拥有自己的监视器，当这个对象由同步块或者这个对象的同步方法调用时，执行方法的线程必须先获取到该对象的监视器才能进入同步块或者同步方法，而没有获取到监视器 ( 执行该方法 ) 的线程将会被阻塞在同步块和同步方法的入口处，进入 BLOCKED 状态

下图描述了对象、对象的监视器、同步队列和执行线程之间的关系：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220612175432756.png" alt="image-20220612175432756" style="zoom:80%;" />

从图中可以看到，任意线程对 Object ( Object 由 synchronized 保护 ) 的访问，首先要获得 Object 的监视器。如果获取失败，线程进入同步队列，线程状态变为 BLOCKED。当访问 Object 的前驱 ( 获得了锁的线程 ) 释放了锁，则该释放操作唤醒阻塞在同步队列中的线程，使其重新尝试对监视器的获取

#### 3.2 等待 / 通知机制

一个线程修改了一个对象的值，而另一个线程感知到了变化，然后进行相应的操作，整个过程开始于一个线程，而最终执行又是另一个线程。前者是生产者，后者就是消费者，这种模式隔离了 "做什么 ( what )" 和 "怎么做" ( how )，在功能层面上实现了解耦，体系结构上具备了良好的伸缩性，但是在 Java 语言中如何实现类似的功能呢?

简单的办法是让消费者线程不断地循环检查变量是否符合预期，如下面代码所示：

```java
while (value != desire) {
    Thread.sleep(1000);
}
doSomething();
```

上面这段伪代码在条件不满足时就睡眠一段时间，这样做的目的是防止过快的 "无效" 尝试，这种方式看似能够实现所需的功能，但是却存在以下问题：

- 难以确保及时性：在睡眠时，基本不消耗处理器资源，但是如果睡得过久，就不能及时发现条件已经变化，也就是及时性难以保证
- 难以降低开销：如果降低睡眠的时间，比如休眠 1 毫秒，这样消费者能更加迅速地发现条件变化，但是却可能消耗更多的处理器资源，造成了无端的浪费

以上两个问题，看似矛盾难以调和，但是 Java 通过内置的等待 / 通知机制能够很好地解决这个矛盾并实现所需的功能

等待 / 通知的相关方法是任意 Java 对象都具备的，因为这些方法被定义在所有对象的超类 java.lang.Object 上，方法和描述如表所示：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220612210118806.png" alt="image-20220612210118806" style="zoom:80%;" />

等待 / 通知机制，是指一个线程 A 调用了对象 O 的 wait() 方法进入等待状态，而另一个线程 B 调用了对象 O 的 notify() 或者 notifyAll() 方法，线程 A 收到通知后从对象 O 的 wait() 方法返回，进而执行后续的操作。上述两个线程通过对象 O 来完成交互，而对象上的 wait() 和 notify() / notifyAll() 的关系就如同开关信号一样，用来完成等待方和通知方之间的交互工作

在以下示例代码中，创建了两个线程 —— WaitThread 和 NotifyThread，前者检查 flag 值是否为 false，如果符合要求，进行后续操作，否则在 lock 上等待，后者在睡眠了一段时间后对 lock 进行通知：

```java
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class WaitNotify {
    static boolean flag = true;
    static Object lock = new Object();

    public static void main(String[] args) throws Exception {
        Thread waitThread = new Thread(new Wait(), "WaitThread");
        waitThread.start();
        TimeUnit.SECONDS.sleep(1);
        Thread notifyThread = new Thread(new Notify(), "NotifyThread");
        notifyThread.start();
    }

    static class Wait implements Runnable {
        @Override
        public void run() {
            // 加锁, 拥有 lock 的 Monitor
            synchronized (lock) {
                // 当条件不满足时, 继续 wait, 同时释放了 lock 的锁
                while (flag) {
                    try {
                        System.out.println(Thread.currentThread() + " flag is true. wait @ " +
                                new SimpleDateFormat("HH:mm:ss").format(new Date()));
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // 条件满足时, 完成工作
                System.out.println(Thread.currentThread() + " flag is false. running @ " +
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
            }
        }
    }

    static class Notify implements Runnable {
        @Override
        public void run() {
            // 加锁, 拥有 lock 的 Monitor
            synchronized (lock) {
                // 获取 lock 的锁, 然后进行通知, 通知时不会释放 lock 的锁
                // 直到当前线程释放了 lock 后, WaitThread 重新获得锁后才能从 wait 方法返回
                System.out.println(Thread.currentThread() + " hold lock. notify @ " +
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
                lock.notifyAll();
                flag = false;
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // 再次加锁
            synchronized (lock) {
                System.out.println(Thread.currentThread() + " hold lock again. sleep @ " +
                        new SimpleDateFormat("HH:mm:ss").format(new Date()));
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
```

简易图示：其中 WaitThread 和 NotifyThread 通过对象 lock 进行交互

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220612225620718.png" alt="image-20220612225620718" style="zoom:80%;" />

输出如下：

```java
Thread[WaitThread,5,main] flag is true. wait @ 21:31:33
Thread[NotifyThread,5,main] hold lock. notify @ 21:31:34
Thread[NotifyThread,5,main] hold lock again. sleep @ 21:31:39
Thread[WaitThread,5,main] flag is false. running @ 21:31:44
```

其中第 3 行和第 4 行输出的顺序可能会互换，而上述例子主要说明了调用 wait()、notify() 以及 notifyAll() 时需要注意的细节：

- 使用 wait()、notify() 和 notifyAll() 时需要先对调用对象加锁
- 调用 wait() 方法后，线程状态由 RUNNING 变为 WAITING，并将当前线程放置到对象的等待队列
- notify() 或 notifyAll() 方法调用后，等待线程依旧不会从 wait() 返回，需要调用 notify() 或 notifyAll() 的线程释放锁之后，等待线程重新获得锁后才会从 wait() 返回
- notify() 方法将等待队列中的一个等待线程从等待队列中移到同步队列中，而 notifyAll() 则是将等待队列中的所有线程全部移到同步队列，被移动的线程状态由 WAITING 变为 BLOCKED
- 从 wait() 方法返回的前提是获得了调用对象的锁

从上述细节中可以看到，等待 / 通知机制依托于同步机制，其目的就是确保等待线程从 wait() 方法返回时能够感知到通知线程对变量做出的修改

下图描述了上述示例的过程：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220612214654484.png" alt="image-20220612214654484" style="zoom:80%;" />

在上图中，WaitThread 首先获取了对象的锁，然后调用对象的 wait() 方法，从而放弃了锁并进入了对象的等待队列 WaitQueue 中，进入等待状态。由于 WaitThread 释放了对象的锁，NotifyThread 随后获取了对象的锁，并调用对象的 notify() 方法，将 WaitThread 从 WaitQueue 移到 SynchronizedQueue 中，此时 WaitThread 的状态变为阻塞状态。NotifyThread 释放了锁之后，WaitThread 再次获取到锁并从 wait() 方法返回继续执行

#### 3.3 等待 / 通知的经典范式

从 WaitNotify 示例中可以提炼出等待 / 通知的经典范式，该范式分为两部分，分别针对等待方 ( 消费者 ) 和通知方 ( 生产者 )

等待方遵循如下原则：

- 获取对象的锁
- 如果条件不满足，那么调用对象的 wait() 方法，被通知后仍要检查条件
- 条件满足则执行对应的逻辑

对应的伪代码如下：

```java
synchronized (对象) {
    while (条件不满足) {
        对象.wait();
    }
    对应的处理逻辑
}
```

通知方遵循如下原则：

- 获得对象的锁
- 改变条件
- 通知所有等待在对象上的线程

对应的伪代码如下：

```java
synchronized (对象) {
    改变条件
    对象.notifyAll();
}
```

#### 3.4 管道输入 / 输出流

管道输入 / 输出流和普通的文件输入 / 输出流或者网络输入 / 输出流不同之处在于，它主要用于线程之间的数据传输，而传输的媒介为内存

管道输入 / 输出流主要包括了如下 4 种实现：PipedOutputStream、PipedInputStream、PipedReader 和 PipedWriter，前两种面向字节，而后两种面向字符

在以下代码示例中，创建了 PrintThread，它用来接收 main 线程的输入，任何 main 线程的输入均通过 PipedWriter 写入，而 PrintThread 在另一端通过 PipedReader 将内容读出并打印

```java
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;

public class Piped {
    public static void main(String[] args) throws Exception {
        PipedWriter out = new PipedWriter();
        PipedReader in = new PipedReader();
        // 将输出流和输入流进行连接, 否则在使用时会抛出 IOException
        out.connect(in);

        Thread printThread = new Thread(new Print(in), "PrintThread");
        printThread.start();

        int receive = 0;
        try {
            while ((receive = System.in.read()) != -1) {
                out.write(receive);
            }
        } finally {
            out.close();
        }
    }

    static class Print implements Runnable {
        private PipedReader in;

        public Print(PipedReader in) {
            this.in = in;
        }

        @Override
        public void run() {
            int receive = 0;
            try {
                while ((receive = in.read()) != -1) {
                    System.out.print((char) receive);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

#### 3.5 Thread.join() 的使用

如果一个线程 A 执行了 thread.join() 语句，其含义是：当前线程 A 等待 thread 线程终止之后才从 thread.join() 返回

线程 Thread 除了提供 join() 方法之外，还提供了 join(long millis) 和 join(long millis，int nanos) 两个具备超时特性的方法。这两个超时方法表示，如果线程 thread 在给定的超时时间里没有终止，那么将会从该超时方法返回

在以下代码示例中，创建了 10 个线程，编号 0 ~ 9，每个线程调用前一个线程的 join() 方法，也就是线程 0 结束了，线程 1 才能从 join() 方法中返回，而线程 0 需要等待 main 线程结束

```java
import java.util.concurrent.TimeUnit;

public class Join {
    public static void main(String[] args) throws Exception {
        Thread previous = Thread.currentThread();
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(new Domino(previous), String.valueOf(i));
            thread.start();
            previous = thread;
        }
        TimeUnit.SECONDS.sleep(3);
        System.out.println(Thread.currentThread().getName() + " terminate.");
    }

    static class Domino implements Runnable {
        private Thread previous;

        public Domino(Thread previous) {
            this.previous = previous;
        }

        @Override
        public void run() {
            try {
                previous.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName() + " terminate.");
        }
    }
}
```

输出如下：

```java
main terminate.
0 terminate.
1 terminate.
2 terminate.
3 terminate.
4 terminate.
5 terminate.
6 terminate.
7 terminate.
8 terminate.
9 terminate.
```

从上述输出可以看到，每个线程终止的前提是前驱线程的终止，每个线程等待前驱线程终止后，才从 join() 方法返回，这里涉及了等待 / 通知机制

以下代码是 JDK 中 Thread.join() 方法的源码 ( 进行了部分调整 )

```java
// 加锁当前线程对象
public final synchronized void join() throws InterruptedException {
    // 条件不满足, 继续等待
    while (isAlive()) {
        wait(0);
    }
    // 条件符合, 方法返回
}
```

当线程终止时，会调用线程自身的 notifyAll() 方法，会通知所有等待在该线程对象上的线程。可以看到 join() 方法的逻辑结构与等待 / 通知经典范式一致，即加锁、循环和处理 3 个步骤

#### 3.6 ThreadLocal

当访问共享的可变数据时，通常需要使用同步。一种避免使用同步的方式就是不共享数据。当某个对象封闭在一个线程中时，这种用法将自动实现线程安全性，即使被封闭的对象本身不是线程安全的。这种技术被称为线程封闭 ( Thread Confinement )，它是实现线程安全性的最简单方式之一

维持线程封闭性的一种规范方式是使用 ThreadLocal，ThreadLocal 提供了 get 和 set 等访问接口或方法，这些方法为每个使用共享变量的线程都存有一份独立的副本，因此 get 总是返回当前执行线程在调用 set 时设置的最新值

当某个线程初次调用 ThreadLocal.get() 方法时，就会调用 initialValue 来获取初始值。从概念上看，你可以将 ThreadLocal\<T\> 视为包含了 Map\<Thread，T\> 对象，其中保存了特定于该线程的值，但 ThreadLocal 的实现并非如此。这些特定于线程的值保存在 Thread 对象中，当线程终止后，这些值会被执行垃圾回收

以下代码示例中，构建了一个常用的 Profiler 类，它具有 begin() 和 end() 两个方法，而 end() 方法返回从 begin() 方法调用开始到 end() 方法被调用时的时间差，单位是毫秒：

```java
import java.util.concurrent.TimeUnit;

public class Profiler {
    // 第一次 get() 方法调用时会进行初始化 (如果 set() 方法没有调用), 每个线程会调用一次
    private static final ThreadLocal<Long> TIME_THREADLOCAL = new ThreadLocal<Long>() {
        protected Long initialValue() {
            return System.currentTimeMillis();
        }
    };

    public static final void begin() {
        TIME_THREADLOCAL.set(System.currentTimeMillis());
    }

    public static final long end() {
        return System.currentTimeMillis() - TIME_THREADLOCAL.get();
    }

    public static void main(String[] args) throws Exception {
        Profiler.begin();
        TimeUnit.SECONDS.sleep(1);
        System.out.println("Cost: " + Profiler.end() + " millis");
    }
}
```

输出结果：

```java
Cost: 1002 millis
```
