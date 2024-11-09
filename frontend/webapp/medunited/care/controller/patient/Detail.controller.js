sap.ui.define([
	"medunited/base/controller/AbstractDetailController",
	"../../utils/Formatter",
	"sap/m/MessageBox",
	"sap/ui/core/Fragment",
	"sap/base/security/URLListValidator",
	"sap/ui/core/mvc/XMLView",
	'medunited/care/utils/PropertyExtractor'
], function (AbstractDetailController, Formatter, MessageBox, Fragment, URLListValidator, XMLView, PropertyExtractor) {
	"use strict";

	return AbstractDetailController.extend("medunited.care.controller.patient.Detail", {
		formatter: Formatter,
		getEntityName: function () {
			return "Patient";
		},
		getBindElementParams: function () {
			return {
				groupId: "patientDetails"
			};
		},
		_onMatched: function (oEvent) {
			AbstractDetailController.prototype._onMatched.apply(this, arguments);
			const oWebdavModel = this.getView().getModel();
			const iPatientModelOffest = oEvent.getParameter("arguments").patient;
			const sWebDavPath = "/response/"+iPatientModelOffest;
			this.getView().bindElement(sWebDavPath);
			const sPatientId = oWebdavModel.getProperty(sWebDavPath+"/propstat/prop/displayname");
			
			oWebdavModel.loadFolderForContext("/response/"+iPatientModelOffest, "/"+sPatientId);
			oWebdavModel.loadFileForContext(sWebDavPath, "/"+sPatientId+"/local/PersoenlicheVersichertendaten.xml");
			oWebdavModel.loadFileForContext(sWebDavPath, "/"+sPatientId+"/local/AllgemeineVersicherungsdaten.xml");
			oWebdavModel.loadFileForContext(sWebDavPath, "/"+sPatientId+"/local/GeschuetzteVersichertendaten.xml");
		},
		formatPatientDataMatrix: function (sId, optionSelected) {
			const oPatient = this.getView().getModel().getProperty("/Patient/" + sId);
			const oMedicationStatement = this.getView().getModel().getProperty("/MedicationStatement");
			if (!oMedicationStatement) {
				return "";
			}
			const aMedicationStatementForPatient = Object.values(oMedicationStatement)
				.filter(aMS => aMS.subject && aMS.subject.reference === "Patient/" + sId)
				.sort(function compareFn(a, b) {
					let aValueDecimal = 0;
					if(a && a.extension && a.extension[2]) {
						aValueDecimal = a.extension[2].valueDecimal
					}
					let bValueDecimal = 0;
					if(b && b.extension && b.extension[2]) {
						bValueDecimal = b.extension[2].valueDecimal
					}
					return bValueDecimal - aValueDecimal;
				});
			return this.getMedicationPlanXml(oPatient, aMedicationStatementForPatient, optionSelected);
		},
		getMedicationPlanXml: function (oPatient, aMedicationStatementForPatient, optionSelected) {
			//https://update.kbv.de/ita-update/Verordnungen/Arzneimittel/BMP/EXT_ITA_VGEX_BMP_Anlage3_mitAend.pdf
			if (optionSelected == "optionJustTheMedications" || optionSelected == undefined) {
				this.byId("groupMedicationsInXML").getSelectedItem().setKey("optionJustTheMedications");
				let sXML = "<MP xmlns=\"http://ws.gematik.de/fa/amtss/AMTS_Document/v1.6\" v=\"025\" U=\"" + [...Array(32)].map(() => 'ABCDEF0123456789'.charAt(Math.floor(Math.random() * 16))).join('') + "\" l=\"de-DE\">\n";
				if (oPatient && oPatient.name && oPatient.name.length > 0 && oPatient.name[0].given) {
					sXML += "  <P g=\"" + oPatient.name[0].given[0] + "\" f=\"" + oPatient.name[0].family + "\" b=\"" + (oPatient.birthDate ? oPatient.birthDate.replaceAll("-", "") : "") + "\" />\n";
				}
				if (this.getNameFromLoggedPerson()) {
					sXML += "  <A n=\"med.united " + this.getNameFromLoggedPerson();
				}
				if (this.getStreetFromLoggedPerson()) {
					sXML += "\" s=\"" + this.getStreetFromLoggedPerson();
				}
				if (this.getPostalCodeFromLoggedPerson()) {
					sXML += "\" z=\"" + this.getPostalCodeFromLoggedPerson();
				}
				if (this.getCityFromLoggedPerson()) {
					sXML += "\" c=\"" + this.getCityFromLoggedPerson();
				}
				if (this.getPhoneNumberFromLoggedPerson()) {
					sXML += "\" p=\"" + this.getPhoneNumberFromLoggedPerson();
				}
				if (this.getEmailFromLoggedPerson()) {
					sXML += "\" e=\"" + this.getEmailFromLoggedPerson();
				}
				sXML += "\" t=\"" + new Date().toISOString().substring(0, 19) + "\" />\n";
				sXML += "  <S>\n";
				for (let oMedicationStatement of aMedicationStatementForPatient) {
					try {
						if(!oMedicationStatement) {
							continue;
						}
						const pzn = (oMedicationStatement.identifier && oMedicationStatement.identifier.length > 0) ? oMedicationStatement.identifier[0].value : "";
						sXML += "    <M"+ (pzn ? " p=\""+pzn+"\"" : "") + " ";
						if(oMedicationStatement && oMedicationStatement.medicationCodeableConcept) {
							const medicationName = oMedicationStatement.medicationCodeableConcept.text;
							if (medicationName) {
								sXML += "a=\"" + this.escapeXml(medicationName) + "\" ";
							} else {
								sXML += "a=\"\" ";
							}
						} else {
							sXML += "a=\"\" ";
						}
						const oDosage = oMedicationStatement.dosage;
						if (oDosage && oDosage.length > 0 && oDosage[0].text) {
							const aDosage = oDosage[0].text.split(/-/);
							const mDosage = {
								0: "m",
								1: "d",
								2: "v",
								3: "h"
							};
							for (let i = 0; i < aDosage.length; i++) {
								if(parseFloat(aDosage[i].replaceAll(/,/g, ".")) > 0) {
									sXML += mDosage[i] + "=\"" + aDosage[i] + "\" ";
								}
							}
						}
						const vNote = oMedicationStatement.note;
						if (typeof vNote === "string") {
							sXML = this.extractReasonInfo(vNote, sXML);
						} else if(typeof vNote === "object") {
							for(let oItem in vNote) {
								if("text" in vNote[oItem]) {
									sXML = this.extractReasonInfo(vNote[oItem].text, sXML);
									break;
								}
							}
						}
						sXML += "/>\n";
					} catch (e) {
						console.error(e);
					}
				}
				sXML += "   </S>\n";
				sXML += "</MP>";
				this.byId("medicationPlanDataMatrixCode").setMsg(sXML);
				return sXML;

			} else if (optionSelected == "optionMedicationsSortedByDoctors") {
				let newXML = "<MP xmlns=\"http://ws.gematik.de/fa/amtss/AMTS_Document/v1.6\" v=\"025\" U=\"" + [...Array(32)].map(() => 'ABCDEF0123456789'.charAt(Math.floor(Math.random() * 16))).join('') + "\" l=\"de-DE\">\n";
				if (oPatient && oPatient.name && oPatient.name.length > 0 && oPatient.name[0].given) {
					newXML += "  <P g=\"" + oPatient.name[0].given[0] + "\" f=\"" + oPatient.name[0].family + "\" b=\"" + (oPatient.birthDate ? oPatient.birthDate.replaceAll("-", "") : "") + "\" />\n";
				}
				if (this.getNameFromLoggedPerson()) {
					newXML += "  <A n=\"med.united " + this.getNameFromLoggedPerson();
				}
				if (this.getStreetFromLoggedPerson()) {
					newXML += "\" s=\"" + this.getStreetFromLoggedPerson();
				}
				if (this.getPostalCodeFromLoggedPerson()) {
					newXML += "\" z=\"" + this.getPostalCodeFromLoggedPerson();
				}
				if (this.getCityFromLoggedPerson()) {
					newXML += "\" c=\"" + this.getCityFromLoggedPerson();
				}
				if (this.getPhoneNumberFromLoggedPerson()) {
					newXML += "\" p=\"" + this.getPhoneNumberFromLoggedPerson();
				}
				if (this.getEmailFromLoggedPerson()) {
					newXML += "\" e=\"" + this.getEmailFromLoggedPerson();
				}
				newXML += "\" t=\"" + new Date().toISOString().substring(0, 19) + "\" />\n";
				const structureWithMedicationsSortedByPractitioner = this.sortMedicationsByPractitioner(aMedicationStatementForPatient);
				for (const aPractitioner of Object.entries(structureWithMedicationsSortedByPractitioner)) {
					const oView = this.getView();
					let practitionerFullName = "Dr. " + PropertyExtractor.extractFullNameFromPractitioner(oView, aPractitioner[0]);
					if (practitionerFullName == "Dr. undefined undefined" || practitionerFullName == "Dr. undefined") {
						practitionerFullName = this.translate("practitionerNameNotDefined");
					}
					newXML += "  <S t=\"" + practitionerFullName + "\">\n";
					const medStatsOfPractitioner = aPractitioner[1];
					for (const medStatOfPractitioner of Object.entries(medStatsOfPractitioner)) {
						const medStat = medStatOfPractitioner[1];
						const pzn = (medStat.identifier && medStat.identifier.length > 0) ? medStat.identifier[0].value : "";
						newXML += "    <M"+ (pzn ? " p=\""+pzn+"\"" : "") + " ";
						if(medStat && medStat.medicationCodeableConcept) {
							const medicationName = medStat.medicationCodeableConcept.text;
							if (medicationName) {
								newXML += "a=\"" + this.escapeXml(medicationName) + "\" ";
							} else {
								newXML += "a=\"\" ";
							}
						} else {
							newXML += "a=\"\" ";
						}
						const oDosage = medStat.dosage;
						if (oDosage && oDosage.length > 0 && oDosage[0].text) {
							const aDosage = oDosage[0].text.split(/-/);
							const mDosage = {
								0: "m",
								1: "d",
								2: "v",
								3: "h"
							};
							for (let i = 0; i < aDosage.length; i++) {
								if(parseFloat(aDosage[i].replaceAll(/,/g, ".")) > 0) {
									newXML += mDosage[i] + "=\"" + aDosage[i] + "\" ";
								}
							}
						}
						const vNote = medStat.note;
						if (typeof vNote === "string") {
							newXML = this.extractReasonInfo(vNote, newXML);
						} else if(typeof vNote === "object") {
							for(let oItem in vNote) {
								if("text" in vNote[oItem]) {
									newXML = this.extractReasonInfo(vNote[oItem].text, newXML);
									break;
								}
							}
						}
						newXML += "/>\n";
					}
					newXML += "   </S>\n";
				}
				newXML += "</MP>";
				this.byId("medicationPlanDataMatrixCode").setMsg(newXML);
				return newXML;
			}
		},
		sortMedicationsByPractitioner: function(medicationStatements) {

			// structure = { Practitioner : [ MedicationStatements ]}
			const structure = {};

			for (const aMedicationStatement of medicationStatements) {
				const practitionerReference = aMedicationStatement.informationSource.reference;

				const aPractitioner = this.getView().getModel().getProperty("/" + practitionerReference);

				if (practitionerReference in structure) {
					structure[practitionerReference].push(aMedicationStatement);
				}
				else {
					structure[practitionerReference] = [];
					structure[practitionerReference].push(aMedicationStatement);
				}
			}
			return structure;
		},
		extractReasonInfo: function(sNote, sXML) {
			if(!sNote) {
				return sXML;
			}
			const m = sNote.match("Grund: (.*) Hinweis: (.*)");
			if (m) {
				sXML += "r=\"" + this.escapeXml(m.group(1)) + "\" i=\"" + this.escapeXml(m.group(2)) + "\" ";
			} else {
				sXML += "i=\"" + this.escapeXml(sNote) + "\" ";
			}
			return sXML;
		},

		escapeXml: function(unsafe) {
			if(!unsafe) {
				return unsafe;
			}
			return unsafe.replace(/[<>&'"]/g, function (c) {
				switch (c) {
					case '<': return '&lt;';
					case '>': return '&gt;';
					case '&': return '&amp;';
					case '\'': return '&apos;';
					case '"': return '&quot;';
				}
			});
		},

		validateUserCustomAttributes() {
			const phone = this.getPhoneNumberFromLoggedPerson();
			const city = this.getCityFromLoggedPerson();
			const street = this.getStreetFromLoggedPerson();
			const postalCode = this.getPostalCodeFromLoggedPerson();
		},

		getPhoneNumberFromLoggedPerson() {
			return "Unknown";
		},

		getNameFromLoggedPerson() {
			return "Unknown";
		},

		getEmailFromLoggedPerson() {
			return "Unknown";
		},

		getStreetFromLoggedPerson() {
			return "Unknown";
		},

		getPostalCodeFromLoggedPerson() {
			return "Unknown";
		},

		getCityFromLoggedPerson() {
			return "Unknown";
		},

		onCreateMedicationPlan: function () {
			this.validateUserCustomAttributes()
			const sPatientId = this._entity
			fetch("https://medicationplan.med-united.health/medicationPlanPdf", {
				method: "POST",
				mode: "cors",
				body: this.formatPatientDataMatrix(sPatientId, "optionJustTheMedications"),
				headers: {
					"Accept": "application/pdf",
					"Content-Type": "application/xml"
				}
			})
				.then((oResponse) => {
					if (!oResponse.ok) {
						throw Error(oResponse.statusText);
					}
					return oResponse;
				})
				.then(oResponse => oResponse.blob())
				.then((oBlob) => {
					const sObjectURL = URL.createObjectURL(oBlob);
					if (!this.byId("medicationPlanDialog")) {
						URLListValidator.add("blob");
						// load asynchronous XML fragment
						Fragment.load({
							id: this.getView().getId(),
							name: "medunited.care.view." + this.getEntityName().toLowerCase() + ".MedicationPlanDialog",
							controller: this
						}).then((oDialog) => {
							// connect dialog to the root view of this component (models, lifecycle)
							this.getView().addDependent(oDialog);
							this._openMedicationPlanDialog(oDialog, sObjectURL);
						});
					} else {
						this._openMedicationPlanDialog(this.byId("medicationPlanDialog"), sObjectURL);
					}
				}).catch((oError) => MessageBox.show(this.translate("msgError", [oError])));
		},
		_openMedicationPlanDialog: function (oDialog, sObjectURL) {
			oDialog.open();
			this.byId("medicationPlanPdfViewer").setSource(sObjectURL);
		},
		onCloseMedicationPlan: function () {
			this.byId("medicationPlanDialog").close();
		},
		copyXMLOfDataMatrixCodeToClipboard: async function (oEvent) {
			let XMLOfDataMatrixCode = this.byId("medicationPlanDataMatrixCode").getMsg();
			let text;
			while(text != XMLOfDataMatrixCode) {
				navigator.clipboard.writeText(XMLOfDataMatrixCode);
				text = await navigator.clipboard.readText();
			}
			alert("Das folgende XML wurde in die Zwischenablage kopiert:\n\n" + XMLOfDataMatrixCode)
		},
		onChangeInfoOnDatamatrixCode: function (oEvent) {
			const sPatientId = this._entity;
			let optionSelected = this.byId("groupMedicationsInXML").getSelectedItem().getKey();
			return this.formatPatientDataMatrix(sPatientId, optionSelected);
		},
		cleanFraction: function (fraction) {
			const myArray = fraction.split("/");
			let numerator = myArray[0];
			let denominator = myArray[1];
			if ((/^0+$/.test(numerator) && !(/^0+$/.test(denominator))) || /^0*1$/.test(denominator)) {
				return numerator;
			}
			return numerator.replace(/^0+(?!$)/, "") + "/" + denominator.replace(/^0+(?!$)/, ""); // remove leading zeros
		},
		validateResource: function () {
			const oModel = this.getView().getModel();
			let medicationStatementsOfPatient = this.getMedicationStatementsOfPatient(this._entity);
            for (let i of medicationStatementsOfPatient) {
				// Validate PZNs
				let pznValue = oModel.getProperty("/MedicationStatement/" + i + "/identifier/0/value");
				oModel.setProperty("/MedicationStatement/" + i + "/identifier/0/value", parseInt(pznValue, 10));
				// Validate Dosages
				let dosageValue = oModel.getProperty("/MedicationStatement/" + i + "/dosage/0/text");
				if (dosageValue === "" || dosageValue == null || dosageValue.trim().length === 0) {
					MessageBox.error(this.translate("msgAtLeastOneOfTheDosagesWasNotSpecified"), {
						title: this.translate("msgErrorTitle"),
						onClose: null,
						styleClass: "",
						actions: MessageBox.Action.OK,
						emphasizedAction: MessageBox.Action.OK,
						initialFocus: null,
						textDirection: sap.ui.core.TextDirection.Inherit
					});
					return false;
				}
				else if (dosageValue !== "" && dosageValue.trim().length > 0 && dosageValue.includes("-") && dosageValue.match(/-/g).length == 3) {
					let morgensDosage = dosageValue.split("-")[0].replace(/\s/g, "");
					let mittagsDosage = dosageValue.split("-")[1].replace(/\s/g, "");
					let abendsDosage = dosageValue.split("-")[2].replace(/\s/g, "");
					let nachtsDosage = dosageValue.split("-")[3].replace(/\s/g, "");

					if (morgensDosage == "") {
						morgensDosage = "0";
					} else if (/^\d+\/\d+$/.test(morgensDosage.trim())) {
						morgensDosage = this.cleanFraction(morgensDosage.trim());
					}
					if (mittagsDosage == "") {
						mittagsDosage = "0";
					} else if (/^\d+\/\d+$/.test(mittagsDosage.trim())) {
						mittagsDosage = this.cleanFraction(mittagsDosage.trim());
					}
					if (abendsDosage == "") {
						abendsDosage = "0";
					} else if (/^\d+\/\d+$/.test(abendsDosage.trim())) {
						abendsDosage = this.cleanFraction(abendsDosage.trim());
					}
					if (nachtsDosage == "") {
						nachtsDosage = "0";
					} else if (/^\d+\/\d+$/.test(nachtsDosage.trim())) {
						nachtsDosage = this.cleanFraction(nachtsDosage.trim());
					}

					if (((/^\d+(,\d+)?$/.test(morgensDosage.trim())) || (/^\d+\/\d+$/.test(morgensDosage.trim()))) && !(/^\d+\/0+$/.test(morgensDosage.trim())) &&
						((/^\d+(,\d+)?$/.test(mittagsDosage.trim())) || (/^\d+\/\d+$/.test(mittagsDosage.trim()))) && !(/^\d+\/0+$/.test(mittagsDosage.trim())) &&
						((/^\d+(,\d+)?$/.test(abendsDosage.trim())) || (/^\d+\/\d+$/.test(abendsDosage.trim()))) && !(/^\d+\/0+$/.test(abendsDosage.trim())) &&
						((/^\d+(,\d+)?$/.test(nachtsDosage.trim())) || (/^\d+\/\d+$/.test(nachtsDosage.trim()))) && !(/^\d+\/0+$/.test(nachtsDosage.trim()))) {
						let newDosageValue = morgensDosage.trim() + "-" + mittagsDosage.trim() + "-" + abendsDosage.trim() + "-" + nachtsDosage.trim();
						oModel.setProperty("/MedicationStatement/" + i + "/dosage/0/text", newDosageValue);
					}
					else {
						MessageBox.error(this.translate("msgAtLeastOneOfTheDosagesContainsCharactersThatAreNotAllowed"), {
							title: this.translate("msgErrorTitle"),
							onClose: null,
							styleClass: "",
							actions: MessageBox.Action.OK,
							emphasizedAction: MessageBox.Action.OK,
							initialFocus: null,
							textDirection: sap.ui.core.TextDirection.Inherit
						});
						return false;
					}
				}
				else {
					MessageBox.error(this.translate("msgAtLeastOneOfTheDosagesDoesNotHaveTheRightFormat"), {
						title: this.translate("msgErrorTitle"),
						onClose: null,
						styleClass: "",
						actions: MessageBox.Action.OK,
						emphasizedAction: MessageBox.Action.OK,
						initialFocus: null,
						textDirection: sap.ui.core.TextDirection.Inherit
					});
					return false;
				}
			}
			return true;
		},
		getMedicationStatementsOfPatient: function (patientId) {
			let oModel = this.getView().getModel();
			let medicationStatements = oModel.getProperty("/MedicationStatement");
			let medicationStatementsOfPatient = [];
			for (let medStat in medicationStatements) {
				if (oModel.getProperty("/MedicationStatement/" + medStat + "/subject/reference") == "Patient/" + patientId) {
					medicationStatementsOfPatient.push(medStat);
				}
			}
			return medicationStatementsOfPatient;
		},
		onSeeMedicationPlan: function() {
			const oFlexibleColumnLayout = this.getOwnerComponent().getRootControl().byId("fcl");
			const me = this;
			const sPatientId = this.getView().getBindingContext().getProperty("propstat/prop/displayname");
			this.getOwnerComponent().runAsOwner(() => {
				XMLView.create({
				    viewName: "medunited.care.view.patient.viewer.HtmlViewer"
				}).then((oView) => {
					fetch("/fhir/xhtml/1?kvnr="+sPatientId)
						.then(o => o.text())
						.then((text) => oView.byId("html").setContent(text));
					oFlexibleColumnLayout.removeAllEndColumnPages();
				    oFlexibleColumnLayout.addEndColumnPage(oView);
					me.oRouter.navTo(this.getEntityName().toLowerCase() + "-master", {
						"patient" : this._entity,
						"layout": "ThreeColumnsEndExpanded",
						"document": "/fhir/xhtml/1?kvnr="+sPatientId
					});
				});
			});
		}
	});
}, true);