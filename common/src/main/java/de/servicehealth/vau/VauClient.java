package de.servicehealth.vau;

import de.gematik.vau.lib.VauClientStateMachine;
import de.gematik.vau.lib.data.KdfKey2;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class VauClient {
    
    private static final Logger log = Logger.getLogger(VauClient.class.getName());

    public static final String VAU_CID = "VAU-CID";

    public static final String VAU_DEBUG_SK1_C2S = "vau-debug-s_k1_c2s";
    public static final String VAU_DEBUG_SK1_S2C = "vau-debug-s_k1_s2c";
    public static final String VAU_DEBUG_SK2_C2S_INFO = "vau-debug-s_k2_c2s_keyconfirmation";
    public static final String VAU_DEBUG_SK2_S2C_INFO = "vau-debug-s_k2_s2c_keyconfirmation";

    public static final String VAU_NON_PU_TRACING = "VAU-nonPU-Tracing";
    public static final String VAU_ERROR = "VAU_ERROR";
    public static final String VAU_NO_SESSION = "VAU_NO_SESSION";

    public static final String X_INSURANT_ID = "x-insurantid";
    public static final String X_USER_AGENT = "x-useragent";
    public static final String X_KONNEKTOR = "x-konnektor";
    public static final String X_WORKPLACE = "x-workplace";
    public static final String X_SUBJECT = "subject";
    public static final String X_BACKEND = "x-backend";
    public static final String VAU_NP = "VAU-NP";
    public static final String KVNR = "kvnr";
    public static final String TELEMATIK_ID = "telematikId";
    public static final String TASK_ID = "taskId";

    public static final String CLIENT_ID = "ClientID";

    private static final int PERMITS = 1;

    private final VauClientStateMachine vauStateMachine;

    @Getter
    private final boolean mock;

    @Getter
    @Setter
    private VauInfo vauInfo;

    private final AtomicLong acquiredAt;
    private final AtomicBoolean broken;
    private final int readTimeoutMs;
    private final Semaphore semaphore;


    public VauClient(boolean pu, boolean mock, int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
        this.mock = mock;

        vauStateMachine = new VauClientStateMachine(pu);

        semaphore = new Semaphore(PERMITS);
        broken = new AtomicBoolean(false);
        acquiredAt = new AtomicLong(0L);
    }

    public boolean acquire() {
        boolean acquired = semaphore.tryAcquire();
        if (acquired) {
            broken.set(false);
            acquiredAt.set(System.currentTimeMillis());
            String threadName = Thread.currentThread().getName();
            log.fine(String.format("[VauClient %d] ACQUIRED by %s acquiredAt=%d", hashCode(), threadName, acquiredAt.get()));
        }
        return acquired;
    }

    public boolean busy() {
        long acquired = acquiredAt.get();
        int availablePermits = semaphore.availablePermits();
        return availablePermits == 0 && 0 < acquired && acquired >= System.currentTimeMillis() - readTimeoutMs;
    }

    public Long hangs() {
        int availablePermits = semaphore.availablePermits();
        boolean hangs = availablePermits == 0 && 0 < acquiredAt.get() && acquiredAt.get() < System.currentTimeMillis() - readTimeoutMs;
        long hangsTime = System.currentTimeMillis() - acquiredAt.get();
        return hangs ? hangsTime : null;
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

    public byte[] generateMessage1() {
        return vauStateMachine.generateMessage1();
    }

    public KdfKey2 getClientKey2() {
        return vauStateMachine.getClientKey2();
    }

    public byte[] receiveMessage2(byte[] message2) {
        return vauStateMachine.receiveMessage2(message2);
    }

    public void receiveMessage4(byte[] message4) {
        vauStateMachine.receiveMessage4(message4);
    }

    public byte[] encryptVauMessage(byte[] bytes) {
        return vauStateMachine.encryptVauMessage(bytes);
    }

    public byte[] decryptVauMessage(byte[] bytes) {
        try {
            return vauStateMachine.decryptVauMessage(bytes);
        } finally {
            String threadName = Thread.currentThread().getName();
            log.fine(String.format("[VauClient %d] RELEASE by %s acquiredAt=%d", hashCode(), threadName, acquiredAt.get()));
            release();
        }
    }

    String forceRelease(Long hangsTime) {
        long acquiredAt = this.acquiredAt.get();
        long delta = System.currentTimeMillis() - acquiredAt;
        if (hangsTime == null || hangsTime <= delta) {
            try {
                String threadName = Thread.currentThread().getName();
                log.fine(
                    String.format(
                        "[VauClient %d] FORCE RELEASE by %s hangsTime=%d delta=%d acquiredAt=%d",
                        hashCode(), threadName, hangsTime, delta, acquiredAt
                    )
                );

                String vauCid = vauInfo == null ? "not-defined-yet" : vauInfo.getVauCid();
                broken.set(true);
                setVauInfo(null);
                return vauCid;
            } finally {
                release();
            }
        } else {
            return null;
        }
    }
}