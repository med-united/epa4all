/**
 * Very small in-memory mock of the gematik ePA Medication Service
 * (Implementation Guide ePA Medication Service v1.3.1, FHIR R4).
 *
 * Implements the FHIR operations the frontend needs for the technical use case
 * "Verordnung, Verschreibung und Dispensierung mit dem eMP":
 *
 *   GET  $medication-plan          eMP (EMPMedicationRequest + EMPMedication + linked eML entries + chronology)
 *   GET  $medication-plan-log      eMP chronology (EMPChronologyProvenance history)
 *   GET  $medication-list          eML (EPAMedicationStatement + referenced resources)
 *   POST $add-emp-entry            create an eMP entry (requires acknowledgedChronologyId)
 *   POST $update-emp-entry         update an eMP entry (new version, requires acknowledgedChronologyId)
 *   POST $provide-prescription-erp E-Rezept prescription (normally sent by the E-Rezept-Fachdienst)
 *   POST $provide-dispensation-erp E-Rezept dispensation (normally sent by the E-Rezept-Fachdienst)
 *   POST MedicationStatement/{id}/$link-emp    link an eML entry with an eMP entry (empEntryReference)
 *   POST MedicationStatement/{id}/$unlink-emp  remove that link
 *   GET  {Type}/{id}, {Type}/{id}/_history, {Type}?... (simple Query API)
 *
 * The linking logic of the spec is emulated:
 *   - prescription: EPAMedication (inactive) + EPAMedicationRequest (active) + EPAMedicationStatement (intended,
 *     derivedFrom request, dosage copied). If the prescription references an eMP entry (basedOn with the
 *     eMP identifier) the statement is linked (statement.basedOn -> eMP entry, eMP entry.activity -> statement).
 *   - dispensation: EPAMedicationDispense (completed), medication -> active, statement -> unknown. The dispensed
 *     Medication becomes the medicationReference of the linked eMP entry (except KPG), the original Medication is
 *     preserved in the originMedication extension, a changed dosage of a substitution is copied to the eMP entry.
 *   - every change creates an EPAActivityProvenance, every eMP change a new EMPChronologyProvenance.
 *
 * Everything is kept in memory per KVNR (x-insurantid) and is lost on restart.
 */
"use strict";

const crypto = require("crypto");

const BASE = "https://gematik.de/fhir/epa-medication";
const PROFILE = {
	empMedication: BASE + "/StructureDefinition/emp-medication|1.3.0",
	empMedicationRequest: BASE + "/StructureDefinition/emp-medication-request|1.3.1",
	epaMedication: BASE + "/StructureDefinition/epa-medication|1.3.0",
	epaMedicationRequest: BASE + "/StructureDefinition/epa-medication-request|1.3.0",
	epaMedicationDispense: BASE + "/StructureDefinition/epa-medication-dispense|1.3.0",
	epaMedicationStatement: BASE + "/StructureDefinition/epa-medication-statement|1.3.0",
	activityProvenance: BASE + "/StructureDefinition/epa-activity-provenance|1.3.0",
	chronologyProvenance: BASE + "/StructureDefinition/emp-chronology-provenance|1.3.0",
	operationOutcome: BASE + "/StructureDefinition/epa-ms-operation-outcome|1.3.0",
	organizationDirectory: "https://gematik.de/fhir/directory/StructureDefinition/OrganizationDirectory",
	practitionerDirectory: "https://gematik.de/fhir/directory/StructureDefinition/PractitionerDirectory",
	tiOrganization: "https://gematik.de/fhir/ti/StructureDefinition/TIOrganization"
};
const EXT = {
	context: BASE + "/StructureDefinition/context-extension",
	originMedication: BASE + "/StructureDefinition/emp-origin-medication-extension",
	empActivity: BASE + "/StructureDefinition/emp-activity-extension",
	isChronology: BASE + "/StructureDefinition/is-emp-chronology-extension",
	reasonPatientInstruction: BASE + "/StructureDefinition/reason-patient-instruction-extension",
	patientNote: BASE + "/StructureDefinition/patient-note-extension",
	medicationType: BASE + "/StructureDefinition/epa-medication-type-extension",
	drugCategory: BASE + "/StructureDefinition/drug-category-extension",
	vaccine: BASE + "/StructureDefinition/medication-id-vaccine-extension",
	effectiveDosePeriod: "http://hl7.org/fhir/5.0/StructureDefinition/extension-MedicationRequest.effectiveDosePeriod",
	renderedDosageInstructionMR: "http://hl7.org/fhir/5.0/StructureDefinition/extension-MedicationRequest.renderedDosageInstruction",
	renderedDosageInstructionMS: "http://hl7.org/fhir/5.0/StructureDefinition/extension-MedicationStatement.renderedDosageInstruction",
	normgroesse: "http://fhir.de/StructureDefinition/normgroesse"
};
const SYSTEM = {
	kvid: "http://fhir.de/sid/gkv/kvid-10",
	pzn: "http://fhir.de/CodeSystem/ifa/pzn",
	prescriptionId: "https://gematik.de/fhir/erp/sid/PrescriptionID",
	empIdentifier: BASE + "/sid/emp-identifier",
	telematikId: "https://gematik.de/fhir/sid/telematik-id",
	lanr: "https://fhir.kbv.de/NamingSystem/KBV_NS_Base_ANR",
	darreichungsform: "https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_KBV_DARREICHUNGSFORM",
	icd10: "http://fhir.de/CodeSystem/bfarm/icd-10-gm",
	provenanceActivity: "http://terminology.hl7.org/CodeSystem/v3-DataOperation",
	participationType: "http://terminology.hl7.org/CodeSystem/provenance-participant-type",
	outcomeDetails: BASE + "/CodeSystem/epa-ms-operation-outcome-details"
};

const MEDICATIONSVC = "MEDICATIONSVC";

function uuid() {
	return crypto.randomUUID();
}
function now() {
	return new Date().toISOString();
}
function today() {
	return new Date().toISOString().substring(0, 10);
}
function clone(o) {
	return JSON.parse(JSON.stringify(o));
}
function ext(oResource, sUrl) {
	return (oResource.extension || []).find((e) => e.url === sUrl);
}
function setExt(oResource, oExtension) {
	oResource.extension = (oResource.extension || []).filter((e) => e.url !== oExtension.url);
	oResource.extension.push(oExtension);
}
function contextOf(oResource) {
	const e = ext(oResource, EXT.context);
	return e ? e.valueCode : undefined;
}
function versionedRef(oResource) {
	return oResource.resourceType + "/" + oResource.id + "/_history/" + oResource.meta.versionId;
}
function ref(oResource) {
	return oResource.resourceType + "/" + oResource.id;
}
function pznOf(oMedication) {
	const c = ((oMedication.code || {}).coding || []).find((c) => c.system === SYSTEM.pzn);
	return c ? c.code : undefined;
}
function formOf(oMedication) {
	const c = ((oMedication.form || {}).coding || [])[0];
	return c ? c.code : undefined;
}

class OperationError extends Error {
	constructor(iStatus, sCode, sDiagnostics) {
		super(sDiagnostics);
		this.status = iStatus;
		this.code = sCode;
	}
}

function operationOutcome(sSeverity, sCode, sDetailCode, sDiagnostics) {
	return {
		resourceType: "OperationOutcome",
		id: uuid(),
		meta: { profile: [PROFILE.operationOutcome] },
		issue: [{
			severity: sSeverity,
			code: sCode,
			details: { coding: [{ system: SYSTEM.outcomeDetails, code: sDetailCode }] },
			diagnostics: sDiagnostics
		}]
	};
}

/**
 * Per insurant record.
 */
class Record {
	constructor(sKvnr) {
		this.kvnr = sKvnr;
		this.store = {}; // resourceType -> id -> [versions]
		this.chronologyId = undefined;
		this.seed();
	}

	// ---------- generic storage ----------
	_versions(sType, sId) {
		this.store[sType] = this.store[sType] || {};
		this.store[sType][sId] = this.store[sType][sId] || [];
		return this.store[sType][sId];
	}
	save(oResource) {
		if (!oResource.id) {
			oResource.id = uuid();
		}
		const aVersions = this._versions(oResource.resourceType, oResource.id);
		oResource.meta = oResource.meta || {};
		oResource.meta.versionId = String(aVersions.length + 1);
		oResource.meta.lastUpdated = now();
		aVersions.push(clone(oResource));
		return clone(oResource);
	}
	get(sType, sId, sVersion) {
		const aVersions = ((this.store[sType] || {})[sId]) || [];
		if (!aVersions.length) {
			return undefined;
		}
		if (sVersion) {
			return clone(aVersions.find((r) => r.meta.versionId === sVersion));
		}
		return clone(aVersions[aVersions.length - 1]);
	}
	history(sType, sId) {
		return clone(((this.store[sType] || {})[sId]) || []).reverse();
	}
	all(sType) {
		return Object.values(this.store[sType] || {}).map((a) => clone(a[a.length - 1]));
	}
	resolve(sReference) {
		if (!sReference) {
			return undefined;
		}
		const m = /^([A-Za-z]+)\/([^/]+)(?:\/_history\/([^/]+))?$/.exec(sReference);
		return m ? this.get(m[1], m[2], m[3]) : undefined;
	}

	// ---------- provenance ----------
	activityProvenance(sActivity, aTargets, oAgentWho, sAgentType) {
		return this.save({
			resourceType: "Provenance",
			meta: { profile: [PROFILE.activityProvenance] },
			target: aTargets.map((r) => ({ reference: versionedRef(r) })),
			recorded: now(),
			activity: { coding: [{ system: SYSTEM.provenanceActivity, code: sActivity }] },
			agent: [{
				type: { coding: [{ system: SYSTEM.participationType, code: sAgentType || "author" }] },
				who: oAgentWho || { identifier: { system: SYSTEM.telematikId, value: MEDICATIONSVC }, display: MEDICATIONSVC }
			}]
		});
	}
	empEntries() {
		return this.all("MedicationRequest").filter((r) => r.intent === "plan" && contextOf(r) === "EMP");
	}
	chronology(oAgentWho) {
		const aActive = this.empEntries().filter((r) => r.status === "active" || r.status === "on-hold");
		const oChronology = this.save({
			resourceType: "Provenance",
			meta: { profile: [PROFILE.chronologyProvenance] },
			extension: [{ url: EXT.isChronology, valueBoolean: true }],
			target: aActive.map((r) => ({ reference: versionedRef(r) })),
			recorded: now(),
			activity: { coding: [{ system: SYSTEM.provenanceActivity, code: "UPDATE" }] },
			agent: [{
				type: { coding: [{ system: SYSTEM.participationType, code: "author" }] },
				who: oAgentWho || { identifier: { system: SYSTEM.telematikId, value: MEDICATIONSVC }, display: MEDICATIONSVC }
			}]
		});
		this.chronologyId = oChronology.id;
		this.activityProvenance("CREATE", [oChronology], oAgentWho);
		return oChronology;
	}
	currentChronology() {
		return this.chronologyId ? this.get("Provenance", this.chronologyId) : undefined;
	}
	checkChronology(sAcknowledgedId) {
		if (this.chronologyId && sAcknowledgedId !== this.chronologyId) {
			throw new OperationError(409, "MEDICATIONSVC_EMP_CHRONOLOGY_OUTDATED",
				"acknowledgedChronologyId '" + sAcknowledgedId + "' is not the latest chronology '" + this.chronologyId + "'. Reload the eMP.");
		}
	}

	// ---------- eMP operations ----------
	addEmpEntry(oParameters, oRequestingOrganization) {
		const oAck = param(oParameters, "acknowledgedChronologyId");
		this.checkChronology(oAck ? oAck.valueId : undefined);
		const oMedicationParam = param(oParameters, "medication");
		const oEntryParam = param(oParameters, "empEntry");
		if (!oEntryParam || !oEntryParam.resource || oEntryParam.resource.resourceType !== "MedicationRequest") {
			throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "Parameters.parameter:empEntry with a MedicationRequest resource is required");
		}
		const oEntry = oEntryParam.resource;
		if (oEntry.intent !== "plan") {
			throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "MedicationRequest.intent must be 'plan'");
		}
		let oMedication;
		const oMedicationResource = oMedicationParam && (part(oMedicationParam, "resource") || {}).resource;
		if (oMedicationResource) {
			oMedication = clone(oMedicationResource);
			oMedication.id = uuid();
			oMedication.meta = { profile: [PROFILE.empMedication] };
			setExt(oMedication, { url: EXT.context, valueCode: "EMP" });
			oMedication.status = oMedication.status || "active";
			oMedication = this.save(oMedication);
		} else if (oEntry.medicationReference) {
			oMedication = this.resolve(oEntry.medicationReference.reference);
			if (!oMedication) {
				throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "referenced Medication not found");
			}
		} else {
			throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "medication or medicationReference is required");
		}
		const oOrganization = this.saveRequestingOrganization(oRequestingOrganization);
		const oNew = clone(oEntry);
		oNew.id = uuid();
		oNew.meta = { profile: [PROFILE.empMedicationRequest] };
		setExt(oNew, { url: EXT.context, valueCode: "EMP" });
		if (!ext(oNew, EXT.originMedication)) {
			setExt(oNew, { url: EXT.originMedication, valueReference: { reference: ref(oMedication) } });
		}
		oNew.identifier = (oNew.identifier || []).filter((i) => i.system !== SYSTEM.empIdentifier);
		oNew.identifier.push({ system: SYSTEM.empIdentifier, value: uuid() });
		oNew.status = oNew.status || "active";
		oNew.medicationReference = { reference: ref(oMedication) };
		oNew.subject = { identifier: { system: SYSTEM.kvid, value: this.kvnr } };
		oNew.authoredOn = oNew.authoredOn || today();
		if (oOrganization) {
			oNew.requester = { reference: ref(oOrganization), display: oOrganization.name };
		}
		const oSaved = this.save(oNew);
		const oWho = oOrganization ? { reference: ref(oOrganization), display: oOrganization.name } : undefined;
		const oActivity = this.activityProvenance("CREATE", [oSaved, oMedication], oWho);
		const oChronology = this.chronology(oWho);
		return parameters([
			{ name: "medicationRequest", resource: oSaved },
			{ name: "medication", resource: oMedication },
			{ name: "empChronology", resource: oChronology },
			{ name: "activityProvenance", resource: oActivity }
		]);
	}

	updateEmpEntry(oParameters, oRequestingOrganization) {
		const oAck = param(oParameters, "acknowledgedChronologyId");
		this.checkChronology(oAck ? oAck.valueId : undefined);
		const oEntryParam = param(oParameters, "empEntry");
		const oEntry = oEntryParam && oEntryParam.resource;
		if (!oEntry || oEntry.resourceType !== "MedicationRequest" || !oEntry.id) {
			throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "Parameters.parameter:empEntry with an existing MedicationRequest.id is required");
		}
		const oExisting = this.get("MedicationRequest", oEntry.id);
		if (!oExisting || contextOf(oExisting) !== "EMP") {
			throw new OperationError(404, "MEDSVC_RESOURCE_NOT_FOUND", "eMP entry " + oEntry.id + " not found");
		}
		let oMedication = oExisting.medicationReference ? this.resolve(oExisting.medicationReference.reference) : undefined;
		const oMedicationParam = param(oParameters, "medication");
		const oMedicationResource = oMedicationParam && (part(oMedicationParam, "resource") || {}).resource;
		if (oMedicationResource) {
			oMedication = clone(oMedicationResource);
			oMedication.id = uuid();
			oMedication.meta = { profile: [PROFILE.empMedication] };
			setExt(oMedication, { url: EXT.context, valueCode: "EMP" });
			oMedication = this.save(oMedication);
		}
		const oOrganization = this.saveRequestingOrganization(oRequestingOrganization);
		const oUpdated = clone(oEntry);
		oUpdated.meta = { profile: [PROFILE.empMedicationRequest] };
		oUpdated.identifier = oExisting.identifier; // eMP identifier never changes
		oUpdated.intent = "plan";
		oUpdated.subject = oExisting.subject;
		setExt(oUpdated, { url: EXT.context, valueCode: "EMP" });
		const oOrigin = ext(oExisting, EXT.originMedication);
		if (oOrigin) {
			setExt(oUpdated, oOrigin);
		}
		const oActivityExt = ext(oExisting, EXT.empActivity);
		if (oActivityExt && !ext(oUpdated, EXT.empActivity)) {
			setExt(oUpdated, oActivityExt);
		}
		if (oMedication) {
			oUpdated.medicationReference = { reference: ref(oMedication) };
		}
		if (oOrganization) {
			oUpdated.requester = { reference: ref(oOrganization), display: oOrganization.name };
		}
		const oSaved = this.save(oUpdated);
		const oWho = oOrganization ? { reference: ref(oOrganization), display: oOrganization.name } : undefined;
		const oActivity = this.activityProvenance("UPDATE", [oSaved], oWho);
		const oChronology = this.chronology(oWho);
		return parameters([
			{ name: "medicationRequest", resource: oSaved },
			{ name: "medication", resource: oMedication },
			{ name: "empChronology", resource: oChronology },
			{ name: "activityProvenance", resource: oActivity }
		]);
	}

	saveRequestingOrganization(oOrganization) {
		if (!oOrganization) {
			return undefined;
		}
		const sTelematikId = ((oOrganization.identifier || []).find((i) => i.system === SYSTEM.telematikId) || {}).value;
		const oExisting = this.all("Organization").find((o) =>
			(o.identifier || []).some((i) => i.system === SYSTEM.telematikId && i.value === sTelematikId));
		if (oExisting) {
			return oExisting;
		}
		const oNew = clone(oOrganization);
		oNew.id = uuid();
		oNew.meta = { profile: [PROFILE.tiOrganization] };
		return this.save(oNew);
	}

	medicationPlan() {
		const aEntries = this.empEntries().filter((r) => r.status === "active" || r.status === "on-hold");
		const aResources = [];
		const mSeen = {};
		const add = (r) => {
			if (r && !mSeen[ref(r)]) {
				mSeen[ref(r)] = true;
				aResources.push(r);
			}
		};
		aEntries.forEach((oEntry) => {
			add(oEntry);
			add(this.resolve((oEntry.medicationReference || {}).reference));
			const oOrigin = ext(oEntry, EXT.originMedication);
			add(oOrigin ? this.resolve(oOrigin.valueReference.reference) : undefined);
			(ext(oEntry, EXT.empActivity) ? [ext(oEntry, EXT.empActivity)] : []).forEach((e) => {
				const oStatement = this.resolve((e.valueReference || {}).reference);
				add(oStatement);
				if (oStatement) {
					(oStatement.derivedFrom || []).forEach((d) => {
						const oDerived = this.resolve(d.reference);
						add(oDerived);
						if (oDerived && oDerived.medicationReference) {
							add(this.resolve(oDerived.medicationReference.reference));
						}
					});
				}
			});
			add(this.resolve((oEntry.requester || {}).reference));
		});
		this.all("MedicationDispense").forEach((d) => {
			if ((d.authorizingPrescription || []).some((p) => mSeen[p.reference])) {
				add(d);
				add(this.resolve((d.medicationReference || {}).reference));
				(d.performer || []).forEach((p) => add(this.resolve((p.actor || {}).reference)));
			}
		});
		add(this.currentChronology());
		return bundle("searchset", aResources);
	}

	medicationPlanLog() {
		const aChronologies = this.all("Provenance")
			.filter((p) => ext(p, EXT.isChronology))
			.sort((a, b) => (a.recorded < b.recorded ? 1 : -1));
		return bundle("searchset", aChronologies);
	}

	medicationList() {
		const aResources = [];
		const mSeen = {};
		const add = (r) => {
			if (r && !mSeen[ref(r)]) {
				mSeen[ref(r)] = true;
				aResources.push(r);
			}
		};
		this.all("MedicationStatement").filter((s) => s.status !== "entered-in-error").forEach((s) => {
			add(s);
			add(this.resolve((s.medicationReference || {}).reference));
			(s.derivedFrom || []).forEach((d) => {
				const r = this.resolve(d.reference);
				add(r);
				if (r && r.medicationReference) {
					add(this.resolve(r.medicationReference.reference));
				}
				if (r && r.requester) {
					add(this.resolve(r.requester.reference));
				}
				(r && r.performer || []).forEach((p) => add(this.resolve((p.actor || {}).reference)));
			});
		});
		return bundle("searchset", aResources);
	}

	// ---------- eML <-> eMP linking ----------
	linkEmp(sStatementId, oParameters, oRequestingOrganization) {
		const oStatement = this.get("MedicationStatement", sStatementId);
		if (!oStatement) {
			throw new OperationError(404, "MEDICATIONSVC_RESOURCE_NOT_FOUND", "MedicationStatement/" + sStatementId + " not found");
		}
		const oRefParam = param(oParameters, "empEntryReference");
		const oEntry = oRefParam && oRefParam.valueReference ? this.findEmpEntryByReference([oRefParam.valueReference]) : undefined;
		if (!oEntry) {
			throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "Parameters.parameter:empEntryReference must reference an existing eMP entry");
		}
		const bLinkedElsewhere = this.empEntries().some((e) => e.id !== oEntry.id && (ext(e, EXT.empActivity) || {}).valueReference && ext(e, EXT.empActivity).valueReference.reference === ref(oStatement));
		if (bLinkedElsewhere) {
			throw new OperationError(409, "MEDICATIONSVC_EML_ALREADY_LINKED", "MedicationStatement/" + sStatementId + " is already linked to another eMP entry");
		}
		const oOrganization = this.saveRequestingOrganization(oRequestingOrganization);
		const oWho = oOrganization ? { reference: ref(oOrganization), display: oOrganization.name } : undefined;
		oStatement.basedOn = [{ reference: ref(oEntry) }];
		const oSavedStatement = this.save(oStatement);
		const oUpdated = clone(oEntry);
		setExt(oUpdated, { url: EXT.empActivity, valueReference: { reference: ref(oSavedStatement) } });
		const oSavedEntry = this.save(oUpdated);
		const oActivity = this.activityProvenance("UPDATE", [oSavedStatement, oSavedEntry], oWho);
		const oChronology = this.chronology(oWho);
		return parameters([
			{ name: "medicationStatement", resource: oSavedStatement },
			{ name: "medicationRequest", resource: oSavedEntry },
			{ name: "empChronology", resource: oChronology },
			{ name: "activityProvenance", resource: oActivity }
		]);
	}

	unlinkEmp(sStatementId, oRequestingOrganization) {
		const oStatement = this.get("MedicationStatement", sStatementId);
		if (!oStatement) {
			throw new OperationError(404, "MEDICATIONSVC_RESOURCE_NOT_FOUND", "MedicationStatement/" + sStatementId + " not found");
		}
		const oOrganization = this.saveRequestingOrganization(oRequestingOrganization);
		const oWho = oOrganization ? { reference: ref(oOrganization), display: oOrganization.name } : undefined;
		const aEntries = this.empEntries().filter((e) => (ext(e, EXT.empActivity) || { valueReference: {} }).valueReference.reference === ref(oStatement));
		delete oStatement.basedOn;
		const oSavedStatement = this.save(oStatement);
		const aTargets = [oSavedStatement];
		aEntries.forEach((oEntry) => {
			const oUpdated = clone(oEntry);
			oUpdated.extension = (oUpdated.extension || []).filter((e) => e.url !== EXT.empActivity);
			aTargets.push(this.save(oUpdated));
		});
		const oActivity = this.activityProvenance("UPDATE", aTargets, oWho);
		const oChronology = aEntries.length ? this.chronology(oWho) : this.currentChronology();
		return parameters([
			{ name: "medicationStatement", resource: oSavedStatement },
			{ name: "empChronology", resource: oChronology },
			{ name: "activityProvenance", resource: oActivity }
		]);
	}

	// ---------- E-Rezept operations ----------
	providePrescription(oParameters) {
		const aResults = (oParameters.parameter || []).filter((p) => p.name === "rxPrescription").map((oRx) => {
			const oPrescriptionId = (part(oRx, "prescriptionId") || {}).valueIdentifier;
			const sAuthoredOn = (part(oRx, "authoredOn") || {}).valueDate;
			try {
				if (!oPrescriptionId || !sAuthoredOn) {
					throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "rxPrescription.prescriptionId and authoredOn are required");
				}
				const oMedicationIn = (part(oRx, "medication") || {}).resource;
				const oRequestIn = (part(oRx, "medicationRequest") || {}).resource;
				const oOrganizationIn = (part(oRx, "organization") || {}).resource;
				const oPractitionerIn = (part(oRx, "practitioner") || {}).resource;
				if (!oMedicationIn || !oRequestIn) {
					throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "rxPrescription.medication and medicationRequest are required");
				}
				const bDuplicate = this.all("MedicationRequest").some((r) =>
					(r.identifier || []).some((i) => i.system === SYSTEM.prescriptionId && i.value === oPrescriptionId.value));
				if (bDuplicate) {
					throw new OperationError(409, "MEDICATIONSVC_PRESCRIPTION_DUPLICATE", "prescription " + oPrescriptionId.value + " already exists");
				}
				const oOrganization = oOrganizationIn ? this.save(Object.assign(clone(oOrganizationIn), { id: uuid(), meta: { profile: [PROFILE.organizationDirectory] } })) : undefined;
				const oPractitioner = oPractitionerIn ? this.save(Object.assign(clone(oPractitionerIn), { id: uuid(), meta: { profile: [PROFILE.practitionerDirectory] } })) : undefined;
				if (oPractitioner && oOrganization) {
					this.save({
						resourceType: "PractitionerRole",
						practitioner: { reference: ref(oPractitioner) },
						organization: { reference: ref(oOrganization) }
					});
				}
				const oMedication = clone(oMedicationIn);
				oMedication.id = uuid();
				oMedication.meta = { profile: [PROFILE.epaMedication] };
				oMedication.status = "inactive"; // not yet dispensed
				const oSavedMedication = this.save(oMedication);

				const oRequest = clone(oRequestIn);
				oRequest.id = uuid();
				oRequest.meta = { profile: [PROFILE.epaMedicationRequest] };
				oRequest.identifier = (oRequest.identifier || []).filter((i) => i.system !== SYSTEM.prescriptionId);
				oRequest.identifier.push({ system: SYSTEM.prescriptionId, value: oPrescriptionId.value });
				oRequest.status = "active";
				oRequest.intent = "order";
				oRequest.medicationReference = { reference: ref(oSavedMedication) };
				oRequest.subject = { identifier: { system: SYSTEM.kvid, value: this.kvnr } };
				oRequest.authoredOn = sAuthoredOn;
				if (oPractitioner) {
					oRequest.requester = { reference: ref(oPractitioner), display: displayName(oPractitioner) };
				}
				// optional logical reference to the eMP entry via the eMP identifier
				const oEmpEntry = this.findEmpEntryByReference(oRequest.basedOn);
				if (oEmpEntry) {
					oRequest.basedOn = [{ reference: ref(oEmpEntry), identifier: empIdentifier(oEmpEntry) }];
				}
				const oSavedRequest = this.save(oRequest);

				const oStatement = {
					resourceType: "MedicationStatement",
					meta: { profile: [PROFILE.epaMedicationStatement] },
					extension: [{ url: EXT.context, valueCode: "ERP" }],
					status: "intended",
					medicationReference: { reference: ref(oSavedMedication) },
					subject: { identifier: { system: SYSTEM.kvid, value: this.kvnr } },
					effectivePeriod: { start: sAuthoredOn },
					dateAsserted: sAuthoredOn,
					derivedFrom: [{ reference: ref(oSavedRequest) }],
					dosage: clone(oRequest.dosageInstruction || [])
				};
				const oRendered = ext(oRequest, EXT.renderedDosageInstructionMR);
				if (oRendered) {
					oStatement.extension.push({ url: EXT.renderedDosageInstructionMS, valueMarkdown: oRendered.valueMarkdown });
				}
				if (oEmpEntry) {
					oStatement.basedOn = [{ reference: ref(oEmpEntry) }];
				}
				const oSavedStatement = this.save(oStatement);
				const aTargets = [oSavedMedication, oSavedRequest, oSavedStatement];
				if (oEmpEntry) {
					const oLinked = clone(oEmpEntry);
					setExt(oLinked, { url: EXT.empActivity, valueReference: { reference: ref(oSavedStatement) } });
					const oSavedEntry = this.save(oLinked);
					aTargets.push(oSavedEntry);
					this.activityProvenance("CREATE", aTargets);
					this.chronology();
				} else {
					this.activityProvenance("CREATE", aTargets);
				}
				return {
					name: "rxPrescription", part: [
						{ name: "prescriptionId", valueIdentifier: oPrescriptionId },
						{ name: "authoredOn", valueDate: sAuthoredOn },
						{ name: "operationOutcome", resource: operationOutcome("information", "informational", "MEDICATIONSVC_OPERATION_SUCCESS", "Operation was successful") }
					]
				};
			} catch (e) {
				return {
					name: "rxPrescription", part: [
						{ name: "prescriptionId", valueIdentifier: oPrescriptionId },
						{ name: "authoredOn", valueDate: sAuthoredOn },
						{ name: "operationOutcome", resource: operationOutcome("error", "processing", e.code || "MEDICATIONSVC_OPERATION_FAILED", e.message) }
					]
				};
			}
		});
		return parameters(aResults);
	}

	findEmpEntryByReference(aBasedOn) {
		for (const oBasedOn of (aBasedOn || [])) {
			if (oBasedOn.identifier && oBasedOn.identifier.system === SYSTEM.empIdentifier) {
				const oEntry = this.empEntries().find((r) => (r.identifier || []).some((i) => i.system === SYSTEM.empIdentifier && i.value === oBasedOn.identifier.value));
				if (oEntry) {
					return oEntry;
				}
			}
			if (oBasedOn.reference) {
				const oEntry = this.resolve(oBasedOn.reference);
				if (oEntry && oEntry.resourceType === "MedicationRequest" && contextOf(oEntry) === "EMP") {
					return oEntry;
				}
			}
		}
		return undefined;
	}

	provideDispensation(oParameters) {
		const aResults = (oParameters.parameter || []).filter((p) => p.name === "rxDispensation").map((oRx) => {
			const oPrescriptionId = (part(oRx, "prescriptionId") || {}).valueIdentifier;
			const sAuthoredOn = (part(oRx, "authoredOn") || {}).valueDate;
			try {
				if (!oPrescriptionId || !sAuthoredOn) {
					throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "rxDispensation.prescriptionId and authoredOn are required");
				}
				const oPrescription = this.all("MedicationRequest").find((r) =>
					(r.identifier || []).some((i) => i.system === SYSTEM.prescriptionId && i.value === oPrescriptionId.value));
				if (!oPrescription) {
					throw new OperationError(404, "MEDICATIONSVC_PRESCRIPTION_NO_EXIST", "prescription " + oPrescriptionId.value + " not found");
				}
				const oDispenseIn = (part(oRx, "medicationDispense") || {}).resource;
				const oMedicationIn = (part(oRx, "medication") || {}).resource;
				const oOrganizationIn = (part(oRx, "organization") || {}).resource;
				if (!oDispenseIn || !oMedicationIn) {
					throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "rxDispensation.medicationDispense and medication are required");
				}
				const oPharmacy = oOrganizationIn ? this.save(Object.assign(clone(oOrganizationIn), { id: uuid(), meta: { profile: [PROFILE.organizationDirectory] } })) : undefined;
				const oPrescribedMedication = this.resolve(oPrescription.medicationReference.reference);
				const oMedication = clone(oMedicationIn);
				oMedication.id = uuid();
				oMedication.meta = { profile: [PROFILE.epaMedication] };
				oMedication.status = "active"; // available to the patient
				const oSavedMedication = this.save(oMedication);
				// the prescribed medication becomes active as well
				oPrescribedMedication.status = "active";
				this.save(oPrescribedMedication);

				const oDispense = clone(oDispenseIn);
				oDispense.id = uuid();
				oDispense.meta = { profile: [PROFILE.epaMedicationDispense] };
				oDispense.identifier = [{ system: SYSTEM.prescriptionId, value: oPrescriptionId.value }];
				oDispense.status = "completed";
				oDispense.medicationReference = { reference: ref(oSavedMedication) };
				oDispense.subject = { identifier: { system: SYSTEM.kvid, value: this.kvnr } };
				oDispense.authorizingPrescription = [{ reference: ref(oPrescription) }];
				if (oPharmacy) {
					oDispense.performer = [{ actor: { reference: ref(oPharmacy), display: oPharmacy.name } }];
				}
				oDispense.whenHandedOver = oDispense.whenHandedOver || sAuthoredOn;
				const oSavedDispense = this.save(oDispense);

				const oStatement = this.all("MedicationStatement").find((s) => (s.derivedFrom || []).some((d) => d.reference === ref(oPrescription)));
				const aTargets = [oSavedMedication, oSavedDispense];
				const bSubstitution = pznOf(oSavedMedication) && pznOf(oPrescribedMedication) && pznOf(oSavedMedication) !== pznOf(oPrescribedMedication);
				if (oStatement) {
					oStatement.status = "unknown";
					oStatement.derivedFrom = (oStatement.derivedFrom || []).concat([{ reference: ref(oSavedDispense) }]);
					if (bSubstitution) {
						oStatement.medicationReference = { reference: ref(oSavedMedication) };
						if (oDispense.dosageInstruction && oDispense.dosageInstruction.length) {
							oStatement.dosage = clone(oDispense.dosageInstruction);
							const sText = (oDispense.dosageInstruction.find((d) => d.text) || {}).text;
							if (sText) {
								setExt(oStatement, { url: EXT.renderedDosageInstructionMS, valueMarkdown: sText });
							}
						}
					}
					aTargets.push(this.save(oStatement));
				}
				// automatic eMP linking: dispensed medication becomes medicationReference of the linked eMP entry
				const oEmpEntry = this.findEmpEntryByReference(oPrescription.basedOn);
				let bEmpChanged = false;
				if (oEmpEntry) {
					const oUpdated = clone(oEmpEntry);
					if (formOf(oSavedMedication) !== "KPG") {
						if (!ext(oUpdated, EXT.originMedication)) {
							setExt(oUpdated, { url: EXT.originMedication, valueReference: oUpdated.medicationReference });
						}
						oUpdated.medicationReference = { reference: ref(oSavedMedication) };
						bEmpChanged = true;
					}
					if (bSubstitution && oDispense.dosageInstruction && oDispense.dosageInstruction.length) {
						oUpdated.dosageInstruction = clone(oDispense.dosageInstruction);
						const sText = (oDispense.dosageInstruction.find((d) => d.text) || {}).text;
						if (sText) {
							setExt(oUpdated, { url: EXT.renderedDosageInstructionMR, valueMarkdown: sText });
						}
						bEmpChanged = true;
					}
					if (bEmpChanged) {
						aTargets.push(this.save(oUpdated));
					}
				}
				this.activityProvenance("CREATE", aTargets);
				if (bEmpChanged) {
					this.chronology();
				}
				return {
					name: "rxDispensation", part: [
						{ name: "prescriptionId", valueIdentifier: oPrescriptionId },
						{ name: "authoredOn", valueDate: sAuthoredOn },
						{ name: "operationOutcome", resource: operationOutcome("information", "informational", "MEDICATIONSVC_OPERATION_SUCCESS", "Operation was successful") }
					]
				};
			} catch (e) {
				return {
					name: "rxDispensation", part: [
						{ name: "prescriptionId", valueIdentifier: oPrescriptionId },
						{ name: "authoredOn", valueDate: sAuthoredOn },
						{ name: "operationOutcome", resource: operationOutcome("error", "processing", e.code || "MEDICATIONSVC_OPERATION_FAILED", e.message) }
					]
				};
			}
		});
		return parameters(aResults);
	}

	// ---------- seed data ----------
	seed() {
		// One eMP entry created by a practice (Benazepril) ...
		const oPractice = {
			resourceType: "Organization",
			identifier: [{ system: SYSTEM.telematikId, value: "1-SMC-B-Testkarte-883110000141773" }],
			name: "Praxis Dr. med. Hannelore Popówitsch"
		};
		this.addEmpEntry(parameters([
			{ name: "medication", part: [{ name: "resource", resource: sampleMedication("04351736", "Benazepril AL 20mg 98 Filmtabletten N3", "FTA", "N3", "Benazepril hydrochlorid", 20, "mg") }] },
			{ name: "empEntry", resource: sampleEmpEntry(this.kvnr, "1-0-1-0 Stück", { frequency: 2, when: ["MORN", "EVE"], dose: 1 }, "Bluthochdruck", "I10.00", "Benazepril kann anfangs Schwindel verursachen - daher zuerst in Ruhe einnehmen.", "Blutdruck regelmäßig kontrollieren", "2025-11-03") }
		]), oPractice);
		// ... one that has been prescribed and dispensed already (Metformin) ...
		const oMetformin = this.addEmpEntry(parameters([
			{ name: "acknowledgedChronologyId", valueId: this.chronologyId },
			{ name: "medication", part: [{ name: "resource", resource: sampleMedication("00893891", "Metformin AbZ 1000 mg 120 Filmtabletten N3", "FTA", "N3", "Metformin hydrochlorid", 1000, "mg") }] },
			{ name: "empEntry", resource: sampleEmpEntry(this.kvnr, "1-0-1-0 Stück", { frequency: 2, when: ["MORN", "EVE"], dose: 1 }, "Diabetes mellitus Typ 2", "E11.90", "Zu den Mahlzeiten einnehmen.", undefined, "2025-06-12") }
		]), oPractice);
		const oMetforminEntry = param(oMetformin, "medicationRequest").resource;
		const sPrescriptionId = "160.000.000.000.001.11";
		this.providePrescription(parameters([{
			name: "rxPrescription", part: [
				{ name: "prescriptionId", valueIdentifier: { system: SYSTEM.prescriptionId, value: sPrescriptionId } },
				{ name: "authoredOn", valueDate: "2025-06-12" },
				{ name: "medicationRequest", resource: Object.assign(samplePrescriptionRequest(this.kvnr, "1-0-1-0 Stück", { frequency: 2, when: ["MORN", "EVE"], dose: 1 }, 1), {
					basedOn: [{ identifier: empIdentifier(oMetforminEntry) }]
				}) },
				{ name: "medication", resource: sampleMedication("00893891", "Metformin AbZ 1000 mg 120 Filmtabletten N3", "FTA", "N3", "Metformin hydrochlorid", 1000, "mg") },
				{ name: "organization", resource: { resourceType: "Organization", identifier: [{ system: SYSTEM.telematikId, value: "1-SMC-B-Testkarte-883110000141773" }], name: "Praxis Dr. med. Hannelore Popówitsch" } },
				{ name: "practitioner", resource: { resourceType: "Practitioner", identifier: [{ system: SYSTEM.lanr, value: "123456789" }], name: [{ family: "Popówitsch", given: ["Hannelore"], prefix: ["Dr. med."] }] } }
			]
		}]));
		this.provideDispensation(parameters([{
			name: "rxDispensation", part: [
				{ name: "prescriptionId", valueIdentifier: { system: SYSTEM.prescriptionId, value: sPrescriptionId } },
				{ name: "authoredOn", valueDate: "2025-06-13" },
				{ name: "medicationDispense", resource: { resourceType: "MedicationDispense", status: "completed", quantity: { value: 1, unit: "Packung" }, whenHandedOver: "2025-06-13" } },
				{ name: "medication", resource: sampleMedication("01067927", "Metformin-ratiopharm 1000 mg 120 Filmtabletten N3", "FTA", "N3", "Metformin hydrochlorid", 1000, "mg") },
				{ name: "organization", resource: { resourceType: "Organization", identifier: [{ system: SYSTEM.telematikId, value: "3-SMC-B-Testkarte-883110000116873" }], name: "Adler-Apotheke" } }
			]
		}]));
		// ... and one eML entry (prescription) that is not linked to the eMP (Ibuprofen)
		this.providePrescription(parameters([{
			name: "rxPrescription", part: [
				{ name: "prescriptionId", valueIdentifier: { system: SYSTEM.prescriptionId, value: "160.000.000.000.002.08" } },
				{ name: "authoredOn", valueDate: "2026-01-20" },
				{ name: "medicationRequest", resource: samplePrescriptionRequest(this.kvnr, "1-1-1-0 Stück bei Bedarf", { frequency: 3, when: ["MORN", "NOON", "EVE"], dose: 1 }, 1) },
				{ name: "medication", resource: sampleMedication("02013219", "Ibuflam akut 400 mg 20 Filmtabletten N1", "FTA", "N1", "Ibuprofen", 400, "mg") },
				{ name: "organization", resource: { resourceType: "Organization", identifier: [{ system: SYSTEM.telematikId, value: "1-SMC-B-Testkarte-883110000141773" }], name: "Praxis Dr. med. Hannelore Popówitsch" } },
				{ name: "practitioner", resource: { resourceType: "Practitioner", identifier: [{ system: SYSTEM.lanr, value: "123456789" }], name: [{ family: "Popówitsch", given: ["Hannelore"], prefix: ["Dr. med."] }] } }
			]
		}]));
	}
}

// ---------- helpers for FHIR structures ----------
function param(oParameters, sName) {
	return (oParameters.parameter || []).find((p) => p.name === sName);
}
function part(oParameter, sName) {
	return (oParameter.part || []).find((p) => p.name === sName);
}
function parameters(aParameter) {
	return { resourceType: "Parameters", parameter: aParameter.filter((p) => p && (p.resource || p.part || Object.keys(p).some((k) => k.startsWith("value")))) };
}
function bundle(sType, aResources) {
	return {
		resourceType: "Bundle",
		id: uuid(),
		type: sType,
		timestamp: now(),
		total: aResources.length,
		entry: aResources.map((r) => ({ fullUrl: "urn:uuid:" + r.id, resource: r, search: { mode: "match" } }))
	};
}
function empIdentifier(oEntry) {
	return (oEntry.identifier || []).find((i) => i.system === SYSTEM.empIdentifier);
}
function displayName(oPractitioner) {
	const n = (oPractitioner.name || [])[0] || {};
	return [(n.prefix || []).join(" "), (n.given || []).join(" "), n.family].filter(Boolean).join(" ");
}
function dosage(sText, oTiming) {
	const oDosage = { text: sText };
	if (oTiming) {
		oDosage.timing = { repeat: { frequency: oTiming.frequency, period: 1, periodUnit: "d", when: oTiming.when } };
		oDosage.doseAndRate = [{ doseQuantity: { value: oTiming.dose, unit: "Stück", system: "https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_BMP_DOSIEREINHEIT", code: "1" } }];
	}
	return oDosage;
}
function sampleMedication(sPzn, sName, sForm, sNormgroesse, sIngredient, iStrength, sUnit) {
	return {
		resourceType: "Medication",
		extension: [
			{ url: EXT.medicationType, valueCoding: { system: "http://snomed.info/sct", code: "763158003", display: "Medicinal product (product)" } },
			{ url: EXT.drugCategory, valueCoding: { system: BASE + "/CodeSystem/epa-drug-category-cs", code: "00" } },
			{ url: EXT.normgroesse, valueCode: sNormgroesse },
			{ url: EXT.vaccine, valueBoolean: false }
		],
		code: { coding: [{ system: SYSTEM.pzn, code: sPzn, display: sName }], text: sName },
		status: "active",
		form: { coding: [{ system: SYSTEM.darreichungsform, code: sForm }] },
		ingredient: sIngredient ? [{
			itemCodeableConcept: { text: sIngredient },
			strength: { numerator: { value: iStrength, unit: sUnit }, denominator: { value: 1, unit: "Stück" } }
		}] : undefined
	};
}
function sampleEmpEntry(sKvnr, sDosageText, oTiming, sReason, sIcd, sPatientNote, sNote, sStart) {
	return {
		resourceType: "MedicationRequest",
		extension: [
			{ url: EXT.context, valueCode: "EMP" },
			{ url: EXT.reasonPatientInstruction, valueString: sReason },
			{ url: EXT.patientNote, valueAnnotation: { text: sPatientNote } },
			{ url: EXT.effectiveDosePeriod, valuePeriod: { start: sStart } },
			{ url: EXT.renderedDosageInstructionMR, valueMarkdown: sDosageText }
		],
		status: "active",
		intent: "plan",
		subject: { identifier: { system: SYSTEM.kvid, value: sKvnr } },
		authoredOn: sStart,
		reasonCode: [{ coding: [{ system: SYSTEM.icd10, code: sIcd, display: sReason }], text: sReason }],
		note: sNote ? [{ text: sNote }] : undefined,
		dosageInstruction: [dosage(sDosageText, oTiming)]
	};
}
function samplePrescriptionRequest(sKvnr, sDosageText, oTiming, iQuantity) {
	return {
		resourceType: "MedicationRequest",
		extension: [{ url: EXT.renderedDosageInstructionMR, valueMarkdown: sDosageText }],
		status: "active",
		intent: "order",
		subject: { identifier: { system: SYSTEM.kvid, value: sKvnr } },
		dosageInstruction: [dosage(sDosageText, oTiming)],
		dispenseRequest: { quantity: { value: iQuantity, unit: "Packung" } }
	};
}

/**
 * The mock service: holds one Record per KVNR and dispatches requests.
 */
class MedicationServiceMock {
	constructor() {
		this.records = {};
	}
	record(sKvnr) {
		if (!/^[A-Z]\d{9}$/.test(sKvnr || "")) {
			throw new OperationError(400, "MEDICATIONSVC_INVALID_INSURANT", "x-insurantid (KVNR) is missing or invalid: " + sKvnr);
		}
		this.records[sKvnr] = this.records[sKvnr] || new Record(sKvnr);
		return this.records[sKvnr];
	}

	/**
	 * @param {string} sMethod HTTP method
	 * @param {string} sPath path relative to the FHIR base, e.g. "$medication-plan" or "MedicationRequest/123"
	 * @param {object} mQuery query parameters
	 * @param {object} mHeaders lower-cased request headers
	 * @param {object} [oBody] parsed JSON body
	 * @returns {{status: number, body: object}}
	 */
	handle(sMethod, sPath, mQuery, mHeaders, oBody) {
		try {
			sPath = sPath.replace(/^\/+/, "").replace(/\/+$/, "");
			if (sMethod === "GET" && sPath === "metadata") {
				return { status: 200, body: capabilityStatement() };
			}
			const sKvnr = mHeaders["x-insurantid"] || mQuery["x-insurantid"];
			const oRecord = this.record(sKvnr);
			let oRequestingOrganization;
			if (mHeaders["x-requesting-organization"]) {
				try {
					oRequestingOrganization = JSON.parse(Buffer.from(mHeaders["x-requesting-organization"], "base64").toString("utf8"));
				} catch (e) {
					throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "X-Requesting-Organization is not a base64 encoded FHIR Organization");
				}
			}
			const bWrite = ["$add-emp-entry", "$update-emp-entry"].includes(sPath) || /\$(link|unlink)-emp$/.test(sPath);
			if (bWrite && !oRequestingOrganization) {
				throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "X-Requesting-Organization header is required for " + sPath);
			}
			if (sMethod === "GET") {
				switch (sPath) {
					case "$medication-plan": return { status: 200, body: oRecord.medicationPlan() };
					case "$medication-plan-log": return { status: 200, body: oRecord.medicationPlanLog() };
					case "$medication-list": return { status: 200, body: oRecord.medicationList() };
					default: return this.query(oRecord, sPath, mQuery);
				}
			}
			if (sMethod === "POST") {
				if (!oBody || oBody.resourceType !== "Parameters") {
					throw new OperationError(400, "MEDSVC_NO_VALID_STRUCTURE", "request body must be a FHIR Parameters resource");
				}
				const mLink = /^MedicationStatement\/([^/]+)\/\$(link|unlink)-emp$/.exec(sPath);
				if (mLink) {
					return { status: 200, body: mLink[2] === "link" ? oRecord.linkEmp(mLink[1], oBody, oRequestingOrganization) : oRecord.unlinkEmp(mLink[1], oRequestingOrganization) };
				}
				switch (sPath) {
					case "$add-emp-entry": return { status: 200, body: oRecord.addEmpEntry(oBody, oRequestingOrganization) };
					case "$update-emp-entry": return { status: 200, body: oRecord.updateEmpEntry(oBody, oRequestingOrganization) };
					case "$provide-prescription-erp": return { status: 200, body: oRecord.providePrescription(oBody) };
					case "$provide-dispensation-erp": return { status: 200, body: oRecord.provideDispensation(oBody) };
					default: throw new OperationError(404, "MEDICATIONSVC_OPERATION_UNKNOWN", "unknown operation " + sPath);
				}
			}
			throw new OperationError(405, "MEDICATIONSVC_METHOD_NOT_ALLOWED", sMethod + " not allowed");
		} catch (e) {
			if (e instanceof OperationError) {
				return { status: e.status, body: operationOutcome("error", e.status === 404 ? "not-found" : "processing", e.code, e.message) };
			}
			return { status: 500, body: operationOutcome("fatal", "exception", "MEDICATIONSVC_INTERNAL_ERROR", e.stack || String(e)) };
		}
	}

	query(oRecord, sPath, mQuery) {
		const aSegments = sPath.split("/");
		const sType = aSegments[0];
		if (!/^[A-Z][A-Za-z]+$/.test(sType)) {
			throw new OperationError(404, "MEDICATIONSVC_RESOURCE_NOT_FOUND", "unknown path " + sPath);
		}
		if (aSegments.length === 1) {
			let aResources = oRecord.all(sType);
			if (mQuery._id) {
				aResources = aResources.filter((r) => r.id === mQuery._id);
			}
			if (mQuery.status) {
				aResources = aResources.filter((r) => r.status === mQuery.status);
			}
			if (mQuery.identifier) {
				const sValue = mQuery.identifier.split("|").pop();
				aResources = aResources.filter((r) => (r.identifier || []).some((i) => i.value === sValue));
			}
			return { status: 200, body: bundle("searchset", aResources) };
		}
		if (aSegments[1] === "_history") {
			const aAll = [].concat(...Object.keys(oRecord.store[sType] || {}).map((id) => oRecord.history(sType, id)));
			return { status: 200, body: bundle("history", aAll) };
		}
		const sId = aSegments[1];
		if (aSegments[2] === "_history") {
			if (aSegments[3]) {
				const oVersion = oRecord.get(sType, sId, aSegments[3]);
				if (!oVersion) {
					throw new OperationError(404, "MEDICATIONSVC_RESOURCE_NOT_FOUND", sPath + " not found");
				}
				return { status: 200, body: oVersion };
			}
			return { status: 200, body: bundle("history", oRecord.history(sType, sId)) };
		}
		const oResource = oRecord.get(sType, sId);
		if (!oResource) {
			throw new OperationError(404, "MEDICATIONSVC_RESOURCE_NOT_FOUND", sPath + " not found");
		}
		return { status: 200, body: oResource };
	}
}

function capabilityStatement() {
	return {
		resourceType: "CapabilityStatement",
		status: "active",
		date: today(),
		kind: "instance",
		software: { name: "epa4all frontend mock of ePA Medication Service", version: "1.3.1-mock" },
		fhirVersion: "4.0.1",
		format: ["application/fhir+json"],
		rest: [{
			mode: "server",
			resource: ["Medication", "MedicationRequest", "MedicationDispense", "MedicationStatement", "Organization", "Practitioner", "PractitionerRole", "Provenance"].map((t) => ({
				type: t, interaction: [{ code: "read" }, { code: "vread" }, { code: "search-type" }, { code: "history-instance" }, { code: "history-type" }]
			})),
			operation: ["medication-plan", "medication-plan-log", "medication-list", "add-emp-entry", "update-emp-entry", "link-emp", "unlink-emp", "provide-prescription-erp", "provide-dispensation-erp"].map((n) => ({
				name: n, definition: BASE + "/OperationDefinition/epa-op-" + n
			}))
		}]
	};
}

module.exports = { MedicationServiceMock, PROFILE, EXT, SYSTEM };
