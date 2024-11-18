package de.servicehealth.vau;

import de.gematik.vau.lib.VauClientStateMachine;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class VauClient {
    
    public static final String VAU_CID = "VAU-CID";

    public static final String VAU_DEBUG_SK1_C2S = "vau-debug-s_k1_c2s";
    public static final String VAU_DEBUG_SK1_S2C = "vau-debug-s_k1_s2c";
    public static final String VAU_DEBUG_SK2_C2S_INFO = "vau-debug-s_k2_c2s_keyconfirmation";
    public static final String VAU_DEBUG_SK2_S2C_INFO = "vau-debug-s_k2_s2c_keyconfirmation";

    public static final String VAU_NON_PU_TRACING = "VAU-nonPU-Tracing";

    public static final String X_INSURANT_ID = "x-insurantid";
    public static final String X_USER_AGENT = "x-useragent";
    public static final String VAU_NP = "VAU-NP";

    @Getter
    private final VauClientStateMachine vauStateMachine;

    @Setter
    @Getter
    private VauInfo vauInfo;

    @Getter
    private final AtomicLong acquiredAt;

    private final ReentrantLock lock;

    public VauClient(VauClientStateMachine vauStateMachine) {
        this.vauStateMachine = vauStateMachine;

        lock = new ReentrantLock();
        acquiredAt = new AtomicLong(0L);
    }

    public boolean acquire() {
        try {
            return lock.tryLock();
        } finally {
            acquiredAt.set(System.currentTimeMillis());
        }
    }

    public boolean busy() {
        return lock.isLocked();
    }

    public void release() {
        try {
            lock.unlock();
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
            lock.unlock();
        } finally {
            vauInfo = null;
            acquiredAt.set(0L);
        }
    }
}