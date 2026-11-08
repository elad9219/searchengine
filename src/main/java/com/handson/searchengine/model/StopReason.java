package com.handson.searchengine.model;

public enum StopReason {
    maxDistance,
    maxUrls,
    timeout,
    userInitiated,
    completed,
    error
}