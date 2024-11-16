package de.servicehealth.epa4all.server.xdsdocument;

import de.servicehealth.epa4all.xds.ProvideAndRegisterSingleDocumentTypeBuilder;
import de.servicehealth.epa4all.xds.structure.StructureDefinitionService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class HttpXDSDocumentService extends XDSDocumentService {

    public HttpXDSDocumentService() {
    }

    @Inject
    public HttpXDSDocumentService(
        StructureDefinitionService structureDefinitionService,
        ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder
    ) {
        super(structureDefinitionService, provideAndRegisterDocumentBuilder);
    }
}
