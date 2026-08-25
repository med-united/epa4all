sap.ui.define([], function () {
	"use strict";

	/**
	 * Thin client for the ePA Medication Service (gematik IG ePA Medication Service 1.3.1).
	 * Talks to the "fhir" data source of the manifest (/fhir/), which the rest-server forwards
	 * to /epa/medication/api/v1/fhir of the Aktensystem; the KVNR is passed as x-insurantid
	 * (header and query parameter, like the render endpoints) and write operations carry the
	 * base64 encoded Organization in X-Requesting-Organization.
	 */
	const BASE = "https://gematik.de/fhir/epa-medication";

	const MedicationServiceClient = function (sServiceUrl, sKvnr) {
		this.sServiceUrl = (sServiceUrl || "/fhir/").replace(/\/+$/, "");
		this.sKvnr = sKvnr;
	};

	MedicationServiceClient.BASE = BASE;
	MedicationServiceClient.PROFILE = {
		empMedication: BASE + "/StructureDefinition/emp-medication|1.3.0",
		empMedicationRequest: BASE + "/StructureDefinition/emp-medication-request|1.3.1",
		epaMedication: BASE + "/StructureDefinition/epa-medication|1.3.0",
		epaMedicationRequest: BASE + "/StructureDefinition/epa-medication-request|1.3.0",
		epaMedicationDispense: BASE + "/StructureDefinition/epa-medication-dispense|1.3.0",
		addEmpEntryInput: BASE + "/StructureDefinition/epa-op-add-emp-entry-input-parameters|1.3.0",
		updateEmpEntryInput: BASE + "/StructureDefinition/epa-op-update-emp-entry-input-parameters|1.3.0",
		linkEmpInput: BASE + "/StructureDefinition/epa-op-link-emp-input-parameters|1.3.0",
		unlinkEmpInput: BASE + "/StructureDefinition/epa-op-unlink-emp-input-parameters|1.3.0"
	};
	MedicationServiceClient.EXT = {
		context: BASE + "/StructureDefinition/context-extension",
		originMedication: BASE + "/StructureDefinition/emp-origin-medication-extension",
		empActivity: BASE + "/StructureDefinition/emp-activity-extension",
		isChronology: BASE + "/StructureDefinition/is-emp-chronology-extension",
		reasonPatientInstruction: BASE + "/StructureDefinition/reason-patient-instruction-extension",
		patientNote: BASE + "/StructureDefinition/patient-note-extension",
		medicationType: BASE + "/StructureDefinition/epa-medication-type-extension",
		drugCategory: BASE + "/StructureDefinition/drug-category-extension",
		vaccine: BASE + "/StructureDefinition/medication-id-vaccine-extension",
		totalQuantityFormulation: BASE + "/StructureDefinition/medication-total-quantity-formulation-extension",
		effectiveDosePeriod: "http://hl7.org/fhir/5.0/StructureDefinition/extension-MedicationRequest.effectiveDosePeriod",
		renderedDosageInstructionMR: "http://hl7.org/fhir/5.0/StructureDefinition/extension-MedicationRequest.renderedDosageInstruction",
		renderedDosageInstructionMS: "http://hl7.org/fhir/5.0/StructureDefinition/extension-MedicationStatement.renderedDosageInstruction",
		normgroesse: "http://fhir.de/StructureDefinition/normgroesse"
	};
	MedicationServiceClient.SYSTEM = {
		kvid: "http://fhir.de/sid/gkv/kvid-10",
		pzn: "http://fhir.de/CodeSystem/ifa/pzn",
		prescriptionId: "https://gematik.de/fhir/erp/sid/PrescriptionID",
		empIdentifier: BASE + "/sid/emp-identifier",
		telematikId: "https://gematik.de/fhir/sid/telematik-id",
		lanr: "https://fhir.kbv.de/NamingSystem/KBV_NS_Base_ANR",
		darreichungsform: "https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_KBV_DARREICHUNGSFORM",
		dosiereinheit: "https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_BMP_DOSIEREINHEIT",
		icd10: "http://fhir.de/CodeSystem/bfarm/icd-10-gm",
		drugCategory: BASE + "/CodeSystem/epa-drug-category-cs"
	};

	/**
	 * The Organization sent in X-Requesting-Organization: the SMC-B telematik id stored by the settings dialog.
	 */
	MedicationServiceClient.prototype.getRequestingOrganization = function () {
		return {
			resourceType: "Organization",
			identifier: [{
				system: MedicationServiceClient.SYSTEM.telematikId,
				value: localStorage.getItem("telematikId") || "1-SMC-B-Testkarte-883110000141773"
			}],
			name: localStorage.getItem("organizationName") || "epa4all Praxis"
		};
	};

	MedicationServiceClient.prototype._headers = function (bWrite) {
		const mHeaders = {
			"Accept": "application/fhir+json",
			"Content-Type": "application/fhir+json",
			"x-insurantid": this.sKvnr,
			"x-useragent": "epa4allFrontendMock1/1.0.0",
			"X-Request-ID": (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()))
		};
		if (bWrite) {
			const sJson = JSON.stringify(this.getRequestingOrganization());
			mHeaders["X-Requesting-Organization"] = btoa(unescape(encodeURIComponent(sJson)));
		}
		return mHeaders;
	};

	MedicationServiceClient.prototype._url = function (sPath, mQuery) {
		const oParams = new URLSearchParams(Object.assign({ "x-insurantid": this.sKvnr }, mQuery || {}));
		return this.sServiceUrl + "/" + sPath + "?" + oParams.toString();
	};

	MedicationServiceClient.prototype._request = function (sMethod, sPath, oBody, mQuery) {
		const bWrite = sMethod !== "GET";
		return fetch(this._url(sPath, mQuery), {
			method: sMethod,
			headers: this._headers(bWrite),
			body: oBody ? JSON.stringify(oBody) : undefined
		}).then(async (oResponse) => {
			let oJson;
			const sText = await oResponse.text();
			try {
				oJson = sText ? JSON.parse(sText) : {};
			} catch (e) {
				oJson = { resourceType: "OperationOutcome", issue: [{ severity: "error", diagnostics: sText }] };
			}
			if (!oResponse.ok) {
				throw MedicationServiceClient.toError(oJson, oResponse.status);
			}
			return oJson;
		});
	};

	MedicationServiceClient.toError = function (oOperationOutcome, iStatus) {
		const aIssues = (oOperationOutcome && oOperationOutcome.issue) || [];
		const sMessage = aIssues.map((i) => {
			const sCode = i.details && i.details.coding && i.details.coding[0] ? i.details.coding[0].code : i.code;
			return (sCode ? sCode + ": " : "") + (i.diagnostics || i.details && i.details.text || "");
		}).join("\n") || ("HTTP " + iStatus);
		const oError = new Error(sMessage);
		oError.status = iStatus;
		oError.operationOutcome = oOperationOutcome;
		return oError;
	};

	// ---------- Operation API ----------
	MedicationServiceClient.prototype.getMedicationPlan = function () {
		return this._request("GET", "$medication-plan");
	};
	MedicationServiceClient.prototype.getMedicationPlanLog = function () {
		return this._request("GET", "$medication-plan-log");
	};
	MedicationServiceClient.prototype.getMedicationList = function () {
		return this._request("GET", "$medication-list");
	};
	MedicationServiceClient.prototype.addEmpEntry = function (sAcknowledgedChronologyId, oMedication, oEmpEntry) {
		const aParameter = [];
		if (sAcknowledgedChronologyId) {
			aParameter.push({ name: "acknowledgedChronologyId", valueId: sAcknowledgedChronologyId });
		}
		if (oMedication) {
			aParameter.push({ name: "medication", part: [{ name: "resource", resource: oMedication }] });
		}
		aParameter.push({ name: "empEntry", resource: oEmpEntry });
		return this._request("POST", "$add-emp-entry", {
			resourceType: "Parameters",
			meta: { profile: [MedicationServiceClient.PROFILE.addEmpEntryInput] },
			parameter: aParameter
		});
	};
	MedicationServiceClient.prototype.updateEmpEntry = function (sAcknowledgedChronologyId, oMedication, oEmpEntry) {
		const aParameter = [{ name: "acknowledgedChronologyId", valueId: sAcknowledgedChronologyId }];
		if (oMedication) {
			aParameter.push({ name: "medication", part: [{ name: "resource", resource: oMedication }] });
		}
		aParameter.push({ name: "empEntry", resource: oEmpEntry });
		return this._request("POST", "$update-emp-entry", {
			resourceType: "Parameters",
			meta: { profile: [MedicationServiceClient.PROFILE.updateEmpEntryInput] },
			parameter: aParameter
		});
	};
	/**
	 * Links an eML entry (MedicationStatement) with an eMP entry (MedicationRequest, intent = plan).
	 */
	MedicationServiceClient.prototype.linkEmp = function (sStatementId, sEmpEntryId) {
		return this._request("POST", "MedicationStatement/" + sStatementId + "/$link-emp", {
			resourceType: "Parameters",
			meta: { profile: [MedicationServiceClient.PROFILE.linkEmpInput] },
			parameter: [{ name: "empEntryReference", valueReference: { reference: "MedicationRequest/" + sEmpEntryId } }]
		});
	};
	MedicationServiceClient.prototype.unlinkEmp = function (sStatementId) {
		return this._request("POST", "MedicationStatement/" + sStatementId + "/$unlink-emp", {
			resourceType: "Parameters",
			meta: { profile: [MedicationServiceClient.PROFILE.unlinkEmpInput] },
			parameter: []
		});
	};

	// ---------- Query API ----------
	MedicationServiceClient.prototype.read = function (sType, sId) {
		return this._request("GET", sType + "/" + sId);
	};
	MedicationServiceClient.prototype.history = function (sType, sId) {
		return this._request("GET", sType + "/" + sId + "/_history");
	};

	// ---------- helpers ----------
	MedicationServiceClient.ext = function (oResource, sUrl) {
		return ((oResource && oResource.extension) || []).find((e) => e.url === sUrl);
	};
	MedicationServiceClient.ref = function (oResource) {
		return oResource.resourceType + "/" + oResource.id;
	};

	return MedicationServiceClient;
});
