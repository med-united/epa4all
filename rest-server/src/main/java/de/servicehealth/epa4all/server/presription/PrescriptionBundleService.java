package de.servicehealth.epa4all.server.presription;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.servicehealth.epa4all.server.presription.requestdata.EPrescriptionRequest;
import de.servicehealth.epa4all.server.presription.requestdata.OrganizationData;
import de.servicehealth.epa4all.server.presription.requestdata.PatientData;
import jakarta.enterprise.context.ApplicationScoped;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
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
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;

import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static java.nio.charset.StandardCharsets.UTF_8;

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

    public String buildPrescriptionRequestBundle(
        EPrescriptionRequest request,
        String kimAddress
    ) throws PrescriptionSendException {
        IParser parser = FHIR_CTX.newJsonParser();
        MedicationRequest medicationRequest = decodeResource(parser, request.getEpaMedicationRequestBase64(), MedicationRequest.class);
        Medication medication = decodeResource(parser, request.getEpaMedicationBase64(), Medication.class);

        String medRefInRequest = medicationRequest.getMedicationReference().getReference();
        String medicationId = medication.getIdElement().getIdPart();

        if (!medRefInRequest.endsWith(medicationId)) {
            String msg = "MedicationRequest references " + medRefInRequest + " but received Medication/" + medicationId;
            throw new PrescriptionSendException(msg, CONFLICT);
        }

        String pzn = extractPzn(medication);
        String medName = medication.hasCode() && medication.getCode().hasText() ? medication.getCode().getText() : "";
        String formCode = extractFormCode(medication);
        String normgroesse = extractExtensionCode(medication);

        String dosage = medicationRequest.hasDosageInstruction() ? medicationRequest.getDosageInstructionFirstRep().getText() : null;
        int quantity = 1;
        String quantityUnit = "{Package}";
        if (medicationRequest.hasDispenseRequest() && medicationRequest.getDispenseRequest().hasQuantity()) {
            Quantity q = medicationRequest.getDispenseRequest().getQuantity();
            quantity = q.hasValue() ? q.getValue().intValue() : 1;
            quantityUnit = q.hasCode() ? q.getCode() : "{Package}";
        }

        PatientData pat = request.getPatientData();
        OrganizationData org = request.getOrganizationData();

        String hdrId = sid(), orgId = sid(), srId = sid();
        String patId = sid(), mrId = sid(), medId = sid();

        MessageHeader header = createMessageHeader(
            hdrId, orgId, srId, org.getTelematikId(), org.getOrgName(),
            request.getPractitionerName(), kimAddress
        );
        Organization organization = createOrganization(orgId, org);
        ServiceRequest sr = createServiceRequest(srId, orgId, patId, mrId);
        Patient patient = createPatient(patId, pat);
        MedicationRequest medReq = createMedicationRequest(mrId, medId, patId, dosage, quantity, quantityUnit);
        Medication med = createMedication(medId, pzn, medName, formCode, normgroesse);

        Bundle bundle = assembleUC1Bundle(header, organization, sr, patient, medReq, med);

        return FHIR_CTX.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }

    private <T extends Resource> T decodeResource(IParser parser, String base64, Class<T> type) {
        String json = new String(Base64.getDecoder().decode(base64), UTF_8);
        return parser.parseResource(type, json);
    }

    /**
     * Assembles the UC1-1 message Bundle from pre-built resources.
     * Entry order follows the gematik spec: MessageHeader, Organization,
     * ServiceRequest, Patient, MedicationRequest, Medication.
     */
    Bundle assembleUC1Bundle(
        MessageHeader header,
        Organization organization,
        ServiceRequest serviceRequest,
        Patient patient,
        MedicationRequest medicationRequest,
        Medication medication
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

    Organization createOrganization(String id, OrganizationData data) {
        Organization org = new Organization();
        org.setId(id);
        org.getMeta().addProfile(PROFILE_ORGANIZATION);
        org.addIdentifier().setSystem(CS_TELEMATIK_ID).setValue(data.getTelematikId());
        if (data.getOrgTypeCode() != null && !data.getOrgTypeCode().isBlank()) {
            org.addType().addCoding().setSystem(CS_ORG_PROFESSION_OID).setCode(data.getOrgTypeCode());
        }
        org.setName(data.getOrgName());
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

    Patient createPatient(String id, PatientData data) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.getMeta().addProfile(PROFILE_PATIENT);
        patient.addIdentifier().setSystem(CS_KVID).setValue(data.getKvnr());

        HumanName humanName = patient.addName();
        humanName.setUse(HumanName.NameUse.OFFICIAL);
        humanName.addGiven(data.getGiven());
        StringType familyElement = humanName.getFamilyElement();
        familyElement.setValue(data.getFamily());
        familyElement.addExtension(
            "http://hl7.org/fhir/StructureDefinition/humanname-own-name", new StringType(data.getFamily()));

        if (data.getBirthDate() != null && !data.getBirthDate().isBlank()) {
            patient.setBirthDateElement(new DateType(data.getBirthDate()));
        }

        patient.addAddress()
            .setType(Address.AddressType.BOTH)
            .setCity(data.getCity())
            .setPostalCode(data.getPostalCode())
            .addLine(data.getStreet() + " " + data.getHouseNumber());

        StringType line = patient.getAddress().getFirst().getLine().getFirst();
        line.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName", new StringType(data.getStreet()));
        line.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber", new StringType(data.getHouseNumber()));

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

    private String extractPzn(Medication med) {
        if (med.hasCode() && med.getCode().hasCoding()) {
            return med.getCode().getCoding().stream()
                .filter(c -> CS_PZN.equals(c.getSystem()))
                .map(Coding::getCode)
                .findFirst().orElse("");
        }
        return "";
    }

    private String extractFormCode(Medication med) {
        if (med.hasForm() && med.getForm().hasCoding()) {
            return med.getForm().getCoding().stream()
                .filter(c -> CS_DARREICHUNGSFORM.equals(c.getSystem()))
                .map(Coding::getCode)
                .findFirst()
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

    private static String sid() {
        return UUID.randomUUID().toString();
    }
}