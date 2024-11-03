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

            // this is a workaround because the block controller gets initialized after the desired route is matched the first time
            var fnInitialFiltering = function (oEvent) {
                var oInsuranceTable = oEvent.getSource();
                var oBindingContext = oInsuranceTable.getBindingContext();
                if (oBindingContext) {
                    // here comes eg. '/Patient/123'
                    var sPatientId = oBindingContext.getPath().split("/")[2];
                    this.filterInsuranceTableToPatient(sPatientId);
                    oInsuranceTable.detachModelContextChange(fnInitialFiltering);
                }
            };
            this.getView().byId("insuranceTable").attachModelContextChange(fnInitialFiltering.bind(this));
        },

        initializeRouter: function () {
            this.oRouter = sap.ui.core.UIComponent.getRouterFor(this);
            this.oRouter.getRoute("patient-detail").attachPatternMatched(this.onPatientRouteMatched, this);
        },

        onPatientRouteMatched: function (oEvent) {
            var sPatientId = oEvent.getParameter("arguments").patient;
            this.filterInsuranceTableToPatient(sPatientId);
        },

        filterInsuranceTableToPatient: function (sPatientId) {
            var aFilters = [];
            aFilters.push(new FHIRFilter({
                path: "patient",
                operator: FHIRFilterOperator.StartsWith,
                value1: "Patient/" + sPatientId,
                valueType: FHIRFilterType.string
            }));
            this.getView().byId("insuranceTable").getBinding("items").filter(aFilters);
        },

        addInsurance: function () {
            var sPatientId = this.byId("insuranceTable").getBindingContext().getPath().split("/")[2];
            var sCoverageId = this.getView().getModel().create("Coverage", {beneficiary: {reference: "Patient/" + sPatientId}}, "patientDetails");
        },

        onInsuranceChange: function (oEvent) {
            var oSelect = oEvent.getSource();
            var sOrganizationId = oSelect.getSelectedKey();
            var sCoverageId = this.getView().getModel().getProperty(oSelect.getBindingContext().getPath() + "/id");
            if (sOrganizationId && sCoverageId) {
                this.getView().getModel().setProperty("/Coverage/" + sCoverageId + "/payor/0/reference", "Organization/" + sOrganizationId);
            }
        },

        onInsuranceCompanyModelChange: function (oEvent) {
            var oSelect = oEvent.getSource();
            var oBindingContext = oSelect.getBindingContext();
            if (oBindingContext) {
                var model = this.getView().getModel();
                if (model) {
                    var sCoverageId = this.getView().getModel().getProperty(oBindingContext.getPath() + "/id");
                    oSelect.setSelectedKey(this.getView().getModel().getProperty("/Coverage/" + sCoverageId + "/payor/0/reference/id"));
                }
            }
        }

    });
});