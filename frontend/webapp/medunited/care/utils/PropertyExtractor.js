sap.ui.define([
], function () {
    "use strict";
    return {

        // Extract property from MedicationStatement

        extractPatientFirstNameFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/subject/reference/name/0/given/0");
        },

        extractPatientSurnameFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/subject/reference/name/0/family");
        },

        extractPatientFullNameFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/subject/reference/name/0/given/0") + " " + oView.getModel().getProperty(plan + "/subject/reference/name/0/family");
        },

        extractPharmacyNameFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/derivedFrom/0/reference/name");
        },

        extractPharmacyEmailFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/derivedFrom/0/reference/telecom/1/value");
        },

        extractDoctorEmailFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/subject/reference/generalPractitioner/0/reference/telecom/[system=email]/value");
        },

        extractDoctorFirstNameFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/informationSource/reference/name/0/given/0");
        },

        extractDoctorSurnameFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/informationSource/reference/name/0/family");
        },

        extractDoctorFullNameFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/informationSource/reference/name/0/given/0") + " " + oView.getModel().getProperty(plan + "/informationSource/reference/name/0/family");
        },

        extractPznFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/identifier/0/value");
        },

        extractDosageFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + '/dosage/0/text')
        },

        extractNoteFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + '/note/0/text')
        },

        extractPatientBirthDateFromPlan: function (oView, plan) {
            return oView.getModel().getProperty(plan + "/subject/reference/birthDate");
        },

        // Extract property from Patient

        extractGivenNameFromPatient: function (oView, patient) {
            return oView.getModel().getProperty('/' + patient + '/name/0/given/0');
        },

        extractFamilyNameFromPatient: function (oView, patient) {
            return oView.getModel().getProperty('/' + patient + '/name/0/family');
        },

        extractBirthDateFromPatient: function (oView, patient) {
            return oView.getModel().getProperty('/' + patient + '/birthDate');
        },

        // Extract property from Practitioner
        // e.g.: extractFaxFromPractitioner(oView, "Practitioner/123")

        extractFullNameFromPractitioner: function (oView, practitioner) {
            return oView.getModel().getProperty('/' + practitioner + '/name/0/given/0') + ' ' + oView.getModel().getProperty('/' + practitioner + '/name/0/family');
        },

        extractAddressFromPractitioner: function (oView, practitioner) {
            return oView.getModel().getProperty('/' + practitioner + '/address/[use=home]/line/0');
        },

        extractPostalCodeFromPractitioner: function (oView, practitioner) {
            return oView.getModel().getProperty('/' + practitioner + '/address/[use=home]/postalCode');
        },

        extractCityFromPractitioner: function (oView, practitioner) {
            return oView.getModel().getProperty('/' + practitioner + '/address/[use=home]/city');
        },

        extractEmailFromPractitioner: function (oView, practitioner) {
            return oView.getModel().getProperty('/' + practitioner + '/telecom/[system=email]/value');
        },

        extractPhoneFromPractitioner: function (oView, practitioner) {
            return oView.getModel().getProperty('/' + practitioner + '/telecom/[system=phone]/value');
        },

        extractFaxFromPractitioner: function (oView, practitioner) {
            return oView.getModel().getProperty('/' + practitioner + '/telecom/[system=fax]/value');
        },

        extractPrescriptionInterfaceFromPractitioner: function (oView, practitioner) {
            return oView.getModel().getProperty('/' + practitioner + '/extension/0/valueString');
        }

    };
}, true);