package io.mewbase.binder;

import io.mewbase.MewbaseTestBase;
import io.mewbase.binders.BinderStore;
import io.mewbase.binders.KeyVal;

import io.mewbase.binders.impl.lmdb.LmdbBinderStore;
import io.mewbase.bson.BsonObject;
import io.mewbase.binders.Binder;


import io.mewbase.server.MewbaseOptions;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;


import java.util.HashSet;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.*;

/**
 * <p>
 * Created by tim on 14/10/16.
 */
@RunWith(VertxUnitRunner.class)
public class BindersTest extends MewbaseTestBase {



    @Test
    public void testCreateBinderStore() throws Exception {
        BinderStore store = BinderStore.instance(createMewbaseOptions());
        // doesnt throw exceptions and does return a valid handle to store
        assertNotNull(store);
    }


    @Test
    public void testOpenBinders() throws Exception {

        // set up the store and add some binders
        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();
        BinderStore store = BinderStore.instance(createMewbaseOptions());

        final int numBinders = 10;
        Binder[] all = new Binder[numBinders];
        IntStream.range(0, all.length).forEach( i -> {
            all[i] = store.open(testBinderName + i);
        });

        Set<String> bindersSet1 = store.binderNames().collect(toSet());
        for (int i = 0; i < numBinders; i++) {
            assertTrue(bindersSet1.contains(testBinderName + i));
        }

        final String name = "YetAnother" + testBinderName;
        store.open(name);
        Set<String> bindersSet2 = store.binderNames().collect(toSet());
        assertTrue(bindersSet2.contains(name));
        assertEquals(bindersSet1.size() + 1, bindersSet2.size());

    }


   @Test
   public void testSimplePutGet() throws Exception {

       BinderStore store = BinderStore.instance(createMewbaseOptions());
       final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

       Binder binder = store.open(testBinderName);
       BsonObject docPut = createObject();
       assertNull(binder.put("id1234", docPut).get());
       BsonObject docGet = binder.get("id1234").get();
       assertEquals(docPut, docGet);

       // and rewrite over-writes same key with new value
       BsonObject docOverwrite = new BsonObject().put("wib", false);
       binder.put("id1234",docOverwrite).get();
       docGet = binder.get("id1234").get();
       assertEquals(docOverwrite, docGet);
       assertNotEquals(docPut,docGet);
    }


    @Test
    public void testAsyncWriteReadInterleaved() throws Exception {

        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

        final BinderStore store = BinderStore.instance(createMewbaseOptions());
        final Binder binder = store.open(testBinderName);

        final String TEST_KEY  = "InOrderTest";
        final BsonObject docToWrite = new BsonObject().put("Thing1", "Bad").put("Thing2", "Worse");

        int iterations = 256;
        CountDownLatch countThemAllHome = new CountDownLatch(iterations);

        IntStream.rangeClosed(1, iterations).forEach(i -> {
            binder.put(TEST_KEY + String.valueOf(i), docToWrite);
            binder.get(TEST_KEY + String.valueOf(i)).whenComplete( (doc,exp) -> {
                assertNotNull(doc);
                assertNull(exp);
                countThemAllHome.countDown();
            });
        });

        countThemAllHome.await();
    }



    @Test
    public void testPutGetDifferentBinders() throws Exception {

        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

        final String B1 = testBinderName + "1";
        final String B2 = testBinderName + "2";

        BinderStore store = BinderStore.instance(createMewbaseOptions());
        Binder binder1 = store.open(B1);
        Binder binder2 = store.open(B2);

        BsonObject docPut1 = createObject();
        docPut1.put("binder", "binder1");
        assertNull(binder1.put("id0", docPut1).get());

        BsonObject docPut2 = createObject();
        docPut2.put("binder", "binder2");
        assertNull(binder2.put("id0", docPut2).get());

        BsonObject docGet1 = binder1.get("id0").get();
        assertEquals("binder1", docGet1.remove("binder"));

        BsonObject docGet2 = binder2.get("id0").get();
        assertEquals("binder2", docGet2.remove("binder"));
    }


    @Test
    public void testBinderIsPersistent() throws Exception {

        final MewbaseOptions OPTIONS = createMewbaseOptions();
        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

        BinderStore store = BinderStore.instance(OPTIONS);
        Binder binder = store.open(testBinderName);
        BsonObject docPut = createObject();
        binder.put("id1234", docPut).get();


        BinderStore store2 = BinderStore.instance(OPTIONS);
        Binder binder2 = store2.open(testBinderName);
        BsonObject docGet = binder2.get("id1234").get();
        assertEquals(docPut, docGet);
    }


    @Test
    public void testBinderSerialisesPutsAndGetsCorrectly() throws Exception {

        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

        BinderStore store = BinderStore.instance(createMewbaseOptions());
        Binder binder = store.open(testBinderName);
        final String DOC_ID = "ID1234567";
        final String FIELD_KEY = "K";
        BsonObject doc = createObject();
        final Integer END_VAL = 128;
        IntStream.rangeClosed(0,END_VAL).forEach( i -> binder.put(DOC_ID, doc.put(FIELD_KEY,i)));
        assertEquals(END_VAL,binder.get("ID1234567").join().getInteger(FIELD_KEY));
    }


    @Test
    public void testFindNoEntry() throws Exception {
        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

        BinderStore store = BinderStore.instance(createMewbaseOptions());
        Binder binder = store.open(testBinderName);
        assertNull(binder.get("id1234").get());

    }


    @Test
    public void testDelete() throws Exception {

        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

        BinderStore store = BinderStore.instance(createMewbaseOptions());
        Binder binder = store.open(testBinderName);

        BsonObject docPut = createObject();
        assertNull(binder.put("id1234", docPut).get());
        BsonObject docGet = binder.get("id1234").get();
        assertEquals(docPut, docGet);
        assertTrue(binder.delete("id1234").get());
        docGet = binder.get("id1234").get();
        assertNull(docGet);
    }


    @Test
    public void testGetAll() throws Exception {

        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

        BinderStore store = BinderStore.instance(createMewbaseOptions());;
        Binder binder = store.open(testBinderName);

        final int MANY_DOCS = 64;
        final String DOC_ID_KEY = "id";

        final IntStream range = IntStream.rangeClosed(1, MANY_DOCS);

        range.forEach(i -> {
            final BsonObject docPut = createObject();
            binder.put(String.valueOf(i), docPut.put(DOC_ID_KEY, i));
        });

        Consumer<KeyVal<String,BsonObject>> checker = (entry) -> {
            try {
                assertNotNull(entry);
                String id = entry.getKey();
                BsonObject doc = entry.getValue();
                assertNotNull(id);
                assertNotNull(doc);
                assertEquals((int)doc.getInteger(DOC_ID_KEY),Integer.parseInt(id));
            } catch (Exception e) {
                fail(e.getMessage());
            }
        };

        // get all
        Stream<KeyVal<String, BsonObject>> docs = binder.getDocuments();
        docs.forEach(checker);
    }


    @Test
    public void testGetWithFilter() throws Exception {

        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();

        BinderStore store = BinderStore.instance(createMewbaseOptions());
        Binder binder = store.open(testBinderName);

        final int ALL_DOCS = 64;
        final String DOC_ID_KEY = "id";

        final IntStream range = IntStream.rangeClosed(1, ALL_DOCS);

        range.forEach(i -> {
            final BsonObject docPut = createObject();
            binder.put(String.valueOf(i), docPut.put(DOC_ID_KEY, i)).join();
        });

        // get with filter
        final int HALF_THE_DOCS = ALL_DOCS / 2;
        Function<KeyVal<String,BsonObject>, BsonObject> checker = (entry) -> {
                assertNotNull(entry);
                String id = entry.getKey();
                BsonObject doc = entry.getValue();
                assertNotNull(id);
                assertNotNull(doc);
                assertEquals((int)doc.getInteger(DOC_ID_KEY),Integer.parseInt(id));
                assertTrue(  doc.getInteger(DOC_ID_KEY) <= HALF_THE_DOCS);
                return doc;
        };

        Predicate<BsonObject> filter = doc -> doc.getInteger(DOC_ID_KEY) <= HALF_THE_DOCS;
        Stream<KeyVal<String, BsonObject>> docs = binder.getDocuments(new HashSet(),filter);

        assertEquals(HALF_THE_DOCS, docs.map(checker).collect(toSet()).size());

    }

    @Test
    public void testGetWithIdSet() throws Exception {

        BinderStore store = BinderStore.instance(createMewbaseOptions());
        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();
        Binder binder = store.open(testBinderName);

        final int ALL_DOCS = 64;
        final String DOC_ID_KEY = "id";

        final IntStream range = IntStream.rangeClosed(1, ALL_DOCS);

        range.forEach(i -> {
            final BsonObject docPut = createObject();
            binder.put(String.valueOf(i), docPut.put(DOC_ID_KEY, i));
        });

        // get with id set
        final int HALF_THE_DOCS = ALL_DOCS / 2;
        final Set<String> idSet = IntStream.rangeClosed(1, HALF_THE_DOCS).mapToObj( String::valueOf ).collect(Collectors.toSet());


        Function<KeyVal<String,BsonObject>, BsonObject> checker = (entry) -> {
            assertNotNull(entry);
            String id = entry.getKey();
            BsonObject doc = entry.getValue();
            assertNotNull(id);
            assertNotNull(doc);
            assertEquals((int)doc.getInteger(DOC_ID_KEY),Integer.parseInt(id));
            assertTrue(  doc.getInteger(DOC_ID_KEY) <= HALF_THE_DOCS);
            return doc;
        };

        Predicate<BsonObject> matchAll = doc -> true;
        Stream<KeyVal<String, BsonObject>> docs = binder.getDocuments(idSet,matchAll);

        assertEquals(docs.map(checker).collect(toSet()).size(), HALF_THE_DOCS);
    }


    protected BsonObject createObject() {
        BsonObject obj = new BsonObject();
        obj.put("foo", "bar").put("quux", 1234).put("wib", true);
        return obj;
    }


    // @Test
    public void testPerformance() throws Exception {

        final String testBinderName = new Object(){}.getClass().getEnclosingMethod().getName();
        final BinderStore store = BinderStore.instance(createMewbaseOptions());
        // final BinderStore store = new LmdbBinderStore();

        final Binder binder = store.open(testBinderName);

        final BsonObject docToWrite = new BsonObject().put("Thing1","Bad").put("Thing2","Worse");

        int iterations = 10000;

        {
            final String test = "Sync Writes";
            final long start = System.currentTimeMillis();
            IntStream.rangeClosed(1, iterations).forEach(i ->
                binder.put(String.valueOf(i), docToWrite).join()
            );
            final long end = System.currentTimeMillis();
            System.out.println(test + " :" + ((float)iterations * 1000f) / (float)(end - start));
        }

        {
            final String test = "Sync Reads";
            final long start = System.currentTimeMillis();
            IntStream.rangeClosed(1, iterations).forEach(i ->
                binder.get(String.valueOf(i)).join()
            );
            final long end = System.currentTimeMillis();
            System.out.println(test + " :" + ((float)iterations * 1000f) / (float)(end - start));
        }


        iterations = 100000;
        {
            final String test = "Async Writes";
            final long start = System.currentTimeMillis();
            IntStream.rangeClosed(1, iterations).forEach(i ->
                    binder.put(String.valueOf(i), docToWrite)
            );
            final long end = System.currentTimeMillis();
            System.out.println(test + " :" + ((float)iterations * 1000f) / (float)(end - start));
        }

        {
            final String test = "Async Reads";
            final long start = System.currentTimeMillis();
            IntStream.rangeClosed(1, iterations).forEach(i ->
                    binder.get(String.valueOf(i))
            );
            final long end = System.currentTimeMillis();
            System.out.println(test + " :" + ((float)iterations * 1000f) / (float)(end - start));
        }


    }


}
