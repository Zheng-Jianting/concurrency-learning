## volatile

### 1. 定义

Java 编程语言允许线程访问共享变量，为了确保共享变量能被准确和一致地更新，线程应该确保通过排他锁单独获得单独获得这个变量。Java 语言提供了 volatile，在某些情况下比锁要更加方便。如果一个字段被声明为 volatile，Java 线程内存模型确保所有线程看到这个变量的值是一致的

### 2. 内存可见性

volatile 是轻量级的 synchronized，它比 synchronized 的使用和执行成本更低，因为它不会引起线程上下文的切换和调度

volatile 在多处理器开发中保证了共享变量的 "可见性"

内存可见性是指某个线程对共享内存进行读写之后，其它线程能立即看到共享内存的变化

下面的例子演示了可见性错误：

假设有三个线程都对变量 a 执行 a++ 操作，起初，它们都从主内存中读取变量 a 的值到各自的本地内存中：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220608232525696.png" alt="image-20220608232525696" style="zoom:80%;" />

假设线程 A 先执行，它在本地内存中将变量 a 加一之后写回主内存：

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220608232551303.png" alt="image-20220608232551303" style="zoom:80%;" />

此时，线程 B 和 线程 C 在本地内存中缓存的变量 a 是旧值 0，等它们都执行完毕之后，主内存中变量 a 的值将会是 1，而不是预期的 3

当然，线程从主内存读取变量的值到本地内存的时机可以稍晚一些，当线程数为 n ( n > 2 ) 时，主内存最终可能的值为：[1，n]

另外一个类似的问题是两个线程都执行 100 次 i++ 操作，最后 i 可能的值为：[2，200]

### 3. 硬件层面上 volatile 的实现原理

在 x86 处理器上对 volatile 进行写操作时，会引发以下两件事情：

- 将当前处理器缓存行的数据写回到系统内存
- 这个写回内存的操作会使在其他 CPU 里缓存了该内存地址的数据无效

如下图所示，为了提高处理速度，处理器不直接和内存进行通信，而是先将系统内存的数据读到内部缓存 ( L1，L2 或其他 ) 后再进行操作，但操作完不知道何时会写到内存。如果对声明了 volatile 的变量进行写操作，JVM 就会向处理器发送一条 Lock 前缀的指令，将这个变量所在的缓存行的数据写回到系统内存。但是，就算写回到内存，如果其他处理器缓存的值还是旧的，再执行计算操作就会有问题。所以，在多处理器下，为了保证各个处理器的缓存是一致的，就会实现缓存一致性协议，每个处理器通过嗅探在总线上传播的数据来检查自己缓存的值是不是过期了，当处理器发现自己缓存行对应的内存地址被修改，就会将当前处理器的缓存行设置为无效状态，当处理器对这个数据进行修改操作的时候，会重新从系统内存中把数据读到处理器缓存里

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220609223030547.png" alt="image-20220609223030547" style="zoom:80%;" />

### 4. Java 内存模型中 volatile 的内存语义

理解 volatile 特性的一个好方法是把 volatile 变量的单个读 / 写，看成是使用同一个锁对这些单个读 / 写操作做了同步。下面通过具体的示例来说明，示例代码如下：

```java
class VolatileFeaturesExample {
    volatile long v1 = 0L; // 使用 volatile 声明 64 位的 long 型变量
    public void set(long l) {
        v1 = l; // 单个 volatile 变量的写
    }
    public void getAndIncrement() {
        v1++; // 复合 (多个) volatile 变量的读/写
    }
    public long get() {
        return v1; // 单个 volatile 变量的读
    }
}
```

假设有多个线程分别调用上面程序的 3 个方法，这个程序在语义上和下面程序等价：

```java
class VolatileFeaturesExample {
    long v1 = 0L; // 64 位的 long 型普通变量
    public synchronized void set(long l) { // 对单个普通变量的写用同一个锁同步
        v1 = l;
    }
    public void getAndIncrement() { // 普通方法调用
        long temp = get(); // 调用已同步的读方法
        temp += 1L; // 普通写操作
        set(temp); // 调用已同步的写方法
    }
    public synchronized long get() { // 对单个普通变量的读用同一个锁同步
        return v1;
    }
}
```

简而言之，volatile 变量自身具有下列特性：

- 可见性：对一个 volatile 变量的读，总是能看到 ( 任意线程 ) 对这个 volatile 变量最后的写入
- 原子性：对任意单个 volatile 变量的读 / 写具有原子性，**但类似于 volatile++ 这种复合操作不具有原子性**

从内存语义的角度来说，volatile 的写 - 读与锁的释放 - 获取有相同的内存效果：volatile 写和锁的释放有相同的内存语义，volatile 读与锁的获取有相同的内存语义

volatile 写 - 读的内存语义：

- 当写一个 volatile 变量时，JMM 会把该线程对应的本地内存中的共享变量值刷新到主内存
- 当读一个 volatile 变量时，JMM 会把该线程对应的本地内存置为无效，线程接下来将从主内存中读取共享变量

<img src="C:\Users\zjt\AppData\Roaming\Typora\typora-user-images\image-20220609223232945.png" alt="image-20220609223232945" style="zoom:80%;" />

从 JSR-133 开始 ( 即从 JDK 5 开始 )，volatile 变量的写 - 读可以实现线程之间的通信：

- 线程 A 写一个 volatile 变量，实质上是线程 A 向接下来将要读这个 volatile 变量的某个线程发出了 ( 其对共享变量所做修改的 ) 消息
- 线程 B 读一个 volatile 变量，实质上是线程 B 接收了之前某个线程发出的 ( 对共享变量所做修改的 ) 消息
- 线程 A 写一个 volatile 变量，随后线程 B 读这个 volatile 变量，这个过程实质上是线程 A 通过主内存向线程 B 发送消息