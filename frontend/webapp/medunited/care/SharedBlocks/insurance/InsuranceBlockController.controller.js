sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "../../utils/Formatter",
    "sap/fhir/model/r4/FHIRFilter",
    "sap/fhir/model/r4/FHIRFilterType",
    "sap/fhir/model/r4/FHIRFilterOperator"
], function (Controller, Formatter, FHIRFilter, FHIRFilterType, FHIRFilterOperator) {
    "use strict";

    return Controller.extend("medunited.care.SharedBlocks.insurance.InsuranceBlockController", {

        formatter: Formatter,

        onInit: function () {
            this.initializeRouter();
        },

        initializeRouter: function () {
            this.oRouter = sap.ui.core.UIComponent.getRouterFor(this);
            this.oRouter.getRoute("patient-detail").attachPatternMatched(this.onPatientRouteMatched, this);
        },

        onPatientRouteMatched: function (oEvent) {
            var sPatientId = oEvent.getParameter("arguments").patient;
            
        }

    });
});