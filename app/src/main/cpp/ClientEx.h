/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

#ifndef AVNC_CLIENTEX_H
#define AVNC_CLIENTEX_H

#include <jni.h>
#include "Cursor.h"

/**
 * We attach some additional data to every rfbClient.
 * ClientEx is used as wrapper for this data.
 */
struct ClientEx {
    // Reference to managed `VncClient`
    jobject managedClient;

    // Protects concurrent access to framebuffer & cursor data
    MUTEX(mutex);

    // Cursor data used for client-side cursor rendering
    Cursor *cursor;
};

const int ClientExTag = 1;

ClientEx *getClientExtension(rfbClient *client) {
    return (ClientEx *) rfbClientGetClientData(client, (void *) &ClientExTag);
}

void setClientExtension(rfbClient *client, ClientEx *ex) {
    rfbClientSetClientData(client, (void *) &ClientExTag, ex);
}

/**
 * Returns reference to managed `VncClient` associated with given rfbClient.
 */
jobject getManagedClient(rfbClient *client) {
    return getClientExtension(client)->managedClient;
}

/**
 * Associate given rfbClient & managed `VncClient`.
 */
void setManagedClient(rfbClient *client, jobject managedClient) {
    getClientExtension(client)->managedClient = managedClient;
}

/**
 * Create new ClientEx and assign it to given client.
 */
ClientEx *assignClientExtension(rfbClient *client) {
    auto ex = (ClientEx *) malloc(sizeof(ClientEx));
    if (ex) {
        INIT_MUTEX(ex->mutex);
        ex->cursor = nullptr;
        setClientExtension(client, ex);
    }
    return ex;
}

/**
 * Free all resources related to client extension.
 */
void freeClientExtension(rfbClient *client) {
    auto ex = getClientExtension(client);
    if (ex) {
        TINI_MUTEX(ex->mutex);
        freeCursor(ex->cursor);
        free(ex);
        setClientExtension(client, nullptr);
    }
}

#endif //AVNC_CLIENTEX_H
