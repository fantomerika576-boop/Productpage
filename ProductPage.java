// П6. Сторінка товару.
//
// Сторінці потрібні три джерела: опис за 200 мс, ціна за 150 мс, наявність за 250 мс.
// Послідовно це 600 мс, хоча жодне джерело не залежить від інших. Якщо всі три
// стартують разом, сторінка збирається за час найповільнішого, тобто близько 250 мс.
//
// Кожне джерело має право впасти. Тоді в його поле йде заглушка, а решта сторінки
// лишається справжньою. Порожніх полів у результаті бути не може.
//
// У заготовці вже працюють: три джерела, послідовний еталон, пул, заглушки і
// робочий приклад demoTwo() на двох джерелах — читайте його першим. Дописати треба
// load(), близько 12 рядків, місце позначене TODO.
//
// Збірка і запуск:
//   javac -encoding UTF-8 ProductPage.java
//   java ProductPage

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ProductPage {

    static final int ID = 7;

    /** Сторінка товару. Жодне поле не має права бути null. */
    record Page(String product, String price, String stock) {}

    static final String NO_PRODUCT = "опис недоступний";
    static final String NO_PRICE   = "ціна недоступна";
    static final String NO_STOCK   = "наявність невідома";

    /**
     * Пул для гілок. Його треба передавати другим аргументом у supplyAsync:
     * без нього гілки поїдуть у спільний ForkJoinPool, а не в наш.
     */
    static final AtomicInteger threadNo = new AtomicInteger();
    static final ExecutorService POOL = Executors.newFixedThreadPool(4, body -> {
        Thread t = new Thread(body, "сторінка-" + threadNo.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    /** Шляхи, які зараз відповідають помилкою. Готово. */
    static final Set<String> broken = ConcurrentHashMap.newKeySet();

    /** Одне джерело: чекає ms і віддає тіло. На зламаному шляху кидає виняток. */
    static String source(String path, int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (broken.contains(path)) throw new IllegalStateException(path + " відповів 503");
        return path;
    }

    static String product(int id) { return source("/опис/" + id, 200); }
    static String price(int id)   { return source("/ціна/" + id, 150); }
    static String stock(int id)   { return source("/наявність/" + id, 250); }

    // ------------------------------------------------------- єдиний TODO

    /** Зібрати сторінку. Зразок того самого патерну на двох джерелах лежить у demoTwo(). */
    static Page load(int id) {
    
        CompletableFuture<String> a = CompletableFuture
                .supplyAsync(() -> product(id), POOL)
                .exceptionally(e -> NO_PRODUCT);
        CompletableFuture<String> b = CompletableFuture
                .supplyAsync(() -> price(id), POOL)
                .exceptionally(e -> NO_PRICE);
        CompletableFuture<String> c = CompletableFuture
                .supplyAsync(() -> stock(id), POOL)
                .exceptionally(e -> NO_STOCK);

        CompletableFuture.allOf(a, b, c).join();

        return new Page(a.join(), b.join(), c.join());
    }

    // ---------------------------------------------------------- дано нижче

    /** Послідовний еталон: 200 + 150 + 250, близько 600 мс. */
    static Page sequential(int id) {
        return new Page(product(id), price(id), stock(id));
    }

    /** Робочий приклад: два джерела разом, у кожного своя заглушка. */
    static String demoTwo(int id) {
        CompletableFuture<String> a = CompletableFuture
                .supplyAsync(() -> product(id), POOL)
                .exceptionally(e -> NO_PRODUCT);
        CompletableFuture<String> b = CompletableFuture
                .supplyAsync(() -> stock(id), POOL)
                .exceptionally(e -> NO_STOCK);
        CompletableFuture.allOf(a, b).join();
        return a.join() + " | " + b.join();
    }

    public static void main(String[] args) {
        System.out.println("послідовно:  " + timed(() -> show(sequential(ID))));
        System.out.println("demoTwo:     " + timed(() -> demoTwo(ID)));
        System.out.println("load:        " + timed(() -> show(load(ID))));

        broken.add("/ціна/" + ID);
        System.out.println("\nзламали лише ціну:");
        System.out.println("load:        " + timed(() -> show(load(ID))));

        broken.add("/опис/" + ID);
        broken.add("/наявність/" + ID);
        System.out.println("\nзламали всі три джерела:");
        System.out.println("load:        " + timed(() -> show(load(ID))));
        System.out.println("\nЖодне поле не має бути null: там, де джерело впало, стоїть заглушка.");
    }

    /** Повертає «<час> мс, <результат>» для одного виміру. */
    static String timed(java.util.function.Supplier<String> measured) {
        long t0 = System.currentTimeMillis();
        String out;
        try { out = measured.get(); }
        catch (Throwable t) { out = "виняток " + t.getClass().getSimpleName(); }
        return (System.currentTimeMillis() - t0) + " мс, " + out;
    }

    static String show(Page p) {
        return p == null ? "null" : p.product() + " | " + p.price() + " | " + p.stock();
    }
}
