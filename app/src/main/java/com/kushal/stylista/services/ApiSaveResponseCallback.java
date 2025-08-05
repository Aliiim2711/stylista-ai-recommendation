package com.kushal.stylista.services;

public interface ApiSaveResponseCallback {
    void onSuccess();
    void onFailure(Exception exception);
}