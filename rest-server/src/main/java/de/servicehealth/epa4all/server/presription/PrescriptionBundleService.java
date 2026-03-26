package de.servicehealth.epa4all.server.presription;

import ca.uhn.fhir.context.FhirContext;
import de.servicehealth.epa4all.server.presription.requestdata.MedicationData;
import de.servicehealth.epa4all.server.presription.requestdata.OrganizationData;
import de.servicehealth.epa4all.server.presription.requestdata.PatientData;
import de.servicehealth.epa4all.server.presription.requestdata.PractitionerData;
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

import java.util.Date;
import java.util.UUID;

/**
 * Builds a UC1-1-Prescription-Request-To-Prescriber Bundle per gematik spec-E-Rezept-ServiceRequest.
 *
 * <p>The resulting Bundle is a FHIR message (type: message) conforming to
 * {@code erp-service-request-message-container} and contains 6 entries:
 * MessageHeader, Organization, ServiceRequest, Patient, MedicationRequest, Medication.
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

    /**
     * Builds a UC1-1-Prescription-Request Bundle from a raw ePA Medication JSON.
     */
    public String buildPrescriptionRequestBundleFromEpa(
        String epaMedicationJson,
        String dosage,
        PatientData patientData,
        OrganizationData organizationData,
        PractitionerData practitionerData
    ) {
        Medication epaMed = FHIR_CTX.newJsonParser().parseResource(Medication.class, epaMedicationJson);

        String pzn = extractPzn(epaMed);
        String name = epaMed.hasCode() && epaMed.getCode().hasText() ? epaMed.getCode().getText() : "";
        String formCode = extractFormCode(epaMed);
        String normgroesse = extractExtensionCode(epaMed);

        int quantity = 1;
        String quantityUnit = "{Package}";
        if (epaMed.hasAmount() && epaMed.getAmount().hasNumerator() && epaMed.getAmount().getNumerator().hasCode()) {
            quantityUnit = epaMed.getAmount().getNumerator().getCode();
        }
        if (epaMed.hasAmount() && epaMed.getAmount().hasDenominator() && epaMed.getAmount().getDenominator().hasValue()) {
            quantity = epaMed.getAmount().getDenominator().getValue().intValue();
        }

        MedicationData medicationData = new MedicationData(
            pzn, name, formCode, normgroesse, dosage, quantity, quantityUnit
        );
        return buildPrescriptionRequestBundle(medicationData, patientData, organizationData, practitionerData);
    }

    public String buildPrescriptionRequestBundle(
        MedicationData medicationData,
        PatientData patientData,
        OrganizationData organizationData,
        PractitionerData practitionerData
    ) {
        String hdrId = randomId();
        String orgId = randomId();
        String srId = randomId();
        String mrId = randomId();
        String medId = randomId();
        String patientId = randomId();

        MessageHeader header = createMessageHeader(
            hdrId, orgId, srId, organizationData.getTelematikId(), organizationData.getOrgName(),
            practitionerData.getName(), practitionerData.getKimAddress()
        );
        Organization org = createOrganization(
            orgId, organizationData.getTelematikId(), organizationData.getOrgName(), organizationData.getOrgTypeCode()
        );
        ServiceRequest sr = createServiceRequest(srId, orgId, patientId, mrId);
        Patient patient = createPatient(patientId, patientData);
        MedicationRequest medReq = createMedicationRequest(mrId, medId, patientId, medicationData);
        Medication med = createMedication(medId, medicationData);

        Bundle bundle = assembleUC1Bundle(header, org, sr, patient, medReq, med);
        return FHIR_CTX.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }

    private static String randomId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Assembles the UC1-1 message Bundle from pre-built resources.
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
        bundle.setId(randomId());
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
        String destName, String destEndpoint
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
            .setName(destName).setEndpoint(destEndpoint));
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
        sr.addIdentifier().setSystem(NS_REQUEST_ID).setValue(randomId());
        sr.setRequisition(new Identifier().setSystem(NS_PROCEDURE_ID).setValue("GroupID-" + UUID.randomUUID()));
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

    Patient createPatient(String id, PatientData patientData) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.getMeta().addProfile(PROFILE_PATIENT);
        patient.addIdentifier().setSystem(CS_KVID).setValue(patientData.getKvnr());

        HumanName humanName = patient.addName();
        humanName.setUse(HumanName.NameUse.OFFICIAL);
        humanName.addGiven(patientData.getGiven());
        StringType familyElement = humanName.getFamilyElement();
        familyElement.setValue(patientData.getFamily());
        familyElement.addExtension(
            "http://hl7.org/fhir/StructureDefinition/humanname-own-name", new StringType(patientData.getFamily()));

        if (patientData.getBirthDate() != null && !patientData.getBirthDate().isBlank()) {
            patient.setBirthDateElement(new DateType(patientData.getBirthDate()));
        }

        patient.addAddress()
            .setType(Address.AddressType.BOTH)
            .setCity(patientData.getCity())
            .setPostalCode(patientData.getPostalCode())
            .addLine(patientData.getStreet() + " " + patientData.getHouseNumber());

        StringType line = patient.getAddress().getFirst().getLine().getFirst();
        line.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName", new StringType(patientData.getStreet()));
        line.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber", new StringType(patientData.getHouseNumber()));

        return patient;
    }

    MedicationRequest createMedicationRequest(
        String id, String medicationId, String patientId, MedicationData medicationData
    ) {
        MedicationRequest mr = new MedicationRequest();
        mr.setId(id);
        mr.getMeta().addProfile(PROFILE_MEDICATION_REQUEST);
        mr.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        mr.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        mr.getMedicationReference().setReference("Medication/" + medicationId);
        mr.getSubject().setReference("Patient/" + patientId);

        if (medicationData.getDosage() != null && !medicationData.getDosage().isBlank()) {
            mr.addDosageInstruction().setText(medicationData.getDosage());
        }

        Quantity qty = new Quantity();
        qty.setValue(medicationData.getQuantity());
        qty.setUnit("Packung");
        qty.setSystem("http://unitsofmeasure.org");
        qty.setCode(medicationData.getQuantityUnit() != null ? medicationData.getQuantityUnit() : "{Package}");
        mr.getDispenseRequest().setQuantity(qty);

        return mr;
    }

    Medication createMedication(String id, MedicationData medicationData) {
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
            new CodeType(medicationData.getNormgroesse() != null ? medicationData.getNormgroesse() : "N1")));

        med.getCode().addCoding().setSystem(CS_PZN).setCode(medicationData.getPzn() != null ? medicationData.getPzn() : "");
        med.getCode().setText(medicationData.getName() != null ? medicationData.getName() : "");

        if (medicationData.getFormCode() != null && !medicationData.getFormCode().isBlank()) {
            med.setForm(new CodeableConcept().addCoding(new Coding(CS_DARREICHUNGSFORM, medicationData.getFormCode(), "")));
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
}