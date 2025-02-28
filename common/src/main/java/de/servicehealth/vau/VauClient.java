package de.servicehealth.vau;

import de.gematik.vau.lib.VauClientStateMachine;
import de.gematik.vau.lib.data.KdfKey2;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class VauClient {
    
    private static final Logger log = LoggerFactory.getLogger(VauClient.class.getName());

    public static final String VAU_CLIENT_UUID = "VAU_CLIENT_UUID";
    public static final String VAU_CID = "VAU-CID";

    public static final String VAU_DEBUG_SK1_C2S = "vau-debug-s_k1_c2s";
    public static final String VAU_DEBUG_SK1_S2C = "vau-debug-s_k1_s2c";
    public static final String VAU_DEBUG_SK2_C2S_INFO = "vau-debug-s_k2_c2s_keyconfirmation";
    public static final String VAU_DEBUG_SK2_S2C_INFO = "vau-debug-s_k2_s2c_keyconfirmation";

    public static final String VAU_NON_PU_TRACING = "VAU-nonPU-Tracing";
    public static final String VAU_ERROR = "VAU_ERROR";
    public static final String VAU_NO_SESSION = "VAU_NO_SESSION";
    public static final String VAU_STATUS = "VAU_STATUS";

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
    public static final String VAU_CLIENT = "vauClient";

    public static final String CLIENT_ID = "ClientID";

    private static final int PERMITS = 1;

    private VauClientStateMachine vauStateMachine;

    @Getter
    private final boolean mock;

    @Getter
    @Setter
    private volatile VauInfo vauInfo;

    @Getter
    @Setter
    private String vauNp;

    @Getter
    private final String uuid;

    @Getter
    private final String konnektorWorkplace;
    private final AtomicLong acquiredAt;
    private final int readTimeoutMs;
    private final Semaphore semaphore;
    private final boolean pu;

    public VauClient(boolean pu, boolean mock, int readTimeoutMs, String konnektorWorkplace) {
        this.konnektorWorkplace = konnektorWorkplace;
        this.readTimeoutMs = readTimeoutMs;
        this.mock = mock;
        this.pu = pu;

        uuid = UUID.randomUUID().toString();
        vauStateMachine = new VauClientStateMachine(pu);
        semaphore = new Semaphore(PERMITS);
        acquiredAt = new AtomicLong(0L);
    }

    public boolean acquire() {
        boolean acquired = semaphore.tryAcquire();
        if (acquired) {
            acquiredAt.set(System.currentTimeMillis());
            String msg = "[VauClient %s] ACQUIRED acquiredAt=%d, permits=%d";
            log.debug(String.format(msg, uuid, acquiredAt.get(), semaphore.availablePermits()));
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

    private void release() {
        if (acquiredAt.get() > 0) {
            try {
                semaphore.release();
            } finally {
                acquiredAt.set(0L);
            }
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
        byte[] decryptedBytes = vauStateMachine.decryptVauMessage(bytes);
        String msg = "[VauClient %s] RELEASE acquiredAt=%d permits=%d";
        log.debug(String.format(msg, uuid, acquiredAt.get(), semaphore.availablePermits()));
        release();
        return decryptedBytes;
    }

    public String forceRelease(Long hangsTime) {
        long acquiredAt = this.acquiredAt.get();
        long delta = System.currentTimeMillis() - acquiredAt;
        if (hangsTime == null || hangsTime <= delta) {
            try {
                String msg = "[VauClient %s] FORCE RELEASE hangsTime=%d delta=%d acquiredAt=%d";
                log.debug(String.format(msg, uuid, hangsTime, delta, acquiredAt));

                String vauCid = vauInfo == null ? "not-defined-yet" : vauInfo.getVauCid();
                setVauInfo(null);
                setVauNp(null);
                vauStateMachine = new VauClientStateMachine(pu);
                return vauCid;
            } finally {
                release();
            }
        } else {
            return null;
        }
    }
}