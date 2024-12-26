package de.servicehealth.vau;

import de.gematik.vau.lib.VauClientStateMachine;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class VauClient {

    public static final String VAU_CID = "VAU-CID";

    public static final String VAU_DEBUG_SK1_C2S = "vau-debug-s_k1_c2s";
    public static final String VAU_DEBUG_SK1_S2C = "vau-debug-s_k1_s2c";
    public static final String VAU_DEBUG_SK2_C2S_INFO = "vau-debug-s_k2_c2s_keyconfirmation";
    public static final String VAU_DEBUG_SK2_S2C_INFO = "vau-debug-s_k2_s2c_keyconfirmation";

    public static final String VAU_NON_PU_TRACING = "VAU-nonPU-Tracing";
    public static final String VAU_ERROR = "VAU_ERROR";

    public static final String X_INSURANT_ID = "x-insurantid";
    public static final String X_USER_AGENT = "x-useragent";
    public static final String X_KONNEKTOR = "x-konnektor";
    public static final String X_BACKEND = "x-backend";
    public static final String VAU_NP = "VAU-NP";
    public static final String CLIENT_ID = "ClientID";

    private static final int PERMITS = 1;

    @Getter
    private final VauClientStateMachine vauStateMachine;

    @Getter
    private final boolean mock;

    @Setter
    @Getter
    private VauInfo vauInfo;

    private final AtomicLong acquiredAt;
    private final AtomicBoolean broken;
    private final int readTimeoutMs;

    private final Semaphore semaphore;

    public VauClient(VauClientStateMachine vauStateMachine, boolean mock, int readTimeoutMs) {
        this.vauStateMachine = vauStateMachine;
        this.readTimeoutMs = readTimeoutMs;
        this.mock = mock;

        semaphore = new Semaphore(PERMITS);
        broken = new AtomicBoolean(false);
        acquiredAt = new AtomicLong(0L);
    }

    public boolean acquire() {
        boolean acquired = semaphore.tryAcquire();
        if (acquired) {
            broken.set(false);
            acquiredAt.set(System.currentTimeMillis());
        }
        return acquired;
    }

    public boolean busy() {
        long acquired = acquiredAt.get();
        int availablePermits = semaphore.availablePermits();
        return availablePermits == 0 && 0 < acquired && acquired >= System.currentTimeMillis() - readTimeoutMs;
    }

    public boolean hangs() {
        long acquired = acquiredAt.get();
        int availablePermits = semaphore.availablePermits();
        return availablePermits == 0 && 0 < acquired && acquired < System.currentTimeMillis() - readTimeoutMs;
    }

    public boolean broken() {
        return broken.get();
    }

    public void release() {
        try {
            semaphore.release();
        } finally {
            acquiredAt.set(0L);
        }
    }

    public byte[] decryptVauMessage(byte[] bytes) {
        try {
            return vauStateMachine.decryptVauMessage(bytes);
        } finally {
            release();
        }
    }

    public void forceRelease() {
        try {
            release();
        } finally {
            broken.set(true);
            vauInfo = null;
        }
    }
}