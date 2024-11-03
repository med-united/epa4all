sap.ui.define([
    "medunited/base/controller/AbstractController",
    "../../utils/Formatter",
    "sap/fhir/model/r4/FHIRFilter",
    "sap/fhir/model/r4/FHIRFilterType",
    "sap/fhir/model/r4/FHIRFilterOperator",
    "../../search/MedicationSearchProvider",
    "sap/ui/core/Item",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "sap/m/ColumnListItem",
	"sap/ui/model/ChangeReason"
], function (AbstractController, Formatter, FHIRFilter, FHIRFilterType, FHIRFilterOperator, MedicationSearchProvider, Item, MessageToast, MessageBox, ColumnListItem, ChangeReason) {
    "use strict";

    return AbstractController.extend("medunited.care.SharedBlocks.medication.MedicationBlockController", {

        formatter: Formatter,

        onInit: function () {
            this.initializeRouter();

            // this is a workaround because the block controller gets initialized after the desired route is matched the first time
            var fnInitialFiltering = function (oEvent) {
                var oMedicationTable = oEvent.getSource();
                var oBindingContext = oMedicationTable.getBindingContext();
                if (oBindingContext) {
                    // here comes eg. '/Patient/123'
                    var sPatientId = oBindingContext.getPath().split("/")[2];
                    this.filterMedicationTableToPatient(sPatientId);
                    oMedicationTable.detachModelContextChange(fnInitialFiltering);
                }
            };
            this.getView().byId("medicationTable").attachModelContextChange(fnInitialFiltering.bind(this));

            this._oMedicationSearchProvider = new MedicationSearchProvider();
            // npm install -g local-cors-proxy
            // lcp --proxyUrl https://www.apotheken-umschau.de/
            // http://localhost:8010/proxy
            this._oMedicationSearchProvider.setSuggestUrl("https://medication.med-united.health/ajax/search/drugs/auto/?query={searchTerms}");
        },

        initializeRouter: function () {
            this.oRouter = sap.ui.core.UIComponent.getRouterFor(this);
            this.oRouter.getRoute("patient-detail").attachPatternMatched(this.onPatientRouteMatched, this);
        },
        onDataReceivedAsureStructure: function(oEvent) {
            const oData = oEvent.getParameter("data");
			if(!oData.entry || oData.entry.length == 0) {
				return;
			}
            let dLastDouble = 0.0;

			for(let oMedicationStatement of oData.entry) {
				if(!oMedicationStatement.resource.identifier) {
                    oMedicationStatement.resource.identifier = [
                        {"value":undefined}
                    ];
                }
                if(!oMedicationStatement.resource.dosage) {
                    oMedicationStatement.resource.dosage = [
                        {"text":undefined}
                    ];
                }
                if(!oMedicationStatement.resource.extension) {
                    oMedicationStatement.resource.extension = [
                        {"valueString":undefined},
                        {"valueString":undefined},
                        {"valueDecimal":dLastDouble}
                    ];
                } else if(oMedicationStatement.resource.extension.length == 1) {
                    oMedicationStatement.resource.extension.push({"valueString":undefined});
                    oMedicationStatement.resource.extension.push({"valueDecimal":dLastDouble});
                } else if(oMedicationStatement.resource.extension.length == 2) {
                    oMedicationStatement.resource.extension.push({"valueDecimal":dLastDouble});
                }
                if(!oMedicationStatement.resource.note) {
                    oMedicationStatement.resource.note = [
                        {"text":undefined}
                    ];
                }
                if(!oMedicationStatement.resource.informationSource) {
                    oMedicationStatement.resource.informationSource = {"reference":undefined};
                }
                if(!oMedicationStatement.resource.derivedFrom) {
                    oMedicationStatement.resource.derivedFrom = [
                        {"reference":undefined}
                    ];
                }
                dLastDouble = oMedicationStatement.resource.extension[2].valueDecimal+1;
            }
            this.sortMedicationBySecondExtension();
        },
        onPatientRouteMatched: function (oEvent) {
            var sPatientId = oEvent.getParameter("arguments").patient;
            this.filterMedicationTableToPatient(sPatientId);
        },

        filterMedicationTableToPatient: function (sPatientId) {
            var aFilters = [];
            aFilters.push(new FHIRFilter({ path: "subject", operator: FHIRFilterOperator.StartsWith, value1: "Patient/" + sPatientId, valueType: FHIRFilterType.string }));
            const oTable = this.getView().byId("medicationTable");
            const oBinding = oTable.getBinding("items");
            oBinding.filter(aFilters);
        },
        addMedication: function () {
            let sPatientId = this.byId("medicationTable").getBindingContext().getPath().split("/")[2];

            let dLastDouble = 1;
            const oItems = this.byId("medicationTable").getItems();
            if(oItems.length > 0) {
                dLastDouble = parseInt(oItems[0].getBindingContext().getProperty("extension/2/valueDecimal"))+1;
            }
            const oModel = this.getView().getModel();
            // If there is a Practitioner with the same name like the JWT token take it
            oModel.sendGetRequest("/Practitioner", {
                "urlParameters": {
                    "family" : this.getView().getModel("JWT").getProperty("/family_name"),
                    "given" : this.getView().getModel("JWT").getProperty("/given_name")
                },
                "success" : (aPractitioner) => {
                    // If there is only one pharmacy it should be selected
                    oModel.sendGetRequest("/Organization", {
						"success" : (aOrganization) => {
                            const oMedicationStatement = {
                                extension: [{"valueString":"1"},
                                {"valueString":undefined},
                                {"valueDecimal":dLastDouble}],
                                subject: { reference: "Patient/" + sPatientId }
                            };
                            if(aPractitioner.entry && aPractitioner.entry.length == 1) {
                                oMedicationStatement.informationSource = {
                                    reference: "Practitioner/"+aPractitioner.entry[0].resource.id
                                };
                            }
                            if(aOrganization.entry && aOrganization.entry.length == 1) {
                                oMedicationStatement.derivedFrom = [{
                                    reference: "Organization/"+aOrganization.entry[0].resource.id
                                }];
                            }
                            let sMedicationStatementId = oModel.create("MedicationStatement", oMedicationStatement, "patientDetails");
						},
						"error" : (e) => {
							MessageBox.show(this.translate("msgSavedFailed", [e.code, e.message]));
							console.log(e.stack);
						}
					});
                },
                "error" : (e) => {
                    MessageBox.show(this.translate("msgSavedFailed", [e.code, e.message]));
                    console.log(e.stack);
                }
            });

            
        },
        deleteMedication: function () {
            const aResources = this.byId("medicationTable").getSelectedItems().map(oItem => oItem.getBindingContext().getPath());
            const iCount = aResources.length;
            const oModel = this.getView().getModel();
            const me = this;
            oModel.remove(aResources);

            /*oModel.submitChanges(function () {
                MessageToast.show(me.translate("msgCountDeleted", iCount));
                me.byId("medicationTable").getBinding("items").refresh();
            }.bind(this), function (oError) {
                MessageBox.show(me.translate("msgDeleteFailed", [oError.statusCode, oError.statusText]));
            });*/
        },
        onSuggestMedicationName: function (oEvent) {
            const sTerm = oEvent.getParameter("suggestValue");
            const oController = this.getView().getController();

            this._oMedicationSearchProvider.suggest(sTerm, function (sValue, aSuggestions) {
                this.destroySuggestionItems();

                for (const suggestion of aSuggestions) {
                    if (suggestion.packaging.standardPackage != null) {
                        this.addSuggestionItem(new Item({
                            text: oController.cleanMedicationNameResults(suggestion.name) + " - " + suggestion.packaging.standardPackage + " (" + suggestion.pzn + ")"
                        }));
                    } else {
                        this.addSuggestionItem(new Item({
                            text: oController.cleanMedicationNameResults(suggestion.name) + " (" + suggestion.pzn + ")"
                        }));
                    }
                }
            }.bind(oEvent.getSource()));
        },
        cleanMedicationNameResults: function (medicationNameFromSearchProvider) {
            const htmlTagsRegex = /<\/?[^>]+>/g;
            return medicationNameFromSearchProvider.replace(htmlTagsRegex, '');
        },
        onSuggestionMedicationNameSelected: function (oEvent) {
            const oItem = oEvent.getParameter("selectedItem");
            const itemSelected = oItem.getText();
            const pznRegex = new RegExp(/\(\d*\)$/, "g");
            const allNumbersBetweenParenthesisMatches = itemSelected.match(pznRegex);
            const lastMatch = allNumbersBetweenParenthesisMatches.length - 1;

            const medicationPZN = allNumbersBetweenParenthesisMatches[lastMatch].replace(/\(/, '').replace(/\)/, '');
            let medicationName = itemSelected.replace(" \(" + medicationPZN + "\)", "");

            let source = oEvent.getSource();
            if (this.medicationNameHasPackageSize(medicationName)) {
                const medicationPackageSize = this.getPackageSizeFromMedicationName(medicationName);
                medicationName = medicationName.replace(" - " + medicationPackageSize, "");
                source.getModel().setProperty(source.getBindingContext().getPath("extension/1/valueString"), medicationPackageSize);
            } else {
                source.getModel().setProperty(source.getBindingContext().getPath("extension/1/valueString"), "");
            }
            source.getModel().setProperty(source.getBindingContext().getPath("medicationCodeableConcept/text"), medicationName);
            source.getModel().setProperty(source.getBindingContext().getPath("identifier/0/value"), medicationPZN);
        },
        onSuggestPZN: function (oEvent) {
            const sTerm = oEvent.getParameter("suggestValue");
            let leadingZeros;
            if (sTerm.match(/^0+/) != null) {
                leadingZeros = sTerm.match(/^0+/)[0];
            }
            else {
                leadingZeros = "";
            }
            const oController = this.getView().getController();
            this._oMedicationSearchProvider.suggest(sTerm, function (sValue, aSuggestions) {
                this.destroySuggestionItems();

                for (const suggestion of aSuggestions) {
                    console.log(suggestion)
                    console.log(suggestion.packaging.standardPackage)
                    if (suggestion.packaging.standardPackage != null) {
                        this.addSuggestionItem(new Item({
                            text: leadingZeros + suggestion.pzn + " (" + oController.cleanMedicationNameResults(suggestion.name) + " - " + suggestion.packaging.standardPackage + ")"
                        }));
                    } else {
                        this.addSuggestionItem(new Item({
                            text: leadingZeros + suggestion.pzn + " (" + oController.cleanMedicationNameResults(suggestion.name) + ")"
                        }));
                    }
                }
            }.bind(oEvent.getSource()));
        },
        onSuggestionPZNSelected: function (oEvent) {
            const oItem = oEvent.getParameter("selectedItem");
            const itemSelected = oItem.getText();
            const pznRegex = new RegExp(/\d*\s/);
            const pznMatch = itemSelected.match(pznRegex)[0];

            const medicationPZN = pznMatch.trim();
            let medicationName = itemSelected.replace(pznRegex, "").slice(1, -1);

            let source = oEvent.getSource();
            if (this.medicationNameHasPackageSize(medicationName)) {
                const medicationPackageSize = this.getPackageSizeFromMedicationName(medicationName);
                medicationName = medicationName.replace(" - " + medicationPackageSize, "");
                source.getModel().setProperty(source.getBindingContext().getPath("extension/1/valueString"), medicationPackageSize);
            } else {
                source.getModel().setProperty(source.getBindingContext().getPath("extension/1/valueString"), "");
            }
            source.getModel().setProperty(source.getBindingContext().getPath("medicationCodeableConcept/text"), medicationName);
            source.getModel().setProperty(source.getBindingContext().getPath("identifier/0/value"), medicationPZN);
        },
        medicationNameHasPackageSize: function (medicationName) {
            const packageSizeRegex = new RegExp(/ - N\d$/);
            return medicationName.match(packageSizeRegex) != null;
        },
        getPackageSizeFromMedicationName: function (medicationNameWithPackageSize) {
            const packageSizeRegex = new RegExp(/ - N\d$/);
            const packageSizeMatch = medicationNameWithPackageSize.match(packageSizeRegex)[0];
            return packageSizeMatch.replace(" - ", "");
        },
        onDropSelectedMedicationTable: function(oEvent) {
            const oDraggedItem = oEvent.getParameter("draggedControl");
			const oDraggedItemContext = oDraggedItem.getBindingContext();
			if (!oDraggedItemContext) {
				return;
			}

			const oRanking = {
                Initial: 0,
                Default: 1024,
                Before: function(iRank) {
                    return iRank + 1024;
                },
                Between: function(iRank1, iRank2) {
                    // limited to 53 rows
                    return (iRank1 + iRank2) / 2;
                },
                After: function(iRank) {
                    return iRank / 2;
                }
            };
			let iNewRank = oRanking.Default;
			const oDroppedItem = oEvent.getParameter("droppedControl");

			if (oDroppedItem instanceof ColumnListItem) {
				// get the dropped row data
				const sDropPosition = oEvent.getParameter("dropPosition");
				const oDroppedItemContext = oDroppedItem.getBindingContext();
				const iDroppedItemRank = oDroppedItemContext.getProperty("extension/2/valueDecimal");
				const oDroppedTable = oDroppedItem.getParent();
				const iDroppedItemIndex = oDroppedTable.indexOfItem(oDroppedItem);

				// find the new index of the dragged row depending on the drop position
				const iNewItemIndex = iDroppedItemIndex + (sDropPosition === "After" ? 1 : -1);
				const oNewItem = oDroppedTable.getItems()[iNewItemIndex];
				if (!oNewItem) {
					// dropped before the first row or after the last row
					iNewRank = oRanking[sDropPosition](iDroppedItemRank);
				} else {
					// dropped between first and the last row
					const oNewItemContext = oNewItem.getBindingContext();
					iNewRank = oRanking.Between(iDroppedItemRank, oNewItemContext.getProperty("extension/2/valueDecimal"));
				}
			}

            const oModel = this.getView().getModel();
			oModel.setProperty("extension/2/valueDecimal", iNewRank, oDraggedItemContext);
            
            this.sortMedicationBySecondExtension();
            
        },
        sortMedicationBySecondExtension : function() {
            // set the rank property and update the model to refresh the bindings
            const oMedicationTable = this.byId("medicationTable");
            const oBinding = oMedicationTable.getBinding("items");
            const aKeys = oMedicationTable.getItems().sort(function compareFn(a, b) {
                const aValueDecimal = a.getBindingContext().getProperty("extension/2/valueDecimal");
                const bValueDecimal = b.getBindingContext().getProperty("extension/2/valueDecimal");
                return bValueDecimal - aValueDecimal;
            }).map((oItem) => oItem.getBindingContext().getPath().substr(1));
    
            oBinding.aKeys = aKeys;
            oBinding.aKeysServerState = aKeys;
            oBinding._fireChange({reason: ChangeReason.Change});

        }
    });
});