package com.rf.taskmodule.utils;

public interface NetworkStateReceiverListener {
    default void networkAvailable() {

    }

    default void networkUnavailable() {

    }
}
