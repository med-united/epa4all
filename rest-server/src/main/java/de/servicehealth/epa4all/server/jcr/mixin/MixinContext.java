package de.servicehealth.epa4all.server.jcr.mixin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MixinContext {

    private List<String> staleMixins;
}