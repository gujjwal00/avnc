/*
 * Copyright (c) 2026  Vortax contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

#ifndef AVNC_QEMUAUDIO_H
#define AVNC_QEMUAUDIO_H

/**
 * Client side of QEMU's VNC audio extension (see ui/vnc.c in QEMU, "qemu audio" in the RFB
 * extension list). It is implemented as a LibVNCClient protocol extension, so LibVNCClient
 * itself does not need to be patched. This file has no JNI or Android dependency; the glue
 * lives in native-vnc.cpp.
 *
 *   client -> server  SetEncodings pseudo-encoding -259 (offered automatically)
 *   server -> client  FramebufferUpdate rect with encoding -259 and no data: "audio is supported"
 *
 *   client -> server  255, 1, u16 operation
 *                       0 enable   1 disable
 *                       2 set format, then u8 sample format (3 = signed 16 bit), u8 channels, u32 frequency
 *   server -> client  255, 1, u16 operation
 *                       0 end   1 begin
 *                       2 data, then u32 length and that many bytes of PCM
 *
 * All integers are big endian. Servers send nothing until the client has set a format and enabled audio.
 */

#include <rfb/rfbclient.h>
#include <stdint.h>
#include <stdlib.h>

#define QEMU_AUDIO_ENCODING ((uint32_t) 0xFFFFFEFDu)   /* -259 */
#define QEMU_MSG_TYPE 255
#define QEMU_SUBMSG_AUDIO 1
#define QEMU_AUDIO_FORMAT_S16 3
#define QEMU_AUDIO_MAX_CHUNK (4u * 1024u * 1024u)       /* refuse absurd lengths from a broken server */

enum QemuAudioServerOp { QEMU_AUDIO_END = 0, QEMU_AUDIO_BEGIN = 1, QEMU_AUDIO_DATA = 2 };
enum QemuAudioClientOp { QEMU_AUDIO_ENABLE = 0, QEMU_AUDIO_DISABLE = 1, QEMU_AUDIO_SET_FORMAT = 2 };

struct QemuAudioHooks {
    /** The server acknowledged the extension: it can stream sound. May be called on every update that repeats the ack. */
    void (*available)(rfbClient *client);

    /** A chunk of PCM (in the format chosen with qemuAudioStart). Called on the message-loop thread. */
    void (*data)(rfbClient *client, const uint8_t *pcm, uint32_t length);
};

static QemuAudioHooks qemuAudioHooks = {nullptr, nullptr};
static const int qemuAudioTag = 2;                       // address used as the client-data key

static bool qemuAudioIsAvailable(rfbClient *client) {
    return rfbClientGetClientData(client, (void *) &qemuAudioTag) != nullptr;
}

static rfbBool qemuAudioHandleEncoding(rfbClient *client, rfbFramebufferUpdateRectHeader *rect) {
    if ((uint32_t) rect->encoding != QEMU_AUDIO_ENCODING)
        return FALSE;

    // The rectangle carries no payload. Remember the ack, and tell the app only the first time.
    if (!qemuAudioIsAvailable(client)) {
        rfbClientSetClientData(client, (void *) &qemuAudioTag, (void *) &qemuAudioTag);
        if (qemuAudioHooks.available)
            qemuAudioHooks.available(client);
    }
    return TRUE;
}

static rfbBool qemuAudioHandleMessage(rfbClient *client, rfbServerToClientMsg *msg) {
    if (msg->type != QEMU_MSG_TYPE)
        return FALSE;

    uint8_t head[3];                                     // sub-message, then the big endian operation
    if (!ReadFromRFBServer(client, (char *) head, sizeof(head)))
        return FALSE;
    if (head[0] != QEMU_SUBMSG_AUDIO)
        return FALSE;                                    // nothing else is defined for server -> client

    switch (((unsigned) head[1] << 8) | head[2]) {
        case QEMU_AUDIO_END:
        case QEMU_AUDIO_BEGIN:
            return TRUE;

        case QEMU_AUDIO_DATA: {
            uint8_t lenBytes[4];
            if (!ReadFromRFBServer(client, (char *) lenBytes, sizeof(lenBytes)))
                return FALSE;
            uint32_t length = ((uint32_t) lenBytes[0] << 24) | ((uint32_t) lenBytes[1] << 16) |
                              ((uint32_t) lenBytes[2] << 8) | lenBytes[3];
            if (length > QEMU_AUDIO_MAX_CHUNK) {
                rfbClientErr("QEMU audio: refusing a chunk of %u bytes\n", length);
                return FALSE;
            }
            if (length == 0)
                return TRUE;

            auto *pcm = (uint8_t *) malloc(length);
            if (!pcm)
                return FALSE;
            if (!ReadFromRFBServer(client, (char *) pcm, length)) {   // must always be read, even if nobody listens
                free(pcm);
                return FALSE;
            }
            if (qemuAudioHooks.data)
                qemuAudioHooks.data(client, pcm, length);
            free(pcm);
            return TRUE;
        }

        default:
            rfbClientErr("QEMU audio: unknown operation\n");
            return FALSE;
    }
}

static int qemuAudioEncodings[] = {(int) QEMU_AUDIO_ENCODING, 0};
static rfbClientProtocolExtension qemuAudioExtension = {
        qemuAudioEncodings, qemuAudioHandleEncoding, qemuAudioHandleMessage, nullptr};

/** Register the extension. Safe to call more than once. Must happen before the first connection. */
static void qemuAudioInstall(QemuAudioHooks hooks) {
    static bool installed = false;
    qemuAudioHooks = hooks;
    if (!installed) {
        rfbClientRegisterExtension(&qemuAudioExtension);
        installed = true;
    }
}

/** Choose the stream format and turn the stream on. Returns FALSE if the server never acknowledged the extension. */
static rfbBool qemuAudioStart(rfbClient *client, uint32_t frequency, uint8_t channels) {
    if (!qemuAudioIsAvailable(client))
        return FALSE;

    uint8_t msg[10 + 4] = {QEMU_MSG_TYPE, QEMU_SUBMSG_AUDIO, 0, QEMU_AUDIO_SET_FORMAT,
                           QEMU_AUDIO_FORMAT_S16, channels,
                           (uint8_t) (frequency >> 24), (uint8_t) (frequency >> 16), (uint8_t) (frequency >> 8), (uint8_t) frequency,
                           QEMU_MSG_TYPE, QEMU_SUBMSG_AUDIO, 0, QEMU_AUDIO_ENABLE};
    return WriteToRFBServer(client, (char *) msg, sizeof(msg));   // one write: format first, then enable
}

static rfbBool qemuAudioStop(rfbClient *client) {
    if (!qemuAudioIsAvailable(client))
        return FALSE;
    uint8_t msg[4] = {QEMU_MSG_TYPE, QEMU_SUBMSG_AUDIO, 0, QEMU_AUDIO_DISABLE};
    return WriteToRFBServer(client, (char *) msg, sizeof(msg));
}

#endif //AVNC_QEMUAUDIO_H
