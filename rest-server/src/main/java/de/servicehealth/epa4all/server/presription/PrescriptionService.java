package de.servicehealth.epa4all.server.presription;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.parser.IParser;
import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.kim.KimSmtpService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static de.health.service.cetp.domain.eventservice.card.CardType.HBA;
import static org.hl7.fhir.r4.model.Address.AddressType.BOTH;
import static org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.PHONE;

@SuppressWarnings("HttpUrlsUsage")
@ApplicationScoped
public class PrescriptionService {

    private final IParser fhirParser = FhirContext.forR4().newXmlParser();

    private static final Pattern STREET_AND_NUMBER = Pattern.compile("(.*) ([^ ]*)$");

    @Inject
    KimSmtpService kimSmtpService;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    InsuranceDataService insuranceDataService;

    public String sendKimEmail(
        UserRuntimeConfig userRuntimeConfig,
        String telematikId,
        String smcbHandle,
        String insurantId,
        String selectedEquipment,
        String lanr,
        String namePrefix,
        String bsnr,
        String phone,
        String noteToPharmacy
    ) throws Exception {
        InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
        if (insuranceData == null) {
            insuranceData = insuranceDataService.loadInsuranceData(userRuntimeConfig, smcbHandle, telematikId, insurantId);
        }
        if (insuranceData == null) {
            throw new IllegalStateException("[%s] Insurance data not found".formatted(insurantId));
        }
        Bundle bundle = getBundle(insuranceData, userRuntimeConfig, smcbHandle, selectedEquipment, lanr, namePrefix, bsnr, phone);
        fhirParser.setPrettyPrint(true);
        String prescription = fhirParser.encodeResourceToString(bundle);
        return kimSmtpService.sendERezeptToKIMAddress(prescription, noteToPharmacy);
    }

    private Bundle getBundle(
        InsuranceData insuranceData,
        UserRuntimeConfig userRuntimeConfig,
        String smcbHandle,
        String selectedEquipment,
        String lanr,
        String namePrefix,
        String bsnr,
        String phone
    ) throws Exception {
        String hbaHandle = konnektorClient.getCards(userRuntimeConfig, HBA).getFirst().getCardHandle();

        UCPersoenlicheVersichertendatenXML schaumberg = insuranceData.getPersoenlicheVersichertendaten();
        Patient patient = KBVFHIRUtil.UCPersoenlicheVersichertendatenXML2Patient(schaumberg);
        UCAllgemeineVersicherungsdatenXML versicherung = insuranceData.getAllgemeineVersicherungsdaten();
        UCGeschuetzteVersichertendatenXML versichungKennzeichen = insuranceData.getGeschuetzteVersichertendaten();
        Coverage coverage = KBVFHIRUtil.UCAllgemeineVersicherungsdatenXML2Coverage(
            versicherung, patient.getIdElement().getIdPart(), versichungKennzeichen
        );

        Practitioner practitioner = hbaHandle2Practitioner(userRuntimeConfig, hbaHandle, lanr, namePrefix);
        Organization organization = smcbHandle2Organization(userRuntimeConfig, smcbHandle, bsnr, phone);
        Medication medication = createMedicationResource();

        MedicationRequest medicationRequest = createMedicationRequest(
            selectedEquipment,
            medication.getIdElement().getIdPart(),
            patient.getIdElement().getIdPart(),
            practitioner.getIdElement().getIdPart(),
            coverage.getIdElement().getIdPart()
        );
        return KBVFHIRUtil.assembleBundle(
            practitioner, organization, patient, coverage, medication, medicationRequest, null, null
        );
    }

    private Organization smcbHandle2Organization(
        UserRuntimeConfig runtimeConfig,
        String smcbHandle,
        String bsnr,
        String phone
    ) throws Exception {
        X509Certificate x509Certificate = konnektorClient.getSmcbX509Certificate(runtimeConfig, smcbHandle).getCertificate();
        X500Name x500name = new JcaX509CertificateHolder(x509Certificate).getSubject();

        // C=DE,L=Freiburg,PostalCode=79114,STREET=Sundgauallee
        // 59,SERIALNUMBER=80276883110000118001,CN=VincenzkrankenhausTEST-ONLY

        String city = getRdnValue(x500name, BCStyle.L);
        String postalCode = getRdnValue(x500name, BCStyle.POSTAL_CODE);

        String streetName = "";
        String houseNumber = "";

        String street = getRdnValue(x500name, BCStyle.STREET);
        Matcher m = STREET_AND_NUMBER.matcher(street);
        if (m.matches()) {
            streetName = m.group(1);
            houseNumber = m.group(2);
        } else {
            streetName = street;
        }

        String organizationName = getRdnValue(x500name, BCStyle.CN);
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID().toString()).getMeta()
            .addProfile("https://fhir.kbv.de/StructureDefinition/KBV_PR_FOR_Organization|1.1.0");

        Identifier identifier = organization.addIdentifier();
        CodeableConcept codeableConcept = identifier.getType();
        codeableConcept.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setCode("BSNR");

        identifier.setSystem("https://fhir.kbv.de/NamingSystem/KBV_NS_Base_BSNR");
        identifier.setValue(bsnr);

        organization.setName(organizationName);
        organization.addTelecom().setSystem(PHONE).setValue(phone);
        organization.addAddress()
            .setType(BOTH)
            .setCity(city)
            .setPostalCode(postalCode)
            .addLine(streetName + " " + houseNumber)
            .setCountry("D");

        StringType line = organization.getAddress().getFirst().getLine().getFirst();
        line.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName", new StringType(streetName));
        line.addExtension("http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber", new StringType(houseNumber));

        return organization;
    }

    private Practitioner hbaHandle2Practitioner(
        UserRuntimeConfig runtimeConfig,
        String hbaHandle,
        String lanr,
        String namePrefix
    ) throws Exception {
        X509Certificate x509Certificate = konnektorClient.getHbaX509Certificate(runtimeConfig, hbaHandle);

        X500Name x500name = new JcaX509CertificateHolder(x509Certificate).getSubject();
        String firstName = getRdnValue(x500name, BCStyle.GIVENNAME);
        String lastName = getRdnValue(x500name, BCStyle.SURNAME);

        Practitioner practitioner = new Practitioner();
        practitioner.setId(UUID.randomUUID().toString()).getMeta()
            .addProfile("https://fhir.kbv.de/StructureDefinition/KBV_PR_FOR_Practitioner|1.1.0");

        Identifier identifier = practitioner.addIdentifier();
        CodeableConcept identifierCodeableConcept = identifier.getType();
        identifierCodeableConcept.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setCode("LANR");

        identifier.setSystem("https://fhir.kbv.de/NamingSystem/KBV_NS_Base_ANR");
        identifier.setValue(lanr);

        List<StringType> prefixList = new ArrayList<>();
        if (namePrefix != null && !namePrefix.isEmpty()) {
            StringType prefix = new StringType(namePrefix);
            Extension extension = new Extension(
                "http://hl7.org/fhir/StructureDefinition/iso21090-EN-qualifier", new CodeType("AC")
            );
            prefix.addExtension(extension);
            prefixList.add(prefix);
        }

        HumanName humanName = practitioner.addName();
        humanName.setUse(HumanName.NameUse.OFFICIAL).setPrefix(prefixList).addGiven(firstName);

        StringType familyElement = humanName.getFamilyElement();
        List<String> nameParts = new ArrayList<>();

        Extension extension = new Extension("http://hl7.org/fhir/StructureDefinition/humanname-own-name", new StringType(lastName));
        familyElement.addExtension(extension);
        nameParts.add(lastName);
        familyElement.setValue(String.join(" ", nameParts));

        Practitioner.PractitionerQualificationComponent qualification = new Practitioner.PractitionerQualificationComponent();
        CodeableConcept qualificationCodeableConcept = new CodeableConcept();
        Coding practitionerQualificationCoding = new Coding(
            "https://fhir.kbv.de/CodeSystem/KBV_CS_FOR_Qualification_Type", "00", null
        );
        qualificationCodeableConcept.addCoding(practitionerQualificationCoding);

        qualification.setCode(qualificationCodeableConcept);
        practitioner.addQualification(qualification);
        qualification = practitioner.addQualification();
        CodeableConcept code = qualification.getCode();
        code.setText("Arzt");
        practitionerQualificationCoding = new Coding(
            "https://fhir.kbv.de/CodeSystem/KBV_CS_FOR_Berufsbezeichnung", "Berufsbezeichnung", null
        );
        code.getCoding().add(practitionerQualificationCoding);

        return practitioner;
    }

    private String getRdnValue(X500Name x500name, ASN1ObjectIdentifier rdnType) {
        return IETFUtils.valueToString(Stream.of(x500name.getRDNs(rdnType)[0].getTypesAndValues())
            .filter(tv -> tv.getType() == rdnType).findFirst().get().getValue());
    }

    private Medication createMedicationResource() {
        Medication medication = new Medication();

        medication.setId(UUID.randomUUID().toString()).getMeta()
            .addProfile("https://fhir.kbv.de/StructureDefinition/KBV_PR_ERP_Medication_PZN|1.1.0");

        Coding medicationType = new Coding("http://snomed.info/sct", "763158003", "Medicinal product (product)");
        medicationType.setVersion("http://snomed.info/sct/900000000000207008/version/20220331");
        CodeableConcept codeableConcept = new CodeableConcept(medicationType);
        Extension medicationTypeEx = new Extension("https://fhir.kbv.de/StructureDefinition/KBV_EX_Base_Medication_Type", codeableConcept);
        medication.addExtension(medicationTypeEx);

        Coding medicationCategory = new Coding("https://fhir.kbv.de/CodeSystem/KBV_CS_ERP_Medication_Category", "00",
            null);
        Extension medicationCategoryEx = new Extension(
            "https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_Medication_Category", medicationCategory);
        medication.addExtension(medicationCategoryEx);

        Extension medicationVaccine = new Extension(
            "https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_Medication_Vaccine", new BooleanType(false));
        medication.addExtension(medicationVaccine);

        String normgroesseString = "N1";

        Extension normgroesse = new Extension("http://fhir.de/StructureDefinition/normgroesse",
            new CodeType(normgroesseString));
        medication.addExtension(normgroesse);

        medication.getCode().addCoding().setSystem("http://fhir.de/CodeSystem/ifa/pzn").setCode("");
        medication.getCode().setText("");
        String darreichungsform = "TAB";
        Coding formCoding = new Coding("https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_KBV_DARREICHUNGSFORM",
            darreichungsform, "");
        medication.setForm(new CodeableConcept().addCoding(formCoding));

        return medication;
    }

    private MedicationRequest createMedicationRequest(
        String selectedEquipment,
        String medicationId,
        String patientId,
        String practitionerId,
        String coverageId
    ) {
        MedicationRequest medicationRequest = new MedicationRequest();

        medicationRequest.setAuthoredOn(new Date());
        medicationRequest.getAuthoredOnElement().setPrecision(TemporalPrecisionEnum.DAY);

        medicationRequest.setId(UUID.randomUUID().toString());

        medicationRequest.getMeta().addProfile("https://fhir.kbv.de/StructureDefinition/KBV_PR_ERP_Prescription|1.1.0");

        Coding valueCoding = new Coding("https://fhir.kbv.de/CodeSystem/KBV_CS_FOR_StatusCoPayment", "0", null);
        Extension coPayment = new Extension("https://fhir.kbv.de/StructureDefinition/KBV_EX_FOR_StatusCoPayment",
            valueCoding);
        medicationRequest.addExtension(coPayment);

        // emergencyServiceFeeParam default false
        Extension emergencyServicesFee = new Extension(
            "https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_EmergencyServicesFee", new BooleanType(false));
        medicationRequest.addExtension(emergencyServicesFee);

        Extension bvg = new Extension("https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_BVG", new BooleanType(false));
        medicationRequest.addExtension(bvg);

        // <extension
        // url="https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_Multiple_Prescription">
        Extension multiplePrescription = new Extension(
            "https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_Multiple_Prescription");
        // <extension url="Kennzeichen">
        // <valueBoolean value="true" />
        // </extension>
        multiplePrescription.addExtension(new Extension("Kennzeichen", new BooleanType(false)));
        medicationRequest.addExtension(multiplePrescription);

        medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE)
            .setIntent(MedicationRequest.MedicationRequestIntent.ORDER).getMedicationReference()
            .setReference("Medication/" + medicationId);

        medicationRequest.getSubject().setReference("Patient/" + patientId);
        medicationRequest.getRequester().setReference("Practitioner/" + practitionerId);
        medicationRequest.addInsurance().setReference("Coverage/" + coverageId);
        medicationRequest.setNote(Collections.singletonList(new Annotation().setText(selectedEquipment)));

        medicationRequest.addDosageInstruction().setText("").addExtension()
            .setUrl("https://fhir.kbv.de/StructureDefinition/KBV_EX_ERP_DosageFlag")
            .setValue(new BooleanType(true));

        MedicationRequest.MedicationRequestDispenseRequestComponent dispenseRequest = new MedicationRequest.MedicationRequestDispenseRequestComponent();
        Quantity quantity = new Quantity();
        quantity.setValue(1);
        quantity.setSystem("http://unitsofmeasure.org");
        quantity.setCode("{Package}");
        dispenseRequest.setQuantity(quantity);
        medicationRequest.setDispenseRequest(dispenseRequest);
        MedicationRequest.MedicationRequestSubstitutionComponent substitution = new MedicationRequest.MedicationRequestSubstitutionComponent();
        substitution.setAllowed(new BooleanType(true));
        medicationRequest.setSubstitution(substitution);

        return medicationRequest;
    }
}
