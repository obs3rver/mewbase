package com.tesco.mubase.client;

import com.tesco.mubase.common.DocQuerier;
import com.tesco.mubase.common.SubDescriptor;

import java.util.concurrent.CompletableFuture;

/**
 * Created by tim on 22/09/16.
 */
public interface Connection extends DocQuerier {

    Producer createProducer(String streamName);

    // Subscription

    CompletableFuture<Subscription> subscribe(SubDescriptor descriptor);

    CompletableFuture<Void> close();

    // Also provide sync API

    // TODO
}