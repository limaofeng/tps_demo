package com.example.demo.batch;


import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LinkedQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {

    private static final long serialVersionUID = -4457362206741191196L;

    static class Node<E> {
        volatile E item;
        Node<E> next;
        Node<E> previous;

        // Node(E x) { item = x; } 修改 内部类 的构造方法
        Node(E item, Node<E> next, Node<E> previous) {
            this.item = item;
            this.next = next;
            this.previous = previous;
        }
    }

    private final int capacity;
    private final AtomicInteger count = new AtomicInteger(0);
    private transient Node<E> head;
    private transient Node<E> last;
    private final ReentrantLock takeLock = new ReentrantLock();
    private final Condition notEmpty = takeLock.newCondition();
    private final ReentrantLock putLock = new ReentrantLock();
    private final Condition notFull = putLock.newCondition();
    private final List<E> list = new Li();

    private void signalNotEmpty() {
        this.takeLock.lock();
        try {
            this.notEmpty.signal();
        } finally {
            this.takeLock.unlock();
        }
    }

    private void signalNotFull() {
        this.putLock.lock();
        try {
            this.notFull.signal();
        } finally {
            this.putLock.unlock();
        }
    }

    private void insert(E x) {
        last = last.next = new Node<>(x, null, last);
        head.previous = last;
    }

    private E extract() {
        Node<E> first = head.next;
        head = first;
        head.previous = last;
        E x = first.item;
        first.item = null;
        return x;
    }

    public void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    public void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

    public LinkedQueue() {
        this(Integer.MAX_VALUE);
    }

    public LinkedQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
        last = head = new Node<>(null, null, null);
    }

    public LinkedQueue(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        this.addAll(c);
    }

    @Override
    public int size() {
        return count.get();
    }

    @Override
    public int remainingCapacity() {
        return capacity - count.get();
    }

    @Override
    public void put(E o) throws InterruptedException {
        if (o == null) {
            throw new NullPointerException();
        }
        int c;
        putLock.lockInterruptibly();
        try {
            try {
                while (count.get() == capacity) {
                    notFull.await();
                }
            } catch (InterruptedException ie) {
                notFull.signal();
                throw ie;
            }
            insert(o);
            c = count.getAndIncrement();
            if (c + 1 < capacity) {
                notFull.signal();
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0) {
            signalNotEmpty();
        }
    }

    @Override
    public boolean offer(E o, long timeout, TimeUnit unit) throws InterruptedException {

        if (o == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        int c;
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();
        try {
            for (; ; ) {
                if (count.get() < capacity) {
                    insert(o);
                    c = count.getAndIncrement();
                    if (c + 1 < capacity) {
                        notFull.signal();
                    }
                    break;
                }
                if (nanos <= 0) {
                    return false;
                }
                try {
                    nanos = notFull.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    notFull.signal();
                    throw ie;
                }
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0) {
            signalNotEmpty();
        }
        return true;
    }

    @Override
    public boolean offer(E o) {
        if (o == null) {
            throw new NullPointerException();
        }
        final AtomicInteger count = this.count;
        if (count.get() == capacity) {
            return false;
        }
        int c = -1;
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            if (count.get() < capacity) {
                insert(o);
                c = count.getAndIncrement();
                if (c + 1 < capacity) {
                    notFull.signal();
                }
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0) {
            signalNotEmpty();
        }
        return c >= 0;
    }

    @Override
    public E take() throws InterruptedException {
        E x;
        int c;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            try {
                while (count.get() == 0) {
                    notEmpty.await();
                }
            } catch (InterruptedException ie) {
                notEmpty.signal();
                throw ie;
            }

            x = extract();
            c = count.getAndDecrement();
            if (c > 1) {
                notEmpty.signal();
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) {
            signalNotFull();
        }
        return x;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E x;
        int c;
        long nanos = unit.toNanos(timeout);
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            for (; ; ) {
                if (count.get() > 0) {
                    x = extract();
                    c = count.getAndDecrement();
                    if (c > 1) {
                        notEmpty.signal();
                    }
                    break;
                }
                if (nanos <= 0) {
                    return null;
                }
                try {
                    log.debug("等待时间:" + nanos + "\t" + TimeUnit.NANOSECONDS.toMillis(nanos));
                    nanos = notEmpty.awaitNanos(nanos);
                    log.debug("剩余时间:" + nanos + "\t" + TimeUnit.NANOSECONDS.toMillis(nanos));
                } catch (InterruptedException ie) {
                    notEmpty.signal();
                    throw ie;
                }
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) {
            signalNotFull();
        }
        return x;
    }

    @Override
    public E poll() {
        final AtomicInteger count = this.count;
        if (count.get() == 0) {
            return null;
        }
        E x = null;
        int c = -1;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            if (count.get() > 0) {
                x = extract();
                c = count.getAndDecrement();
                if (c > 1) {
                    notEmpty.signal();
                }
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) {
            signalNotFull();
        }
        return x;
    }

    @Override
    public E peek() {
        if (count.get() == 0) {
            return null;
        }
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            Node<E> first = head.next;
            if (first == null) {
                return null;
            } else {
                return first.item;
            }
        } finally {
            takeLock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        if (o == null) {
            return false;
        }
        boolean removed = false;
        fullyLock();
        try {
            Node<E> trail = head;
            Node<E> p = head.next;
            while (p != null) {
                if (o.equals(p.item)) {
                    removed = true;
                    break;
                }
                trail = p;
                p = p.next;
            }
            if (removed) {
                p.item = null;
                trail.next = p.next;
                if (p.next != null) {
                    p.next.previous = trail;
                }
                if (count.getAndDecrement() == capacity) {
                    notFull.signalAll();
                }
            }
        } finally {
            fullyUnlock();
        }
        return removed;
    }

    @Override
    public Object[] toArray() {
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next) {
                a[k++] = p.item;
            }
            return a;
        } finally {
            fullyUnlock();
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size) {
                a = (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
            }
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next) {
                a[k++] = (T) p.item;
            }
            return a;
        } finally {
            fullyUnlock();
        }
    }

    @Override
    public String toString() {
        fullyLock();
        try {
            return super.toString();
        } finally {
            fullyUnlock();
        }
    }

    @Override
    public void clear() {
        fullyLock();
        try {
            head.next = null;
            assert head.item == null;
            last = head;
            if (count.getAndSet(0) == capacity) {
                notFull.signalAll();
            }
        } finally {
            fullyUnlock();
        }
    }

    protected void _clear() {
        head.next = null;
        assert head.item == null;
        last = head;
        if (count.getAndSet(0) == capacity) {
            notFull.signalAll();
        }
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        Node<E> first;
        fullyLock();
        try {
            first = head.next;
            head.next = null;
            assert head.item == null;
            last = head;
            if (count.getAndSet(0) == capacity) {
                notFull.signalAll();
            }
        } finally {
            fullyUnlock();
        }
        int n = 0;
        for (Node<E> p = first; p != null; p = p.next) {
            c.add(p.item);
            p.item = null;
            ++n;
        }
        return n;
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        fullyLock();
        try {
            int n = 0;
            Node<E> p = head.next;
            while (p != null && n < maxElements) {
                c.add(p.item);
                p.item = null;
                p = p.next;
                ++n;
            }
            if (n != 0) {
                head.next = p;
                assert head.item == null;
                if (p == null) {
                    last = head;
                }
                if (count.getAndAdd(-n) == capacity) {
                    notFull.signalAll();
                }
            }
            return n;
        } finally {
            fullyUnlock();
        }
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        private Node<E> current;
        private Node<E> lastRet;
        private E currentElement;

        Itr() {
            final ReentrantLock putLock = LinkedQueue.this.putLock;
            final ReentrantLock takeLock = LinkedQueue.this.takeLock;
            putLock.lock();
            takeLock.lock();
            try {
                current = head.next;
                if (current != null) {
                    currentElement = current.item;
                }
            } finally {
                takeLock.unlock();
                putLock.unlock();
            }
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public E next() {
            final ReentrantLock putLock = LinkedQueue.this.putLock;
            final ReentrantLock takeLock = LinkedQueue.this.takeLock;
            putLock.lock();
            takeLock.lock();
            try {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                E x = currentElement;
                lastRet = current;
                current = current.next;
                if (current != null) {
                    currentElement = current.item;
                }
                return x;
            } finally {
                takeLock.unlock();
                putLock.unlock();
            }
        }

        @Override
        public void remove() {
            if (lastRet == null) {
                throw new IllegalStateException();
            }
            final ReentrantLock putLock = LinkedQueue.this.putLock;
            final ReentrantLock takeLock = LinkedQueue.this.takeLock;
            putLock.lock();
            takeLock.lock();
            try {
                Node<E> node = lastRet;
                lastRet = null;
                Node<E> trail = head;
                Node<E> p = head.next;
                while (p != null && p != node) {
                    trail = p;
                    p = p.next;
                }
                if (p == node) {
                    assert p != null;
                    p.item = null;
                    trail.next = p.next;
                    int c = count.getAndDecrement();
                    if (c == capacity) {
                        notFull.signalAll();
                    }
                }
            } finally {
                takeLock.unlock();
                putLock.unlock();
            }
        }
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        fullyLock();
        try {
            s.defaultWriteObject();
            for (Node<E> p = head.next; p != null; p = p.next) {
                s.writeObject(p.item);
            }
            s.writeObject(null);
        } finally {
            fullyUnlock();
        }
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        count.set(0);
        last = head = new Node<>(null, null, null);
        for (; ; ) {
            E item = (E) s.readObject();
            if (item == null) {
                break;
            }
            add(item);
        }
    }

    public E get(int index) {
        return entry(index).item;
    }

    private Node<E> entry(int index) {
        int size = count.get();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        Node<E> e = head;
        if (index < (size >> 1)) {
            for (int i = 0; i <= index; i++) {
                e = e.next;
            }
        } else {
            for (int i = size; i > index; i--) {
                e = e.previous;
            }
        }
        return e;
    }

    public boolean add(int index, E o) {
        final AtomicInteger count = this.count;
        if (count.get() == capacity) {
            return false;
        }
        int c = -1;
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            if (count.get() < capacity) {
                if (index >= size()) {
                    this.insert(o);
                } else {
                    Node<E> p = entry(index);
                    p.previous.next = p.previous = new Node<>(o, p, p.previous);
                }
                c = count.getAndIncrement();
                if (c + 1 < capacity) {
                    notFull.signal();
                }
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0) {
            signalNotEmpty();
        }
        return c >= 0;
    }

    public int indexOf(E o) {
        int index = 0;
        if (o == null) {
            for (Node<E> e = head.next; e != head; e = e.next) {
                if (e.item == null) {
                    return index;
                }
                index++;
            }
        } else {
            for (Node<E> e = head.next; e != head; e = e.next) {
                if (o.equals(e.item)) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    public E set(int index, E element) {
        Node<E> e = entry(index);
        E oldVal = e.item;
        e.item = element;
        return oldVal;
    }

    public E remove(int index) {
        E oldVal = entry(index).item;
        boolean removed = remove(oldVal);
        return removed ? oldVal : null;
    }

    public void poll(long timeout, TimeUnit unit, PollCallBack<E> pollCallBack) throws InterruptedException {
        int c = -1;
        long nanos = unit.toNanos(timeout);
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            for (; ; ) {
                if (count.get() > 0) {
                    E x = extract();
                    c = count.getAndDecrement();
                    if (pollCallBack.CallBack(x, this.peek())) {
                        continue;
                    } else {
                        break;
                    }
                }
                if (nanos <= 0) {
                    break;
                }
                try {
                    nanos = notEmpty.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    notEmpty.signal();
                    throw ie;
                }
            }
        } finally {
            takeLock.unlock();
        }
        if (c > 1) {
            notEmpty.signal();
        }
        if (c == capacity) {
            signalNotFull();
        }
    }

    public interface PollCallBack<E> {

        boolean CallBack(E current, E next);
    }

    public List<E> list() {
        return list;
    }

    private class Li implements List<E> {

        @Override
        public boolean add(E o) {
            return LinkedQueue.this.add(o);
        }

        @Override
        public void add(int index, E element) {
            LinkedQueue.this.add(index, element);
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            return LinkedQueue.this.addAll(c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends E> c) {
            throw new RuntimeException("null method");
        }

        @Override
        public void clear() {
            LinkedQueue.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return LinkedQueue.this.contains(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return LinkedQueue.this.containsAll(c);
        }

        @Override
        public E get(int index) {
            return LinkedQueue.this.get(index);
        }

        @Override
        public int indexOf(Object o) {
            return LinkedQueue.this.indexOf((E) o);
        }

        @Override
        public boolean isEmpty() {
            return LinkedQueue.this.isEmpty();
        }

        @Override
        public Iterator<E> iterator() {
            return LinkedQueue.this.iterator();
        }

        @Override
        public int lastIndexOf(Object o) {
            throw new RuntimeException("null method");
        }

        @Override
        public ListIterator<E> listIterator() {
            throw new RuntimeException("null method");
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            throw new RuntimeException("null method");
        }

        @Override
        public boolean remove(Object o) {
            return LinkedQueue.this.remove(o);
        }

        @Override
        public E remove(int index) {
            return LinkedQueue.this.remove(index);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return LinkedQueue.this.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return LinkedQueue.this.retainAll(c);
        }

        @Override
        public E set(int index, E element) {
            return LinkedQueue.this.set(index, element);
        }

        @Override
        public int size() {
            return LinkedQueue.this.size();
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            throw new RuntimeException("null method");
        }

        @Override
        public Object[] toArray() {
            return LinkedQueue.this.toArray();
        }

        @Override
        @SuppressWarnings({"SuspiciousToArrayCall"})
        public <T> T[] toArray(T[] a) {
            return LinkedQueue.this.toArray(a);
        }
    }
}
