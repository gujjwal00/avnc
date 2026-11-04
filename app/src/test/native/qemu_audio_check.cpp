/*
 * Copyright (c) 2026  Vortax contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

// Runs the real QemuAudio.h and the real LibVNCClient against a fake QEMU-style server (see test_qemu_audio.py).
// Prints one line per event so the test can compare them.
#include <cstdio>
#include <cstring>
#include "../../main/cpp/QemuAudio.h"

static int chunks = 0;
static bool availableSeen = false;

static void onAvailable(rfbClient *) {
    printf("available\n");
    availableSeen = true;
}

static void onData(rfbClient *, const uint8_t *pcm, uint32_t length) {
    printf("data %u", length);
    for (uint32_t i = 0; i < length && i < 8; i++) printf(" %02x", pcm[i]);
    printf("\n");
    fflush(stdout);
    chunks++;
}

int main(int argc, char **argv) {
    if (argc < 3) return 2;
    qemuAudioInstall({onAvailable, onData});
    qemuAudioInstall({onAvailable, onData});                       // a second call must be harmless

    rfbClient *client = rfbGetClient(8, 3, 4);
    client->serverHost = strdup(argv[1]);
    client->serverPort = atoi(argv[2]);
    client->canHandleNewFBSize = TRUE;
    if (!rfbClientConnect(client) || !rfbClientInitialise(client)) { printf("connect failed\n"); return 1; }

    printf("start before ack: %d\n", qemuAudioStart(client, 22050, 1) ? 1 : 0);     // must refuse
    bool started = false;
    for (int i = 0; i < 200; i++) {
        int w = WaitForMessage(client, 200000);
        if (w < 0) break;
        if (w == 0) continue;
        if (!HandleRFBServerMessage(client)) { printf("message loop ended\n"); break; }
        if (availableSeen && !started) {
            started = true;
            printf("start after ack: %d\n", qemuAudioStart(client, 22050, 1) ? 1 : 0);
            fflush(stdout);
        }
        if (chunks >= 3) {
            // the fake server streams 3 non-empty chunks (plus an empty one that must be skipped), then an 'end' marker
            if (WaitForMessage(client, 1000000) > 0) HandleRFBServerMessage(client);
            printf("stop: %d\n", qemuAudioStop(client) ? 1 : 0);
            fflush(stdout);
            break;
        }
    }
    rfbClientCleanup(client);
    return 0;
}
