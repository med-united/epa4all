package de.servicehealth.epa4all.server.ws;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import org.eclipse.yasson.internal.JsonBindingBuilder;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonEncoder<T> implements Encoder.Text<T> {

    private static final Logger log = Logger.getLogger(JsonEncoder.class.getName());

    private JsonbBuilder jsonbBuilder;

    @Override
    public void init(EndpointConfig config) {
        jsonbBuilder = new JsonBindingBuilder();
    }

    @Override
    public String encode(T object) {
        try (Jsonb build = jsonbBuilder.build()) {
            return build.toJson(object);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while serializing CashierPayload", e);
            return e.getMessage();
        }
    }
}