package de.servicehealth.epa4all.server.presription;

import ca.uhn.fhir.context.FhirContext;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.kim.KimLdapService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;

import java.util.Date;
import java.util.UUID;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * Builds a UC1-1-Prescription-Request-To-Prescriber Bundle per gematik spec-E-Rezept-ServiceRequest.
 *
 * @see <a href="https://github.com/gematik/spec-E-Rezept-ServiceRequest/blob/master/fsh-generated/resources/Bundle-UC1-1-Prescription-Request-To-Prescriber.json">UC1-1 Bundle reference</a>
 */
@ApplicationScoped
public class PrescriptionBundleService {

    static final String PROFILE_MESSAGE_CONTAINER = "https://gematik.de/fhir/erp-servicerequest/StructureDefinition/erp-service-request-message-container";
    static final String PROFILE_REQUEST_HEADER = "https://gematik.de/fhir/erp-servicerequest/StructureDefinition/erp-service-request-request-header";
    static final String PROFILE_ORGANIZATION = "https://gematik.de/fhir/erp-servicerequest/StructureDefinition/erp-service-request-organization";
    static final String PROFILE_PRESCRIPTION_REQUEST = "https://gematik.de/fhir/erp-servicerequest/StructureDefinition/erp-service-request-prescription-request";
    static final String PROFILE_PATIENT = "https://gematik.de/fhir/erp-servicerequest/StructureDefinition/erp-service-request-patient";
    static final String PROFILE_MEDICATION_REQUEST = "https://gematik.de/fhir/erp-servicerequest/StructureDefinition/erp-service-request-medication-request";
    static final String PROFILE_KBV_MEDICATION_PZN = "https://fhir.kbv.de/StructureDefinition/KBV_PR_ERP_Medication_PZN|1.1.0";

    static final String CS_SERVICE_IDENTIFIER = "https://gematik.de/fhir/atf/CodeSystem/service-identifier-cs";
    static final String CS_SERVICE_REQUEST_TYPE = "https://gematik.de/fhir/erp-servicerequest/CodeSystem/service-request-type-cs";
    static final String CS_TELEMATIK_ID = "https://gematik.de/fhir/sid/telematik-id";
    static final String CS_KVID = "http://fhir.de/sid/gkv/kvid-10";
    static final String CS_PZN = "http://fhir.de/CodeSystem/ifa/pzn";
    static final String CS_ORG_PROFESSION_OID = "https://gematik.de/fhir/directory/CodeSystem/OrganizationProfessionOID";
    static final String CS_DARREICHUNGSFORM = "https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_KBV_DARREICHUNGSFORM";
    static final String NS_REQUEST_ID = "https://gematik.de/fhir/erp-servicerequest/sid/RequestIdentifier";
    static final String NS_PROCEDURE_ID = "https://gematik.de/fhir/erp-servicerequest/sid/ProcedureIdentifier";
    static final String EVENT_CODE = "eRezept_Rezeptanforderung;Rezeptanfrage";

    private static final FhirContext FHIR_CTX = FhirContext.forR4();

    @Inject
    KimLdapService kimLdapService;

    /**
     * Builds a UC1-1-Prescription-Request Bundle from a raw ePA Bundle JSON.
     */
    public KimContext prepareKimContextWithBundle(
        UserRuntimeConfig userRuntimeConfig,
        Bundle epaBundle
    ) throws PrescriptionSendException {
        MedicationRequest epaMedRequest = findSingleResource(epaBundle, MedicationRequest.class);
        Medication epaMedication = resolveReference(epaBundle, epaMedRequest.getMedicationReference(), Medication.class);
        Patient epaPatient = findPatient(epaBundle, epaMedRequest);
        Practitioner epaPractitioner = findPractitioner(epaBundle, epaMedRequest);
        String practitionerName = extractPractitionerName(epaPractitioner);
        String kimAddress = kimLdapService.searchKimAddress(userRuntimeConfig, practitionerName);

        String senderTelematikId = "";
        String senderOrgName = practitionerName;
        String senderOrgTypeCode = "";
        Organization epaOrganization = findOrganizationOptional(epaBundle, epaMedRequest);
        if (epaOrganization != null) {
            senderTelematikId = extractIdentifierValue(epaOrganization, CS_TELEMATIK_ID);
            senderOrgName = epaOrganization.hasName() ? epaOrganization.getName() : practitionerName;
            senderOrgTypeCode = extractOrgTypeCode(epaOrganization);
        }

        String kvnr = extractIdentifierValue(epaPatient, CS_KVID);
        String patientFamily = extractFamily(epaPatient);
        String patientGiven = extractGiven(epaPatient);
        String birthDate = epaPatient.hasBirthDate() ? epaPatient.getBirthDateElement().getValueAsString() : null;
        String street = "", houseNumber = "", city = "", postalCode = "";
        if (epaPatient.hasAddress()) {
            Address addr = epaPatient.getAddressFirstRep();
            city = addr.hasCity() ? addr.getCity() : "";
            postalCode = addr.hasPostalCode() ? addr.getPostalCode() : "";
            if (addr.hasLine()) {
                String fullLine = addr.getLine().getFirst().getValue();
                int lastSpace = fullLine.lastIndexOf(' ');
                if (lastSpace > 0) {
                    street = fullLine.substring(0, lastSpace);
                    houseNumber = fullLine.substring(lastSpace + 1);
                } else {
                    street = fullLine;
                }
            }
        }

        String pzn = extractPzn(epaMedication);
        String medName = epaMedication.hasCode() && epaMedication.getCode().hasText()
            ? epaMedication.getCode().getText()
            : "";
        String formCode = extractFormCode(epaMedication);
        String normgroesse = extractExtensionCode(epaMedication);

        String dosage = epaMedRequest.hasDosageInstruction()
            ? epaMedRequest.getDosageInstructionFirstRep().getText()
            : null;
        int quantity = 1;
        String quantityUnit = "{Package}";
        if (epaMedRequest.hasDispenseRequest() && epaMedRequest.getDispenseRequest().hasQuantity()) {
            Quantity q = epaMedRequest.getDispenseRequest().getQuantity();
            quantity = q.hasValue() ? q.getValue().intValue() : 1;
            quantityUnit = q.hasCode() ? q.getCode() : "{Package}";
        }

        String hdrId = sid(), orgId = sid(), srId = sid();
        String patId = sid(), mrId = sid(), medId = sid();

        MessageHeader header = createMessageHeader(
            hdrId, orgId, srId, senderTelematikId, senderOrgName, practitionerName, kimAddress
        );
        Organization organization = createOrganization(orgId, senderTelematikId, senderOrgName, senderOrgTypeCode);
        ServiceRequest sr = createServiceRequest(srId, orgId, patId, mrId);
        Patient patient = createPatient(
            patId, kvnr, patientFamily, patientGiven, birthDate, street, houseNumber, city, postalCode
        );
        MedicationRequest medReq = createMedicationRequest(mrId, medId, patId, dosage, quantity, quantityUnit);
        Medication med = createMedication(medId, pzn, medName, formCode, normgroesse);

        Bundle bundle = assembleUC1Bundle(header, organization, sr, patient, medReq, med);
        String bundleStringValue = FHIR_CTX.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
        return new KimContext(bundleStringValue, kimAddress);
    }

    private Practitioner findPractitioner(
        Bundle bundle,
        MedicationRequest medRequest
    ) throws PrescriptionSendException {
        Reference requesterRef = medRequest.getRequester();
        if (requesterRef == null || !requesterRef.hasReference()) {
            throw new PrescriptionSendException("MedicationRequest.requester is missing", BAD_REQUEST);
        }
        String ref = requesterRef.getReference();
        if (ref.startsWith("Practitioner/")) {
            return resolveReference(bundle, requesterRef, Practitioner.class);
        }
        if (ref.startsWith("PractitionerRole/")) {
            PractitionerRole role = resolveReference(bundle, requesterRef, PractitionerRole.class);
            return resolveReference(bundle, role.getPractitioner(), Practitioner.class);
        }
        throw new PrescriptionSendException("Unsupported requester reference type: " + ref, BAD_REQUEST);
    }

    private Patient findPatient(Bundle bundle, MedicationRequest medRequest) throws PrescriptionSendException {
        Reference subjectRef = medRequest.getSubject();
        if (subjectRef != null && subjectRef.hasReference()) {
            return resolveReference(bundle, subjectRef, Patient.class);
        }
        return findSingleResource(bundle, Patient.class);
    }

    private Organization findOrganizationOptional(Bundle bundle, MedicationRequest medRequest) {
        try {
            Reference requesterRef = medRequest.getRequester();
            if (requesterRef != null && requesterRef.hasReference()
                && requesterRef.getReference().startsWith("PractitionerRole/")) {
                PractitionerRole role = resolveReference(bundle, requesterRef, PractitionerRole.class);
                if (role.hasOrganization()) {
                    return resolveReference(bundle, role.getOrganization(), Organization.class);
                }
            }
        } catch (PrescriptionSendException ignored) {
        }
        try {
            return findSingleResource(bundle, Organization.class);
        } catch (PrescriptionSendException ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T extends Resource> T findSingleResource(Bundle bundle, Class<T> type) throws PrescriptionSendException {
        return bundle.getEntry().stream()
            .map(BundleEntryComponent::getResource)
            .filter(type::isInstance)
            .map(r -> (T) r)
            .findFirst()
            .orElseThrow(() -> new PrescriptionSendException(
                type.getSimpleName() + " not found in ePA Bundle", NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    private <T extends Resource> T resolveReference(
        Bundle bundle, Reference ref, Class<T> type
    ) throws PrescriptionSendException {
        if (ref == null || !ref.hasReference()) {
            throw new PrescriptionSendException("Missing reference to " + type.getSimpleName(), BAD_REQUEST);
        }
        String refValue = ref.getReference();
        return bundle.getEntry().stream()
            .map(BundleEntryComponent::getResource)
            .filter(type::isInstance)
            .map(r -> (T) r)
            .filter(r -> refValue.endsWith(r.getIdElement().getIdPart()))
            .findFirst()
            .orElseThrow(() -> new PrescriptionSendException(
                type.getSimpleName() + " referenced by " + refValue + " not found", NOT_FOUND));
    }

    private String extractPractitionerName(Practitioner practitioner) {
        if (practitioner.hasName()) {
            HumanName name = practitioner.getNameFirstRep();
            if (name.hasText()) return name.getText();
            String prefix = name.hasPrefix() ? name.getPrefix().getFirst().getValue() + " " : "";
            String given = name.hasGiven() ? name.getGivenAsSingleString() : "";
            String family = name.hasFamily() ? name.getFamily() : "";
            return (prefix + given + " " + family).trim();
        }
        return "";
    }

    private String extractIdentifierValue(Resource resource, String system) {
        if (resource instanceof Patient p) {
            return p.getIdentifier().stream()
                .filter(id -> system.equals(id.getSystem()))
                .map(Identifier::getValue).findFirst().orElse("");
        }
        if (resource instanceof Organization o) {
            return o.getIdentifier().stream()
                .filter(id -> system.equals(id.getSystem()))
                .map(Identifier::getValue).findFirst().orElse("");
        }
        return "";
    }

    private String extractOrgTypeCode(Organization org) {
        if (org.hasType()) {
            return org.getTypeFirstRep().getCoding().stream()
                .filter(c -> CS_ORG_PROFESSION_OID.equals(c.getSystem()))
                .map(Coding::getCode).findFirst().orElse("");
        }
        return "";
    }

    private String extractFamily(Patient p) {
        return p.hasName() && p.getNameFirstRep().hasFamily() ? p.getNameFirstRep().getFamily() : "";
    }

    private String extractGiven(Patient p) {
        return p.hasName() && p.getNameFirstRep().hasGiven() ? p.getNameFirstRep().getGivenAsSingleString() : "";
    }

    private String extractPzn(Medication med) {
        if (med.hasCode() && med.getCode().hasCoding()) {
            return med.getCode().getCoding().stream()
                .filter(c -> CS_PZN.equals(c.getSystem()))
                .map(Coding::getCode).findFirst().orElse("");
        }
        return "";
    }

    private String extractFormCode(Medication med) {
        if (med.hasForm() && med.getForm().hasCoding()) {
            return med.getForm().getCoding().stream()
                .filter(c -> CS_DARREICHUNGSFORM.equals(c.getSystem()))
                .map(Coding::getCode).findFirst()
                .orElse(med.getForm().getCodingFirstRep().getCode());
        }
        return "";
    }

    private String extractExtensionCode(Medication med) {
        return med.getExtension().stream()
            .filter(e -> "http://fhir.de/StructureDefinition/normgroesse".equals(e.getUrl()))
            .filter(e -> e.getValue() instanceof CodeType)
            .map(e -> ((CodeType) e.getValue()).getValue())
            .findFirst().orElse(null);
    }

    Bundle assembleUC1Bundle(
        MessageHeader header, Organization organization, ServiceRequest serviceRequest,
        Patient patient, MedicationRequest medicationRequest, Medication medication
    ) {
        Bundle bundle = new Bundle();
        bundle.setId(UUID.randomUUID().toString());
        bundle.getMeta().addProfile(PROFILE_MESSAGE_CONTAINER);
        bundle.setType(BundleType.MESSAGE);
        bundle.setIdentifier(new Identifier()
            .setSystem("urn:ietf:rfc:3986")
            .setValue("urn:uuid:" + UUID.randomUUID()));
        bundle.setTimestamp(new Date());
        addEntry(bundle, header);
        addEntry(bundle, organization);
        addEntry(bundle, serviceRequest);
        addEntry(bundle, patient);
        addEntry(bundle, medicationRequest);
        addEntry(bundle, medication);
        return bundle;
    }

    private void addEntry(Bundle bundle, Resource resource) {
        bundle.addEntry()
            .setFullUrl("urn:uuid:" + resource.getIdElement().getIdPart())
            .setResource(resource);
    }

    MessageHeader createMessageHeader(
        String id, String orgId, String serviceRequestId,
        String senderTelematikId, String senderName,
        String destName, String destKimAddress
    ) {
        MessageHeader h = new MessageHeader();
        h.setId(id);
        h.getMeta().addProfile(PROFILE_REQUEST_HEADER);
        h.setEvent(new Coding().setSystem(CS_SERVICE_IDENTIFIER).setCode(EVENT_CODE));
        MessageHeader.MessageSourceComponent source = new MessageHeader.MessageSourceComponent();
        source.setName("HealthCare-Source");
        source.setSoftware("HealthCare-Software");
        source.setVersion("1.0.0");
        source.setEndpoint("https://healthcare-software.example.de/endpoint");
        h.setSource(source);
        h.setSender(new Reference()
            .setIdentifier(new Identifier().setSystem(CS_TELEMATIK_ID).setValue(senderTelematikId))
            .setDisplay(senderName));
        h.addDestination(new MessageHeader.MessageDestinationComponent()
            .setName(destName).setEndpoint("mailto:" + destKimAddress));
        h.addFocus(new Reference("ServiceRequest/" + serviceRequestId));
        h.setResponsible(new Reference("Organization/" + orgId));
        return h;
    }

    Organization createOrganization(String id, String telematikId, String name, String typeCode) {
        Organization org = new Organization();
        org.setId(id);
        org.getMeta().addProfile(PROFILE_ORGANIZATION);
        org.addIdentifier().setSystem(CS_TELEMATIK_ID).setValue(telematikId);
        if (typeCode != null && !typeCode.isBlank()) {
            org.addType().addCoding().setSystem(CS_ORG_PROFESSION_OID).setCode(typeCode);
        }
        org.setName(name);
        return org;
    }

    ServiceRequest createServiceRequest(String id, String orgId, String patientId, String medicationRequestId) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(id);
        sr.getMeta().addProfile(PROFILE_PRESCRIPTION_REQUEST);
        sr.addIdentifier().setSystem(NS_REQUEST_ID).setValue(sid());
        sr.setRequisition(new Identifier().setSystem(NS_PROCEDURE_ID).setValue("GroupID-" + sid()));
        sr.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
        sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
        sr.setPriority(ServiceRequest.ServiceRequestPriority.ROUTINE);
        sr.setAuthoredOn(new Date());
        sr.getCode().addCoding().setSystem(CS_SERVICE_REQUEST_TYPE).setCode("prescription-request");
        sr.getSubject().setReference("Patient/" + patientId);
        sr.getRequester().setReference("Organization/" + orgId);
        sr.addBasedOn().setReference("MedicationRequest/" + medicationRequestId);
        return sr;
    }

    Patient createPatient(
        String id, String kvnr, String family, String given, String birthDate,
        String street, String houseNumber, String city, String postalCode
    ) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.getMeta().addProfile(PROFILE_PATIENT);
        patient.addIdentifier().setSystem(CS_KVID).setValue(kvnr);
        HumanName humanName = patient.addName();
        humanName.setUse(HumanName.NameUse.OFFICIAL);
        humanName.addGiven(given);
        StringType familyElement = humanName.getFamilyElement();
        familyElement.setValue(family);
        familyElement.addExtension(
            "http://hl7.org/fhir/StructureDefinition/humanname-own-name", new StringType(family));
        if (birthDate != null && !birthDate.isBlank()) {
            patient.setBirthDateElement(new DateType(birthDate));
        }
        if (street != null && !street.isBlank()) {
            patient.addAddress()
                .setType(Address.AddressType.BOTH)
                .setCity(city)
                .setPostalCode(postalCode)
                .addLine(street + " " + houseNumber);
            StringType line = patient.getAddress().getFirst().getLine().getFirst();
            line.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName", new StringType(street));
            line.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber", new StringType(houseNumber));
        }
        return patient;
    }

    MedicationRequest createMedicationRequest(
        String id, String medicationId, String patientId,
        String dosage, int quantity, String quantityUnit
    ) {
        MedicationRequest mr = new MedicationRequest();
        mr.setId(id);
        mr.getMeta().addProfile(PROFILE_MEDICATION_REQUEST);
        mr.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        mr.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        mr.getMedicationReference().setReference("Medication/" + medicationId);
        mr.getSubject().setReference("Patient/" + patientId);
        if (dosage != null && !dosage.isBlank()) {
            mr.addDosageInstruction().setText(dosage);
        }
        Quantity qty = new Quantity();
        qty.setValue(quantity);
        qty.setUnit("Packung");
        qty.setSystem("http://unitsofmeasure.org");
        qty.setCode(quantityUnit != null ? quantityUnit : "{Package}");
        mr.getDispenseRequest().setQuantity(qty);
        return mr;
    }

    Medication createMedication(String id, String pzn, String name, String formCode, String normgroesse) {
        Medication med = new Medication();
        med.setId(id);
        med.getMeta().addProfile(PROFILE_KBV_MEDICATION_PZN);
        Coding medicationType = new Coding("http://snomed.info/sct", "763158003", "Medicinal product (product)");
        medicationType.setVersion("http://snomed.info/sct/900000000000207008/version/20220331");
        med.addExtension(new Extension(
            "https://fhir.kbv.de/StructureDefinition/KBV_EX_Base_Medication_Type",
            new CodeableConcept(medicationType)));
        med.addExtension(new Extension(
            "https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_Medication_Category",
            new Coding("https://fhir.kbv.de/CodeSystem/KBV_CS_ERP_Medication_Category", "00", null)));
        med.addExtension(new Extension(
            "https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_Medication_Vaccine",
            new BooleanType(false)));
        med.addExtension(new Extension(
            "http://fhir.de/StructureDefinition/normgroesse",
            new CodeType(normgroesse != null ? normgroesse : "N1")));
        med.getCode().addCoding().setSystem(CS_PZN).setCode(pzn != null ? pzn : "");
        med.getCode().setText(name != null ? name : "");
        if (formCode != null && !formCode.isBlank()) {
            med.setForm(new CodeableConcept().addCoding(new Coding(CS_DARREICHUNGSFORM, formCode, "")));
        }
        return med;
    }

    private static String sid() {
        return UUID.randomUUID().toString();
    }
}