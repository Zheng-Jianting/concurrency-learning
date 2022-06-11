## 原子操作

原子操作意为 "不可被中断的一个或一系列操作"，即一个线程读改写共享变量，不会因为其它线程的并发访问而导致错误的结果

在 Java 中可以通过锁和循环 CAS 的方式来实现原子操作

### 1. 使用循环 CAS 实现原子操作

CAS ( Compare and Swap ) 操作需要输入两个数值，一个旧值 ( 期望操作前的值 ) 和一个新值，在操作期间先比较旧值有没有发生变化，如果没有发生变化，才交换成新值，发生了变化则不交换

自旋 CAS 实现的基本思路就是循环进行 CAS 操作直到成功为止，以下代码实现了一个基于 CAS 线程安全的计数器方法 safeCount 和一个非线程安全的计数器 count

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Counter {
    private AtomicInteger atomicI = new AtomicInteger(0);
    private int i = 0;

    public static void main(String[] args) {
        final Counter cas = new Counter();
        List<Thread> ts = new ArrayList<>(600);
        long start = System.currentTimeMillis();

        for (int j = 0; j < 100; j++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10000; i++) {
                        cas.count();
                        cas.safeCount();
                    }
                }
            });
            ts.add(thread);
        }

        for (Thread t : ts) {
            t.start();
        }

        // 等待所有线程执行完毕
        for (Thread t : ts) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println(cas.i);
        System.out.println(cas.atomicI);
        System.out.println(System.currentTimeMillis() - start + "ms");
    }

    // 使用 CAS 实现线程安全计数器
    private void safeCount() {
        for (;;) {
            int i = atomicI.get();
            boolean suc = atomicI.compareAndSet(i, ++i);
            if (suc) {
                break;
            }
        }
    }

    // 非线程安全计数器
    private void count() {
        i++;
    }
}
```

**CAS 实现原子操作的三大问题**

- ABA 问题

  如果一个值原来是 A，变成了 B，又变成了 A，那么使用 CAS 进行检查时会发现它的值没有发生变化，但是实际上却变化了

  ABA 问题的解决思路就是使用版本号，在变量前面追加上版本号，每次变量更新的时候把版本号加 1，那么 A → B → A 就会变成 1A → 2B → 3A

  从 JDK 1.5 开始，JDK 的 Atomic 包里提供了一个类 AtomicStampedReference 来解决 ABA 问题。这个类的 compareAndSet 方法的作用是首先检查当前引用是否等于预期引用，并且检查当前标志是否等于预期标志，如果全部相等，则以原子方式将该引用和该标志的值设置为给定的更新值

  ```java
  public boolean compareAndSet(
      V expectedReference,  // 预期引用
      V newReference,       // 更新后的引用
      int expectedStamp,    // 预期标志
      int newStamp          // 更新后的标志
  )
  ```

- 循环时间长开销大

  自旋 CAS 如果长时间不成功，会给 CPU 带来非常大的执行开销。如果 JVM 能支持处理器提供的 pause 指令，那么效率会有一定的提升

- 只能保证一个共享变量的原子操作

  当对一个共享变量执行操作时，我们可以使用循环 CAS 的方式来保证原子操作，但是对多个共享变量操作时，循环 CAS 就无法保证操作的原子性，这个时候就可以用锁

  还有一个取巧的办法，就是把多个共享变量合并成一个共享变量来操作。比如，有两个共享变量 i = 2，j = a，合并一下 ij = 2a，然后用 CAS 来操作 ij

  从 Java 1.5 开始，JDK 提供了 AtomicReference 类来保证引用对象之间的原子性，就可以把多个变量放在一个对象里来进行 CAS 操作

### 2. 使用锁机制实现原子操作

锁机制保证了只有获得锁的线程才能够操作锁定的内存区域

JVM 内部实现了很多种锁机制，有偏向锁、轻量级锁和互斥锁。有意思的是除了偏向锁，JVM 实现锁的方式都用了循环 CAS，即当一个线程想进入同步块的时候使用循环 CAS 的方式来获取锁，当它退出同步块的时候使用循环 CAS 释放锁