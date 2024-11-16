package de.servicehealth.epa4all.server.xdsdocument;

import de.servicehealth.epa4all.xds.ProvideAndRegisterSingleDocumentTypeBuilder;
import de.servicehealth.epa4all.xds.structure.StructureDefinitionService;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class RegularXDSDocumentService extends XDSDocumentService {

    public RegularXDSDocumentService() {
    }

    @Inject
    public RegularXDSDocumentService(
        StructureDefinitionService structureDefinitionService,
        ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder
    ) {
        super(structureDefinitionService, provideAndRegisterDocumentBuilder);
    }
}
