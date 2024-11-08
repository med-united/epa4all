sap.ui.define([
    "medunited/base/controller/AbstractController",
    "../../utils/Formatter",
    "sap/fhir/model/r4/FHIRFilter",
    "sap/fhir/model/r4/FHIRFilterType",
    "sap/fhir/model/r4/FHIRFilterOperator",
    "sap/ui/core/Item",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "sap/m/ColumnListItem",
	"sap/ui/model/ChangeReason"
], function (AbstractController, Formatter, FHIRFilter, FHIRFilterType, FHIRFilterOperator, Item, MessageToast, MessageBox, ColumnListItem, ChangeReason) {
    "use strict";

    return AbstractController.extend("medunited.care.SharedBlocks.document.DocumentBlockController", {

        formatter: Formatter,

        onInit: function () {
            this.initializeRouter();

        },

        initializeRouter: function () {
            this.oRouter = sap.ui.core.UIComponent.getRouterFor(this);
            this.oRouter.getRoute("patient-detail").attachPatternMatched(this.onPatientRouteMatched, this);
        },
        onPatientRouteMatched: function (oEvent) {
            
        },
		addDocument: function () {
                        
        },
        deleteDocument: function () {
            
        },
        onSuggestDocumentName: function (oEvent) {
            
        }
    });
});