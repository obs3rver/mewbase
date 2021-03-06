package io.mewbase.binders;


import io.mewbase.binders.impl.filestore.FileBinderStore;
import io.mewbase.binders.impl.postgres.PostgresBinderStore;
import io.mewbase.eventsource.EventSource;
import io.mewbase.projection.ProjectionManager;
import io.mewbase.projection.impl.ProjectionManagerImpl;
import io.mewbase.server.MewbaseOptions;

import java.util.Optional;
import java.util.stream.Stream;


/**
 * Created by Nige on 14/09/17.
 */
public interface BinderStore {


    static BinderStore instance(MewbaseOptions opts) {
        return new FileBinderStore(opts);
    }

    static BinderStore instance() {
        return new FileBinderStore();
    }


    /**
     * Open a new binder of the given name.
     *
     * If the binder doesnt already exist the binder wil be created.
     *
     * @param name of the Binder to open or create and open
     * @return succesfull  if Binder is created otherwise complet
     */
    Binder open(String name);

    /**
     * Get a Binder with the given name
     *
     * @param  name of the document within the binder
     * @return a CompletableFuture of the binder or a failed future if the binder doesnt exist.
     */
    Optional<Binder> get(String name);

    /**
     * Return a stream of the Binders so that maps / filters can be applied.
     *
     * @return a stream of all of the current binders
     */
    Stream<Binder> binders();

    /**
     * Return a stream of all of the names of the binders
     *
     * @return a stream of all of the current binder names.
     */
    Stream<String> binderNames();

    /**
     * Delete a binder from the store
     *
     * @param  name of  binder
     * @return a CompleteableFuture with a Boolean set to true if successful
     */
    @Deprecated
    Boolean delete(String name);



}
