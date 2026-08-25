sap.ui.define([
	"medunited/base/controller/AbstractController",
	"sap/ui/model/json/JSONModel",
	"sap/ui/core/Fragment",
	"sap/m/MessageBox",
	"sap/m/MessageToast",
	"sap/ui/core/format/DateFormat",
	"sap/m/SelectDialog",
	"sap/m/StandardListItem",
	"sap/ui/model/Filter",
	"sap/ui/model/FilterOperator",
	"medunited/care/utils/MedicationServiceClient"
], function (AbstractController, JSONModel, Fragment, MessageBox, MessageToast, DateFormat, SelectDialog, StandardListItem, Filter, FilterOperator, MedicationServiceClient) {
	"use strict";

	const EXT = MedicationServiceClient.EXT;
	const SYSTEM = MedicationServiceClient.SYSTEM;
	const PROFILE = MedicationServiceClient.PROFILE;
	const ext = MedicationServiceClient.ext;

	const FORMS = [
		{ key: "", text: "" },
		{ key: "TAB", text: "Tablette (TAB)" },
		{ key: "FTA", text: "Filmtablette (FTA)" },
		{ key: "KAP", text: "Kapsel (KAP)" },
		{ key: "HKP", text: "Hartkapsel (HKP)" },
		{ key: "RET", text: "Retardtablette (RET)" },
		{ key: "TRO", text: "Tropfen (TRO)" },
		{ key: "LOE", text: "Lösung (LOE)" },
		{ key: "SAF", text: "Saft (SAF)" },
		{ key: "INJ", text: "Injektionslösung (INJ)" },
		{ key: "SAL", text: "Salbe (SAL)" },
		{ key: "CRE", text: "Creme (CRE)" },
		{ key: "PFT", text: "Pflaster transdermal (PFT)" },
		{ key: "SUP", text: "Zäpfchen (SUP)" },
		{ key: "DFL", text: "Durchstechflasche (DFL)" },
		{ key: "KPG", text: "Kombipackung (KPG)" }
	];
	const DOSE_UNITS = [
		{ key: "1", text: "Stück" },
		{ key: "p", text: "Tropfen" },
		{ key: "#", text: "Messlöffel" },
		{ key: "h", text: "Hub" },
		{ key: "l", text: "ml" },
		{ key: "m", text: "mg" },
		{ key: "e", text: "Beutel" },
		{ key: "b", text: "Ampulle" }
	];
	const FORM_TEXT = FORMS.reduce((m, f) => { m[f.key] = f.text; return m; }, {});
	const WHEN = [
		{ prop: "morning", code: "MORN" },
		{ prop: "noon", code: "NOON" },
		{ prop: "evening", code: "EVE" },
		{ prop: "night", code: "NIGHT" }
	];

	return AbstractController.extend("medunited.care.controller.patient.viewer.MedicationPlan", {

		onInit: function () {
			this.oDateFormat = DateFormat.getDateInstance({ style: "medium" });
			this.oDateTimeFormat = DateFormat.getDateTimeInstance({ style: "medium" });
			this.getView().setModel(new JSONModel({
				busy: false,
				patient: { kvnr: "", name: "" },
				chronology: {},
				counts: { active: 0, onHold: 0, unlinkedEml: 0 },
				entries: [],
				selected: null,
				eml: [],
				log: []
			}), "emp");
			this.getView().setModel(new JSONModel({ busy: false, forms: FORMS, doseUnits: DOSE_UNITS, entry: {} }), "dlg");
		},

		/**
		 * Called by the patient detail controller after the view was created in the end column.
		 * @param {object} oContext {kvnr, modelPath (WebDAV node of the patient), patient (index in the master list), document (route parameter)}
		 */
		setContext: function (oContext) {
			this._oContext = oContext;
			const sServiceUrl = this.getOwnerComponent().getManifestEntry("/sap.app/dataSources/fhir/uri");
			this._oClient = new MedicationServiceClient(sServiceUrl, oContext.kvnr);
			this.getView().getModel("emp").setProperty("/patient", { kvnr: oContext.kvnr });
			if (oContext.modelPath) {
				// patient master data (VSD) from the WebDAV model
				this.getView().bindElement(oContext.modelPath);
			}
			this.load();
		},

		// ---------- loading ----------
		load: function () {
			const oModel = this.getView().getModel("emp");
			oModel.setProperty("/busy", true);
			const sSelectedId = oModel.getProperty("/selected/id");
			return Promise.all([
				this._oClient.getMedicationPlan(),
				this._oClient.getMedicationList().catch(() => ({ entry: [] })),
				this._oClient.getMedicationPlanLog().catch(() => ({ entry: [] }))
			]).then(([oPlan, oList, oLog]) => {
				const oData = this._buildViewData(oPlan, oList, oLog);
				oData.busy = false;
				oData.patient = oModel.getProperty("/patient");
				oData.selected = oData.entries.find((e) => e.id === sSelectedId) || null;
				oModel.setData(oData);
				const oTable = this.byId("entriesTable");
				if (oData.selected && oTable) {
					const oItem = oTable.getItems()[oData.entries.indexOf(oData.selected)];
					if (oItem) {
						oTable.setSelectedItem(oItem);
					}
				}
			}).catch((oError) => {
				oModel.setProperty("/busy", false);
				MessageBox.error(this.translate("empErrorLoad") + "\n" + oError.message);
			});
		},

		/**
		 * Turns the $medication-plan / $medication-list / $medication-plan-log bundles into a flat view model.
		 */
		_buildViewData: function (oPlan, oList, oLog) {
			const mResources = {};
			const index = (oBundle) => (oBundle && oBundle.entry || []).forEach((e) => {
				if (e.resource && e.resource.id) {
					mResources[e.resource.resourceType + "/" + e.resource.id] = e.resource;
				}
			});
			index(oList);
			index(oPlan); // plan wins (latest versions of eMP entries)
			const resolve = (oRef) => {
				if (!oRef || !oRef.reference) {
					return undefined;
				}
				return mResources[oRef.reference.replace(/\/_history\/.*$/, "")];
			};
			const all = (sType) => Object.values(mResources).filter((r) => r.resourceType === sType);

			const medicationInfo = (oMedication) => {
				if (!oMedication) {
					return { name: "", details: "" };
				}
				const oCode = oMedication.code || {};
				const oPzn = (oCode.coding || []).find((c) => c.system === SYSTEM.pzn);
				const sForm = ((oMedication.form || {}).coding || [])[0] ? oMedication.form.coding[0].code : "";
				const oIngredient = (oMedication.ingredient || [])[0];
				let sIngredient = "";
				if (oIngredient) {
					const oStrength = oIngredient.strength && oIngredient.strength.numerator;
					sIngredient = ((oIngredient.itemCodeableConcept || {}).text || "") + (oStrength ? " " + oStrength.value + " " + (oStrength.unit || "") : "");
				}
				const oNorm = ext(oMedication, EXT.normgroesse);
				return {
					id: oMedication.id,
					name: oCode.text || (oPzn && oPzn.display) || ((oCode.coding || [])[0] || {}).display || "",
					pzn: oPzn ? oPzn.code : "",
					form: sForm,
					formText: FORM_TEXT[sForm] || sForm,
					normgroesse: oNorm ? oNorm.valueCode : "",
					ingredient: sIngredient,
					details: [oPzn ? "PZN " + oPzn.code : "", FORM_TEXT[sForm] || sForm, sIngredient].filter(Boolean).join(" · ")
				};
			};
			const dosageText = (oResource, sExtUrl, sProperty) => {
				const oRendered = ext(oResource, sExtUrl);
				if (oRendered && oRendered.valueMarkdown) {
					return oRendered.valueMarkdown;
				}
				const aDosage = oResource[sProperty] || [];
				const oWithText = aDosage.find((d) => d.text);
				return oWithText ? oWithText.text : this._dosageFromTiming(aDosage);
			};

			const aDispenses = all("MedicationDispense");
			const aStatements = all("MedicationStatement");

			const aEntries = all("MedicationRequest")
				.filter((r) => r.intent === "plan" && (ext(r, EXT.context) || {}).valueCode === "EMP")
				.sort((a, b) => (a.authoredOn || "") < (b.authoredOn || "") ? 1 : -1)
				.map((oEntry) => {
					const oMedication = resolve(oEntry.medicationReference);
					const oOriginExt = ext(oEntry, EXT.originMedication);
					const oOrigin = oOriginExt ? resolve(oOriginExt.valueReference) : undefined;
					const oPeriodExt = ext(oEntry, EXT.effectiveDosePeriod);
					const oReason = (oEntry.reasonCode || [])[0];
					const oIcd = oReason && (oReason.coding || []).find((c) => c.system === SYSTEM.icd10);
					const oReasonExt = ext(oEntry, EXT.reasonPatientInstruction);
					const oPatientNote = ext(oEntry, EXT.patientNote);
					const oEmpIdentifier = (oEntry.identifier || []).find((i) => i.system === SYSTEM.empIdentifier);

					// linked eML entry (MedicationStatement) -> prescription -> dispense
					const oActivity = ext(oEntry, EXT.empActivity);
					let oStatement = oActivity ? resolve(oActivity.valueReference) : undefined;
					if (!oStatement) {
						oStatement = aStatements.find((s) => (s.basedOn || []).some((b) => b.reference === "MedicationRequest/" + oEntry.id));
					}
					let oPrescription, oDispense;
					if (oStatement) {
						(oStatement.derivedFrom || []).forEach((d) => {
							const r = resolve(d);
							if (r && r.resourceType === "MedicationRequest") {
								oPrescription = r;
							} else if (r && r.resourceType === "MedicationDispense") {
								oDispense = r;
							}
						});
					}
					if (!oPrescription) {
						oPrescription = all("MedicationRequest").find((r) => r.intent === "order" && (r.basedOn || []).some((b) =>
							b.reference === "MedicationRequest/" + oEntry.id || (b.identifier && oEmpIdentifier && b.identifier.value === oEmpIdentifier.value)));
					}
					if (oPrescription && !oDispense) {
						oDispense = aDispenses.find((d) => (d.authorizingPrescription || []).some((p) => p.reference === "MedicationRequest/" + oPrescription.id));
					}
					const oPrescriptionId = oPrescription && (oPrescription.identifier || []).find((i) => i.system === SYSTEM.prescriptionId);
					const oPrescribedMedication = oPrescription && resolve(oPrescription.medicationReference);
					const oDispensedMedication = oDispense && resolve(oDispense.medicationReference);
					const oMedicationInfo = medicationInfo(oMedication);
					const oPrescribedInfo = medicationInfo(oPrescribedMedication);
					const oDispensedInfo = medicationInfo(oDispensedMedication);

					let oProcess;
					if (oDispense) {
						oProcess = { step: 3, text: this.translate("empProcessDispensed"), state: "Success", icon: "sap-icon://accept" };
					} else if (oPrescription) {
						oProcess = { step: 2, text: this.translate("empProcessPrescribed"), state: "Information", icon: "sap-icon://e-care" };
					} else {
						oProcess = { step: 1, text: this.translate("empProcessPlanned"), state: "None", icon: "sap-icon://calendar" };
					}
					return {
						id: oEntry.id,
						versionId: oEntry.meta && oEntry.meta.versionId,
						resource: oEntry,
						empIdentifier: oEmpIdentifier ? oEmpIdentifier.value : "",
						status: oEntry.status,
						authoredOn: oEntry.authoredOn,
						requester: oEntry.requester && (oEntry.requester.display || oEntry.requester.reference),
						medication: oMedicationInfo,
						medicationResource: oMedication,
						originMedication: oOrigin && oOrigin.id !== (oMedication || {}).id ? medicationInfo(oOrigin) : { name: "" },
						dosageText: dosageText(oEntry, EXT.renderedDosageInstructionMR, "dosageInstruction"),
						dosage: oEntry.dosageInstruction || [],
						period: oPeriodExt ? oPeriodExt.valuePeriod : {},
						reason: (oReason && (oReason.text || (oIcd && oIcd.display))) || (oReasonExt && oReasonExt.valueString) || "",
						icd: oIcd ? oIcd.code : "",
						patientNote: oPatientNote && oPatientNote.valueAnnotation ? oPatientNote.valueAnnotation.text : "",
						note: ((oEntry.note || [])[0] || {}).text || "",
						statement: oStatement ? { id: oStatement.id, status: oStatement.status } : undefined,
						prescription: oPrescription ? {
							id: oPrescription.id,
							prescriptionId: oPrescriptionId ? oPrescriptionId.value : "",
							authoredOn: oPrescription.authoredOn,
							status: oPrescription.status,
							requester: oPrescription.requester && (oPrescription.requester.display || oPrescription.requester.reference),
							medicationName: oPrescribedInfo.name,
							medicationResource: oPrescribedMedication
						} : undefined,
							dispense: oDispense ? {
							id: oDispense.id,
							whenHandedOver: oDispense.whenHandedOver,
							pharmacy: ((oDispense.performer || [])[0] || {}).actor ? (oDispense.performer[0].actor.display || oDispense.performer[0].actor.reference) : "",
							medicationName: oDispensedInfo.name,
							substituted: !!(oDispensedInfo.pzn && oPrescribedInfo.pzn && oDispensedInfo.pzn !== oPrescribedInfo.pzn)
						} : undefined,
						process: oProcess
					};
				});

			const aEml = aStatements
				.filter((s) => s.status !== "entered-in-error")
				.sort((a, b) => ((a.effectivePeriod || {}).start || "") < ((b.effectivePeriod || {}).start || "") ? 1 : -1)
				.map((oStatement) => {
					const sContext = (ext(oStatement, EXT.context) || {}).valueCode;
					const oLinkedEntry = (oStatement.basedOn || []).map((b) => resolve(b)).find((r) => r && r.resourceType === "MedicationRequest");
					const oLinkedMedication = oLinkedEntry && resolve(oLinkedEntry.medicationReference);
					return {
						linkedEntryName: oLinkedMedication ? medicationInfo(oLinkedMedication).name : "",
						id: oStatement.id,
						medication: medicationInfo(resolve(oStatement.medicationReference)),
						dosageText: dosageText(oStatement, EXT.renderedDosageInstructionMS, "dosage"),
						start: (oStatement.effectivePeriod || {}).start || oStatement.dateAsserted,
						status: oStatement.status,
						source: sContext === "MANUAL" ? "Nachtrag" : "E-Rezept",
						linked: !!(oStatement.basedOn && oStatement.basedOn.length)
					};
				});

			const chronologyInfo = (oProvenance) => ({
				id: oProvenance.id,
				recorded: oProvenance.recorded,
				agent: (((oProvenance.agent || [])[0] || {}).who || {}).display || "",
				targets: (oProvenance.target || []).length,
				version: oProvenance.meta && oProvenance.meta.versionId
			});
			const oCurrentChronology = (oPlan.entry || []).map((e) => e.resource)
				.find((r) => r.resourceType === "Provenance" && (ext(r, EXT.isChronology) || {}).valueBoolean === true);
			const aLog = (oLog.entry || []).map((e) => e.resource)
				.filter((r) => r.resourceType === "Provenance")
				.map(chronologyInfo)
				.map((c) => Object.assign(c, { current: !!oCurrentChronology && c.id === oCurrentChronology.id }));

			return {
				chronology: oCurrentChronology ? chronologyInfo(oCurrentChronology) : {},
				counts: {
					active: aEntries.filter((e) => e.status === "active").length,
					onHold: aEntries.filter((e) => e.status === "on-hold").length,
					unlinkedEml: aEml.filter((e) => !e.linked).length
				},
				entries: aEntries,
				eml: aEml,
				log: aLog
			};
		},

		_dosageFromTiming: function (aDosage) {
			const mSlots = { MORN: 0, NOON: 0, EVE: 0, NIGHT: 0 };
			let sUnit = "";
			let bFound = false;
			(aDosage || []).forEach((d) => {
				const aWhen = ((d.timing || {}).repeat || {}).when || [];
				const oDose = ((d.doseAndRate || [])[0] || {}).doseQuantity;
				aWhen.forEach((w) => {
					if (w in mSlots && oDose) {
						mSlots[w] = oDose.value;
						sUnit = oDose.unit || sUnit;
						bFound = true;
					}
				});
			});
			return bFound ? [mSlots.MORN, mSlots.NOON, mSlots.EVE, mSlots.NIGHT].join("-") + (sUnit ? " " + sUnit : "") : "";
		},

		// ---------- formatters ----------
		formatDate: function (sDate) {
			if (!sDate) {
				return "";
			}
			const oDate = new Date(sDate.length === 10 ? sDate + "T00:00:00" : sDate);
			return isNaN(oDate.getTime()) ? sDate : this.oDateFormat.format(oDate);
		},
		formatDateTime: function (sDate) {
			if (!sDate) {
				return "";
			}
			const oDate = new Date(sDate);
			return isNaN(oDate.getTime()) ? sDate : this.oDateTimeFormat.format(oDate);
		},
		formatStatusText: function (sStatus) {
			const mText = { "active": "empStatusActive", "on-hold": "empStatusOnHold", "completed": "empStatusCompleted", "stopped": "empStatusStopped", "cancelled": "empStatusCancelled", "draft": "empStatusDraft" };
			return mText[sStatus] ? this.translate(mText[sStatus]) : (sStatus || "");
		},
		formatStatusState: function (sStatus) {
			return { "active": "Success", "on-hold": "Warning", "completed": "None", "stopped": "Error", "cancelled": "Error" }[sStatus] || "None";
		},

		// ---------- layout ----------
		onSelectEntry: function (oEvent) {
			const oItem = oEvent.getParameter("listItem");
			const oContext = oItem && oItem.getBindingContext("emp");
			this.getView().getModel("emp").setProperty("/selected", oContext ? oContext.getObject() : null);
		},
		onRefresh: function () {
			this.load();
		},
		onPrint: function () {
			window.open("/render/v1/emp/pdf?x-insurantid=" + encodeURIComponent(this._oContext.kvnr), "_blank", "noopener");
		},
		_navToLayout: function (sLayoutProperty) {
			const oLayoutModel = this.getOwnerComponent().getModel("Layout");
			this.getOwnerComponent().getRouter().navTo("patient-detail", {
				patient: this._oContext.patient,
				layout: oLayoutModel.getProperty(sLayoutProperty),
				document: this._oContext.document
			});
		},
		onFullScreen: function () {
			this._navToLayout("/actionButtonsInfo/endColumn/fullScreen");
		},
		onExitFullScreen: function () {
			this._navToLayout("/actionButtonsInfo/endColumn/exitFullScreen");
		},
		onClose: function () {
			const oLayoutModel = this.getOwnerComponent().getModel("Layout");
			this.getOwnerComponent().getRouter().navTo("patient-detail", {
				patient: this._oContext.patient,
				layout: oLayoutModel.getProperty("/actionButtonsInfo/endColumn/closeColumn")
			});
		},

		// ---------- dialogs ----------
		_openDialog: function (sFragment, sId) {
			const oView = this.getView();
			if (!this.byId(sId)) {
				return Fragment.load({ id: oView.getId(), name: "medunited.care.view.patient.viewer." + sFragment, controller: this }).then((oDialog) => {
					oView.addDependent(oDialog);
					oDialog.open();
					return oDialog;
				});
			}
			this.byId(sId).open();
			return Promise.resolve(this.byId(sId));
		},
		_today: function () {
			return new Date().toISOString().substring(0, 10);
		},
		_parseDosageScheme: function (sText) {
			const m = /^\s*(\d+(?:[.,]\d+)?)\s*-\s*(\d+(?:[.,]\d+)?)\s*-\s*(\d+(?:[.,]\d+)?)(?:\s*-\s*(\d+(?:[.,]\d+)?))?/.exec(sText || "");
			const num = (s) => s ? parseFloat(s.replace(",", ".")) : 0;
			return m ? { morning: num(m[1]), noon: num(m[2]), evening: num(m[3]), night: num(m[4]) } : { morning: 0, noon: 0, evening: 0, night: 0 };
		},
		onDosageChange: function () {
			const oModel = this.getView().getModel("dlg");
			const oEntry = oModel.getProperty("/entry");
			const oUnit = DOSE_UNITS.find((u) => u.key === oEntry.doseUnit) || DOSE_UNITS[0];
			const fmt = (v) => String(v || 0).replace(".", ",");
			oModel.setProperty("/entry/dosageText", [fmt(oEntry.morning), fmt(oEntry.noon), fmt(oEntry.evening), fmt(oEntry.night)].join("-") + " " + oUnit.text);
		},

		onAddEntry: function () {
			this.getView().getModel("dlg").setProperty("/entry", {
				name: "", pzn: "", form: "", normgroesse: "", ingredient: "", strength: "", strengthUnit: "mg",
				morning: 1, noon: 0, evening: 0, night: 0, doseUnit: "1", dosageText: "1-0-0-0 Stück",
				start: this._today(), end: "", reason: "", icd: "", patientNote: "", note: "", status: "active"
			});
			this._openDialog("EmpEntryDialog", "empEntryDialog");
		},
		onEditEntry: function () {
			const oSelected = this.getView().getModel("emp").getProperty("/selected");
			if (!oSelected) {
				return;
			}
			const oMed = oSelected.medication;
			const aStrength = /^(.*?)\s+(\d+(?:[.,]\d+)?)\s*(\S+)$/.exec(oMed.ingredient || "");
			const oScheme = this._parseDosageScheme(oSelected.dosageText);
			const oDose = ((((oSelected.dosage || [])[0] || {}).doseAndRate || [])[0] || {}).doseQuantity;
			this.getView().getModel("dlg").setProperty("/entry", Object.assign({
				id: oSelected.id,
				medicationId: oMed.id,
				originalMedication: oSelected.medicationResource,
				name: oMed.name, pzn: oMed.pzn, form: oMed.form, normgroesse: oMed.normgroesse,
				ingredient: aStrength ? aStrength[1] : oMed.ingredient,
				strength: aStrength ? aStrength[2] : "",
				strengthUnit: aStrength ? aStrength[3] : "mg",
				doseUnit: (oDose && DOSE_UNITS.find((u) => u.text === oDose.unit) || DOSE_UNITS[0]).key,
				dosageText: oSelected.dosageText,
				start: oSelected.period.start || "", end: oSelected.period.end || "",
				reason: oSelected.reason, icd: oSelected.icd, patientNote: oSelected.patientNote, note: oSelected.note,
				status: oSelected.status
			}, oScheme));
			this._openDialog("EmpEntryDialog", "empEntryDialog");
		},
		onCancelEntry: function () {
			this.byId("empEntryDialog").close();
		},

		/**
		 * Builds an EMPMedication from the dialog data.
		 */
		_buildMedication: function (oEntry) {
			const oMedication = {
				resourceType: "Medication",
				meta: { profile: [PROFILE.empMedication] },
				extension: [
					{ url: EXT.context, valueCode: "EMP" },
					{ url: EXT.medicationType, valueCoding: { system: "http://snomed.info/sct", code: "763158003", display: "Medicinal product (product)" } },
					{ url: EXT.drugCategory, valueCoding: { system: SYSTEM.drugCategory, code: "00" } },
					{ url: EXT.vaccine, valueBoolean: false }
				],
				code: { coding: [], text: oEntry.name },
				status: "active"
			};
			if (oEntry.pzn) {
				oMedication.code.coding.push({ system: SYSTEM.pzn, code: oEntry.pzn, display: oEntry.name });
			}
			if (oEntry.normgroesse) {
				oMedication.extension.push({ url: EXT.normgroesse, valueCode: oEntry.normgroesse });
			}
			if (oEntry.form) {
				oMedication.form = { coding: [{ system: SYSTEM.darreichungsform, code: oEntry.form }] };
			}
			if (oEntry.ingredient) {
				const oIngredient = { itemCodeableConcept: { text: oEntry.ingredient } };
				if (oEntry.strength) {
					oIngredient.strength = {
						numerator: { value: parseFloat(String(oEntry.strength).replace(",", ".")), unit: oEntry.strengthUnit },
						denominator: { value: 1, unit: "Stück" }
					};
				}
				oMedication.ingredient = [oIngredient];
			}
			return oMedication;
		},
		_buildDosageInstruction: function (oEntry) {
			const oUnit = DOSE_UNITS.find((u) => u.key === oEntry.doseUnit) || DOSE_UNITS[0];
			const aDosage = [];
			WHEN.forEach((oWhen) => {
				const fValue = parseFloat(oEntry[oWhen.prop]) || 0;
				if (fValue > 0) {
					aDosage.push({
						timing: { repeat: { frequency: 1, period: 1, periodUnit: "d", when: [oWhen.code] } },
						doseAndRate: [{ doseQuantity: { value: fValue, unit: oUnit.text, system: SYSTEM.dosiereinheit, code: oUnit.key } }]
					});
				}
			});
			if (!aDosage.length) {
				aDosage.push({});
			}
			aDosage[0].text = oEntry.dosageText;
			return aDosage;
		},
		/**
		 * Builds the EMPMedicationRequest (intent = plan) from the dialog data.
		 */
		_buildEmpEntry: function (oEntry, sMedicationReference) {
			const oRequest = {
				resourceType: "MedicationRequest",
				meta: { profile: [PROFILE.empMedicationRequest] },
				extension: [
					{ url: EXT.context, valueCode: "EMP" },
					{ url: EXT.renderedDosageInstructionMR, valueMarkdown: oEntry.dosageText }
				],
				status: oEntry.status || "active",
				intent: "plan",
				subject: { identifier: { system: SYSTEM.kvid, value: this._oContext.kvnr } },
				authoredOn: this._today(),
				dosageInstruction: this._buildDosageInstruction(oEntry)
			};
			if (oEntry.id) {
				oRequest.id = oEntry.id;
			}
			if (sMedicationReference) {
				oRequest.medicationReference = { reference: sMedicationReference };
			}
			if (oEntry.start || oEntry.end) {
				const oPeriod = {};
				if (oEntry.start) {
					oPeriod.start = oEntry.start;
				}
				if (oEntry.end) {
					oPeriod.end = oEntry.end;
				}
				oRequest.extension.push({ url: EXT.effectiveDosePeriod, valuePeriod: oPeriod });
			}
			if (oEntry.reason) {
				oRequest.extension.push({ url: EXT.reasonPatientInstruction, valueString: oEntry.reason });
			}
			if (oEntry.reason || oEntry.icd) {
				const oReason = { text: oEntry.reason };
				if (oEntry.icd) {
					oReason.coding = [{ system: SYSTEM.icd10, version: "2026", code: oEntry.icd, display: oEntry.reason }];
				}
				oRequest.reasonCode = [oReason];
			}
			if (oEntry.patientNote) {
				oRequest.extension.push({ url: EXT.patientNote, valueAnnotation: { text: oEntry.patientNote } });
			}
			if (oEntry.note) {
				oRequest.note = [{ text: oEntry.note }];
			}
			return oRequest;
		},
		_medicationChanged: function (oEntry) {
			const oOld = oEntry.originalMedication;
			if (!oOld) {
				return true;
			}
			const oNew = this._buildMedication(oEntry);
			const norm = (o) => JSON.stringify({ code: o.code, form: o.form, ingredient: o.ingredient, norm: (ext(o, EXT.normgroesse) || {}).valueCode });
			return norm(oOld) !== norm(oNew);
		},
		onSaveEntry: function () {
			const oDlgModel = this.getView().getModel("dlg");
			const oEntry = oDlgModel.getProperty("/entry");
			const sChronologyId = this.getView().getModel("emp").getProperty("/chronology/id");
			oDlgModel.setProperty("/busy", true);
			let oPromise;
			if (oEntry.id) {
				const bMedicationChanged = this._medicationChanged(oEntry);
				oPromise = this._oClient.updateEmpEntry(sChronologyId,
					bMedicationChanged ? this._buildMedication(oEntry) : undefined,
					this._buildEmpEntry(oEntry, bMedicationChanged ? undefined : "Medication/" + oEntry.medicationId));
			} else {
				oPromise = this._oClient.addEmpEntry(sChronologyId, this._buildMedication(oEntry), this._buildEmpEntry(oEntry));
			}
			oPromise.then(() => {
				oDlgModel.setProperty("/busy", false);
				this.byId("empEntryDialog").close();
				MessageToast.show(this.translate(oEntry.id ? "empMsgEntryUpdated" : "empMsgEntryCreated"));
				return this.load();
			}).catch((oError) => {
				oDlgModel.setProperty("/busy", false);
				this._showError(oError);
			});
		},

		_changeStatus: function (sStatus, sMessageKey) {
			const oSelected = this.getView().getModel("emp").getProperty("/selected");
			if (!oSelected) {
				return;
			}
			const sChronologyId = this.getView().getModel("emp").getProperty("/chronology/id");
			const oRequest = JSON.parse(JSON.stringify(oSelected.resource));
			oRequest.status = sStatus;
			this.getView().getModel("emp").setProperty("/busy", true);
			this._oClient.updateEmpEntry(sChronologyId, undefined, oRequest).then(() => {
				MessageToast.show(this.translate(sMessageKey));
				return this.load();
			}).catch((oError) => {
				this.getView().getModel("emp").setProperty("/busy", false);
				this._showError(oError);
			});
		},
		onPauseEntry: function () {
			this._changeStatus("on-hold", "empMsgEntryPaused");
		},
		onResumeEntry: function () {
			this._changeStatus("active", "empMsgEntryResumed");
		},
		onEndEntry: function () {
			MessageBox.confirm(this.translate("empConfirmEnd"), {
				onClose: (sAction) => {
					if (sAction === MessageBox.Action.OK) {
						this._changeStatus("completed", "empMsgEntryEnded");
					}
				}
			});
		},

		// ---------- eML <-> eMP linking ----------
		onLinkEml: function () {
			const oModel = this.getView().getModel("emp");
			const oSelected = oModel.getProperty("/selected");
			if (!oSelected) {
				return;
			}
			const aCandidates = oModel.getProperty("/eml").filter((e) => !e.linked);
			if (!aCandidates.length) {
				MessageToast.show(this.translate("empMsgNoUnlinkedEml"));
				return;
			}
			if (!this._oLinkDialog) {
				this._oLinkDialog = new SelectDialog({
					title: this.translate("empDialogLinkEml"),
					noDataText: this.translate("empMsgNoUnlinkedEml"),
					contentWidth: "40rem",
					items: {
						path: "link>/candidates",
						template: new StandardListItem({
							title: "{link>medication/name}",
							description: "{link>medication/details} · {link>dosageText} · {link>source}",
							icon: "sap-icon://list",
							iconDensityAware: false,
							iconInset: false
						})
					},
					search: (oEvent) => {
						const sValue = (oEvent.getParameter("value") || "").toLowerCase();
						oEvent.getSource().getBinding("items").filter(sValue ? [new Filter("medication/name", FilterOperator.Contains, sValue)] : []);
					},
					confirm: (oEvent) => {
						const oItem = oEvent.getParameter("selectedItem");
						if (oItem) {
							this._link(oItem.getBindingContext("link").getObject());
						}
					}
				});
				this._oLinkDialog.setModel(new JSONModel(), "link");
				this.getView().addDependent(this._oLinkDialog);
			}
			this._oLinkDialog.getModel("link").setData({ entry: oSelected, candidates: aCandidates });
			this._oLinkDialog.open();
		},
		_link: function (oEmlEntry) {
			const oModel = this.getView().getModel("emp");
			const oSelected = oModel.getProperty("/selected");
			oModel.setProperty("/busy", true);
			this._oClient.linkEmp(oEmlEntry.id, oSelected.id).then(() => {
				MessageToast.show(this.translate("empMsgLinked"));
				return this.load();
			}).catch((oError) => {
				oModel.setProperty("/busy", false);
				this._showError(oError);
			});
		},
		onUnlinkEml: function () {
			const oModel = this.getView().getModel("emp");
			const oSelected = oModel.getProperty("/selected");
			if (!oSelected || !oSelected.statement) {
				return;
			}
			MessageBox.confirm(this.translate("empConfirmUnlink"), {
				onClose: (sAction) => {
					if (sAction !== MessageBox.Action.OK) {
						return;
					}
					oModel.setProperty("/busy", true);
					this._oClient.unlinkEmp(oSelected.statement.id).then(() => {
						MessageToast.show(this.translate("empMsgUnlinked"));
						return this.load();
					}).catch((oError) => {
						oModel.setProperty("/busy", false);
						this._showError(oError);
					});
				}
			});
		},

		_showError: function (oError) {
			if (oError.status === 409) {
				MessageBox.warning(this.translate("empErrorChronologyOutdated") + "\n\n" + oError.message, {
					onClose: () => this.load()
				});
			} else {
				MessageBox.error(oError.message || String(oError));
			}
		}
	});
});
