sap.ui.define([
    'medunited/base/controller/AbstractMasterController',
    'sap/ui/model/Filter',
    'sap/ui/model/FilterOperator',
    'sap/m/MessageBox',
    'sap/m/MessageToast',
    "sap/ui/core/Fragment",
    "sap/ui/model/json/JSONModel",
    'medunited/care/model/WebdavModel',
    "sap/ui/core/BusyIndicator"
], function (AbstractMasterController, Filter, FilterOperator, MessageBox, MessageToast, Fragment, JSONModel, WebdavModel, BusyIndicator) {
    "use strict";

    return AbstractMasterController.extend("medunited.care.controller.patient.Master", {

        onInit: function() {

            AbstractMasterController.prototype.onInit.apply(this, arguments);

            this._connectWebSocket();
        },
        _connectWebSocket: function(retryCount = 0) {
            let sTelematikId = localStorage.getItem("telematikId");

            if (!sTelematikId) {
                console.error("Telematik ID is missing. Cannot establish websocket connection.");
                return;
            }

            let sProtocol = window.location.protocol === "https:" ? "wss://" : "ws://";
            let sHost = window.location.host;
            let sWebSocketUrl = `${sProtocol}${sHost}/ws/${sTelematikId}`;
            // const sWebSocketUrl = `ws://localhost:8765`; // just for testing
            console.log(`Connecting to WebSocket: ${sWebSocketUrl} (Attempt ${retryCount + 1})`);

            this.oWebSocket = new WebSocket(sWebSocketUrl);
            this.oWebSocket.onopen = () => {
                console.log("Websocket connected:", sWebSocketUrl);
                this._retryDelay = 1000;
            };

            this.oWebSocket.onmessage = (oEvent) => {
                // console.log("Raw websocket message:", oEvent.data);
                try {
                    let oMessage = JSON.parse(oEvent.data);
                    console.log("Parsed websocket message:", oMessage);

                    if (oMessage.kvnr && oMessage.medicationPdfBase64) {
                        this._refreshPatientList();
                        this._openMedicationList(oMessage.kvnr, oMessage.medicationPdfBase64);
                    }
                } catch (error) {
                    console.warn("Received message:", oEvent.data);
                }
            };

            this.oWebSocket.onerror = (oError) => {
                console.error("Websocket error:", oError);
            };

            this.oWebSocket.onclose = () => {
                console.warn(`Websocket disconnected. Retrying in ${this._retryDelay / 1000} seconds...`);

                setTimeout(() => {
                    if (retryCount < 5) {
                        this._connectWebSocket(retryCount + 1);
                        this._retryDelay = Math.min(this._retryDelay * 2, 30000);
                    } else {
                        console.error("Max reconnection attempts reached. Websocket will not retry further.");
                    }
                }, this._retryDelay);

                this._retryDelay = this._retryDelay || 1000;
            };
        },
        getEntityName: function () {
            return "Patient";
        },
        getFilter: function (sQuery) {
            return [new Filter({
                filters: sQuery.match(".* .*") ?[
                    // OR filter does not work
                    new Filter("given", FilterOperator.Contains, sQuery.split(" ")[0]),
                    new Filter("family", FilterOperator.Contains, sQuery.split(" ")[1])
                ] : [
                    // OR filter does not work
                    //new Filter("given", FilterOperator.Contains, sQuery),
                    new Filter("family", FilterOperator.Contains, sQuery)
                ],
                and: false
            }
            )];
        },
        onSortFamilyName: function() {
            this._sSortField = "family";
            this.sort();
        },
        onSortBirthDate: function() {
            this._sSortField = "birthdate";
            this.sort();
        },
        getSortField: function () {
            return this._sSortField;
        },
        onEncodingUTF8Selected: function (oEvent) {
            if (this.byId("iso88591").getSelected()) {
                this.byId("iso88591").setSelected(false);
            }
            else if (!this.byId("utf8").getSelected()) {
                this.byId("utf8").setSelected(true);
            }
        },
        onEncodingISO88591Selected: function (oEvent) {
            if (this.byId("utf8").getSelected()) {
                this.byId("utf8").setSelected(false);
            }
            else if (!this.byId("iso88591").getSelected()) {
                this.byId("iso88591").setSelected(true);
            }
        },
        onPressCreatePatientFromBMP: function () {
            this.byId("extScanner").open();
        },
        referencePractitioner: function (sPractitionerPath) {
            try {
                if (sPractitionerPath) {
                    return this.getNameForPath("/" + sPractitionerPath);
                }
            } catch (e) {
                console.log(e);
                return "Arzt unbekannt";
            }
        },
        getNameForPath: function (sObjectPath) {
            const oFhirModel = this.getView().getModel();
            const oObject = oFhirModel.getProperty(sObjectPath);
            if(!oObject || !oObject.name || !oObject.name[0] || !oObject.name[0].given) {
                return "";
            }
            return oObject.name[0].given[0] + " " + oObject.name[0].family;
        },
        referenceOrganization: function (sOrganizationPath) {
            try {
                if (sOrganizationPath) {
                    return this.getPharmacyNameForPath("/" + sOrganizationPath);
                }
            } catch (e) {
                console.log(e);
                return "Apotheke unbekannt";
            }
        },
        getPharmacyNameForPath: function (sObjectPath) {
            const oFhirModel = this.getView().getModel();
            const oObject = oFhirModel.getProperty(sObjectPath);
            return oObject.name;
        },
		save: function(oEvent) {
			const fnSuccess = (oData) => {
                MessageToast.show(this.translate(this.getEntityName()) + ' ' + this.translate("msgSaveResourceSuccessful"));
            };

            const fnError = (oError, sMessage) => {
                MessageBox.show(this.translate(this.getEntityName()) + ' ' + this.translate("msgSaveResouceFailed", [oError.statusCode, sMessage ? sMessage : oError.statusText]));
            };
			let sKvnr = this.byId("kvnr").getValue();
			fetch("/vsd/kvnr?x-insurantid="+encodeURIComponent(sKvnr), {"method": "POST"})
			.then((oResponse) => {
				if(!oResponse.ok) {
					oResponse.statusCode = oResponse.status;
					oResponse.json().then((oJson) => {					
						fnError(oResponse, oJson?.error);
					})
				} else {					
					this.byId("patientTable").getBinding("items").refresh();
					fnSuccess();
				}
			})
			.catch((oError) => {
				fnError(oError);
			});
		},
        onValueScanned: function (oEvent) {

            const mPZN2Name = {};
            const sEMP = oEvent.getParameter("value")
            const parser = new DOMParser();
            const oEMP = parser.parseFromString(sEMP, "application/xml");

            Promise.all(
                Array.from(oEMP.querySelectorAll("M"))
                    .map(m => m.getAttribute("p"))
                    .map(sPZN => fetch("https://medication.med-united.health/ajax/search/drugs/auto/?query=" + sPZN)
                        .then(r => r.json())
                        .then(oMedication => {
                            if (oMedication.results.length > 0) {
                                mPZN2Name[sPZN] = oMedication.results[0].name;
                            }
                            else if (oMedication.results.length == 0 && oEMP.querySelectorAll("W").length > 0) {
                                for (const element of oEMP.querySelectorAll("W")) {
                                    let activeSubstance = element.getAttribute("w");
                                    let strength = element.getAttribute("s");
                                    if (activeSubstance != null && strength != null) {
                                        const fetchPromise = fetch("https://medication.med-united.health/ajax/search/drugs/auto/?query=" + activeSubstance.replace(" ", "-") + "-" + strength.replace(" ", "-"));
                                        fetchPromise.then(response => response.json()).then(results => {
                                            mPZN2Name[sPZN] = this.cleanMedicationNameResults(results.results[0].name);
                                            mPZN2Name[results.results[0].pzn] = mPZN2Name[sPZN];
                                            delete mPZN2Name[sPZN];
                                        });
                                    } else if (activeSubstance != null && strength == null) {
                                        const fetchPromise = fetch("https://medication.med-united.health/ajax/search/drugs/auto/?query=" + activeSubstance.replace(" ", "-"));
                                        fetchPromise.then(response => response.json()).then(results => {
                                            mPZN2Name[sPZN] = this.cleanMedicationNameResults(results.results[0].name);
                                            mPZN2Name[results.results[0].pzn] = mPZN2Name[sPZN];
                                            delete mPZN2Name[sPZN];
                                        });
                                    }
                                }
                            }
                            return true;
                        })
                    )).then(() => {
                        this.createMedicationStatementWithNames(oEMP, mPZN2Name);
                    });
        },
        cleanMedicationNameResults: function (medicationNameFromSearchProvider) {
            const htmlTagsRegex = /<\/?[^>]+>/g;
            return medicationNameFromSearchProvider.replace(htmlTagsRegex, '');
        },
        createMedicationStatementWithNames: function (oEMP, mPZN2Name) {
            try {
                // <MP v="025" U="02BD2867FB024401A590D59D94E1FFAE" l="de-DE"><P g="Jürgen" f="Wernersen" b="19400324"/><A n="Praxis Dr. Michael Müller" s="Schloßstr. 22" z="10555" c="Berlin" p="030-1234567" e="dr.mueller@kbv-net.de" t="2018-07-01T12:00:00"/><S><M p="230272" m="1" du="1" r="Herz/Blutdruck"/><M p="2223945" m="1" du="1" r="Blutdruck"/><M p="558736" m="20" v="20" du="p" i="Wechseln der Injektionsstellen, unmittelbar vor einer Mahlzeit spritzen" r="Diabetes"/><M p="9900751" v="1" du="1" r="Blutfette"/></S><S t="zu besonderen Zeiten anzuwendende Medikamente"><M p="2239828" t="alle drei Tage 1" du="1" i="auf wechselnde Stellen aufkleben" r="Schmerzen"/></S><S c="418"><M p="2455874" m="1" du="1" r="Stimmung"/></S></MP>
                const oModel = this.getView().getModel();

                // <A n="Praxis Dr. Michael Müller" s="Schloßstr. 22" z="10555" c="Berlin" p="030-1234567" e="dr.mueller@kbv-net.de" t="2018-07-01T12:00:00"/>
                const oPractitionerNode = oEMP.querySelector("A");
                const sBirthdate = oEMP.querySelector("P").getAttribute("b");

                const oPatient = {
                    "name": [
                        {
                            "given": [oEMP.querySelector("P").getAttribute("g")],
                            "family": oEMP.querySelector("P").getAttribute("f"),
                            "use": "official"
                        }
                    ],
                    "birthDate": sBirthdate.substring(0, 4) + "-" + sBirthdate.substring(4, 6) + "-" + sBirthdate.substring(6, 8)
                };
                const oPractitioner = {};
                if (oPractitionerNode) {
                    const sName = oPractitionerNode.getAttribute("n");
                    if (sName && sName.split(/ /).length > 1) {
                        const aNameParts = sName.split(/ /);
                        oPractitioner.name = [
                            {
                                "given": [aNameParts[aNameParts.length - 2]],
                                "family": aNameParts[aNameParts.length - 1],
                                "use": "official"
                            }
                        ];
                        oPractitioner.address = [
                            {
                                "line": [oPractitionerNode.getAttribute("s")],
                                "postalCode": oPractitionerNode.getAttribute("z"),
                                "city": oPractitionerNode.getAttribute("c")
                            }
                        ];
                        oPractitioner.telecom = [];
                        const sPhone = oPractitionerNode.getAttribute("p");
                        if (sPhone) {
                            oPractitioner.telecom.push({
                                "system": "phone",
                                "value": sPhone
                            });
                        }
                        const sEmail = oPractitionerNode.getAttribute("e");
                        if (sEmail) {
                            oPractitioner.telecom.push({
                                "system": "email",
                                "value": sEmail
                            });
                        }
                    }

                    oModel.sendGetRequest("/Practitioner", {
                        "urlParameters": {
                            "family" : oPractitioner.name[0].family,
                            "given" : oPractitioner.name[0].given[0]
                        },
                        "success" : () => {
                            this.searchPatientResourcesFromMedicationPlan(oEMP, oModel, oPractitioner, oPatient, mPZN2Name);
                        },
                        "error" : (e) => {
                            MessageBox.show(this.translate("msgSavedFailed", [e.code, e.message]));
                            console.log(e.stack);
                        }
                    });
                } else {
                    this.searchPatientResourcesFromMedicationPlan(oEMP, oModel, oPractitioner, oPatient, mPZN2Name);
                }
            } catch (e) {
                MessageBox.show(this.translate("msgSavedFailed", [e.code, e.message]));
                console.log(e.stack);
            }
        },
        searchPatientResourcesFromMedicationPlan : function (oEMP, oModel, oPractitioner, oPatient, mPZN2Name) {

            if(oPatient && oPatient.name && oPatient.name.length > 0 && oPatient.name[0].given) {
                oModel.sendGetRequest("/Patient", {
                    "urlParameters": {
                        "family" : oPatient.name[0].family,
                        "given" : oPatient.name[0].given[0]
                    },
                    "success" : () => {
                        this.createResourcesFromMedicationPlan(oEMP, oModel, oPractitioner, oPatient, mPZN2Name);
                    },
                    "error" : (e) => {
                        MessageBox.show(this.translate("msgSavedFailed", [e.code, e.message]));
                        console.log(e.stack);
                    }
                });
            } else {
                this.createResourcesFromMedicationPlan(oEMP, oModel, oPractitioner, oPatient, mPZN2Name);
            }
        },
        createResourcesFromMedicationPlan: function (oEMP, oModel, oPractitioner, oPatient, mPZN2Name) {

            let sPractitionerId = undefined;
            let alreadyExistingPractitionerId = undefined;
            let alreadyExistingPatientId = undefined;

            //Check if practitioner already exists
            const aPractitioner = oModel.getProperty("/Practitioner") || [];
            if(Object.keys(oPractitioner).length > 0) {
                const existingDoctor = Object.values(aPractitioner).filter(a => a.name[0].family === oPractitioner.name[0].family && a.name[0].given[0] === oPractitioner.name[0].given[0])
                if (existingDoctor.length == 0) {
                    sPractitionerId = oModel.create("Practitioner", oPractitioner, "patientDetails");
                } else {
                    alreadyExistingPractitionerId = existingDoctor[0].id;
                }
            }

            if (sPractitionerId) {
                oPatient.generalPractitioner = {
                    "reference": "urn:uuid:" + sPractitionerId
                }
            } else if (alreadyExistingPractitionerId) {
                oPatient.generalPractitioner = {
                    "reference": "Practitioner/" + alreadyExistingPractitionerId
                }
            }
            //check if patient already exists
            let sPatientId = undefined;
            const existingPatient = Object.values(oModel.getProperty("/Patient")).filter(a => a.name[0].family === oPatient.name[0].family && a.name[0].given[0] === oPatient.name[0].given[0])
            if (existingPatient.length == 0) {
                sPatientId = oModel.create("Patient", oPatient, "patientDetails");
            } else {
                alreadyExistingPatientId = existingPatient[0].id;
            }
            this.createMedicationFromMedicationPlan(oEMP, oModel, sPractitionerId, alreadyExistingPractitionerId, sPatientId, alreadyExistingPatientId, mPZN2Name);
            this.save();
        },
        createMedicationFromMedicationPlan : function (oEMP, oModel, sPractitionerId, alreadyExistingPractitionerId, sPatientId, alreadyExistingPatientId, mPZN2Name) {
            const aMedication = Array.from(oEMP.querySelectorAll("M"));
            // https://www.vesta-gematik.de/standard/formhandler/324/gemSpec_Info_AMTS_V1_5_0.pdf
            for (let i = 0; i < aMedication.length; i++) {
                let sPZN = Object.entries(mPZN2Name)[i][0];
                let sDosierschemaMorgens = aMedication[i].getAttribute("m");
                if (!sDosierschemaMorgens) {
                    sDosierschemaMorgens = "0";
                }
                let sDosierschemaMittags = aMedication[i].getAttribute("d");
                if (!sDosierschemaMittags) {
                    sDosierschemaMittags = "0";
                }
                let sDosierschemaAbends = aMedication[i].getAttribute("v");
                if (!sDosierschemaAbends) {
                    sDosierschemaAbends = "0";
                }
                let sDosierschemaNachts = aMedication[i].getAttribute("h");
                if (!sDosierschemaNachts) {
                    sDosierschemaNachts = "0";
                }
                // Dosiereinheit strukturiert
                let sDosage = aMedication[i].getAttribute("du");
                // reason
                let sReason = aMedication[i].getAttribute("r");
                let sAdditionalInformation = aMedication[i].getAttribute("i");

                const medicationName = mPZN2Name[sPZN];

                let oMedicationStatement = undefined;

                if (sPatientId) {
                    oMedicationStatement = {
                        identifier: [{ "value": sPZN }],
                        medicationCodeableConcept: { "text": medicationName },
                        dosage: [
                            { text: sDosierschemaMorgens + "-" + sDosierschemaMittags + "-" + sDosierschemaAbends + "-" + sDosierschemaNachts }
                        ],
                        subject: { "reference": "urn:uuid:" + sPatientId },
                        note: "Grund: " + sReason + " Hinweis: " + sAdditionalInformation
                    };
                } else if (alreadyExistingPatientId) {
                    oMedicationStatement = {
                        identifier: [{ "value": sPZN }],
                        medicationCodeableConcept: { "text": medicationName },
                        dosage: [
                            { text: sDosierschemaMorgens + "-" + sDosierschemaMittags + "-" + sDosierschemaAbends + "-" + sDosierschemaNachts }
                        ],
                        subject: { "reference": "Patient/" + alreadyExistingPatientId },
                        note: "Grund: " + sReason + " Hinweis: " + sAdditionalInformation
                    };
                }

                if (sPractitionerId) {
                    oMedicationStatement.informationSource = {
                        reference: "urn:uuid:" + sPractitionerId
                    };
                } else if (alreadyExistingPractitionerId) {
                    oMedicationStatement.informationSource = {
                        reference: "Practitioner/" + alreadyExistingPractitionerId
                    };
                }
                oModel.create("MedicationStatement", oMedicationStatement, "patientDetails");

            }
        },
        _refreshPatientList: function() {
            const oTable = this.byId("patientTable");
            if (!oTable) {
                console.error("Patient table not found");
                return;
            }

            const oModel = this.getOwnerComponent().getModel();
            if (!oModel) {
                console.error("WebDAV model not found");
                return;
            }
            const oBinding = oTable.getBinding("items");
            if (!oBinding) {
                console.error("No binding found for patient table");
                return;
            }

            oModel.refresh(true);

            oModel.attachEventOnce("requestCompleted", function() {
                oBinding.refresh(true);
            });
        },
        _openMedicationList: function (kvnr) {
            if (!kvnr) {
                console.error("No KVNR provided, cannot open medication list.");
                return;
            }

            const oTable = this.byId("patientTable");
            if (!oTable) {
                console.error("Patient table not found");
                return;
            }

            const oBinding = oTable.getBinding("items");
            if (!oBinding) {
                console.error("No binding found for patient table.");
                return;
            }

            let iLoadedItems = oBinding.getLength();
            // console.log("Total loaded items:", iLoadedItems);
            if (iLoadedItems === 0) {
                console.warn("No patients loaded in the table yet.");
                return;
            }

            const aTableContexts = oBinding.getContexts(0, iLoadedItems);

            if (!Array.isArray(aTableContexts) || aTableContexts.length === 0) {
                console.error("No patients found in the binding contexts.");
                return;
            }

            const aTableItems = aTableContexts.map(ctx => ctx.getObject());
            // console.log(`Currently displayed patients in table:`, aTableItems);

            let sPatientIndex = -1;

            aTableItems.forEach((oPatient, index) => {
                let sDisplayName = null;

                try {
                    // console.log(`Patient ${index}:`, oPatient);
                    let oPropStat = oPatient?.childNodes?.[1];
                    let oProp = oPropStat?.childNodes?.[0];
                    let oDisplayNameNode = Array.from(oProp?.childNodes || []).find(node => node.nodeName === "D:displayname");

                    sDisplayName = oDisplayNameNode ? oDisplayNameNode.textContent.trim() : null;
                } catch (e) {
                    console.warn(`Error extracting display name at index ${index}:`, e);
                }

                // console.log(`Checking index ${index} | Extracted displayname: '${sDisplayName}'`);

                if (sDisplayName === kvnr) {
                    console.log(`Match found at index ${index}`);
                    sPatientIndex = index;
                }
            });

            const sPdfUrl = `/fhir/pdf?x-insurantid=${kvnr}`;

            if (sPatientIndex === -1) {
                console.warn(`Patient with KVNR '${kvnr}' is NOT currently displayed in the table. No navigation.`);
                window.open(sPdfUrl, "_blank");
                MessageToast.show(
                    "eGK-Karte erkannt, Medikamentenliste in neuem Tab geöffnet, da Patient nicht geladen",
                    {
                        duration: 10000,
                        width: "25em"
                    }
                );
                return;
            }

            console.log(`Patient with KVNR '${kvnr}' found at table index:`, sPatientIndex);
            MessageToast.show(
                "Die eGK-Karte dieses Patienten wurde erkannt.",
                {
                    duration: 10000,
                    width: "25em"
                }
            );

            BusyIndicator.show(0);

            if (this.oRouter) {
                const oRouter = this.oRouter;
                const targetRoute = "patient-detail";

                oRouter.getRoute(targetRoute).attachPatternMatched(() => {
                    BusyIndicator.hide();
                });

                oRouter.navTo(targetRoute, {
                    "patient": sPatientIndex,
                    "layout": "ThreeColumnsEndExpanded",
                    "document": encodeURIComponent(sPdfUrl)
                });

            } else {
                console.error("Navigation failed: router is missing.");
                BusyIndicator.hide();
            }
        }
    });
}, true);