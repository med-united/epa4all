sap.ui.define([
	"medunited/base/controller/AbstractController",
	"sap/m/MessageBox",
	"sap/m/MessageToast",
	"sap/ui/core/Fragment",
	"sap/ui/model/xml/XMLModel"
], function (Controller, MessageBox, MessageToast, Fragment, XMLModel) {
	"use strict";

	return Controller.extend("medunited.care.controller.patient.viewer.VaccinationEditor", {
		onInit: function() {
			const oModel = new XMLModel();
			oModel.setNameSpace("http://hl7.org/fhir");
			this.getView().setModel(oModel);
		},
		setXML: function(XML) {
			this.getView().getModel().setXML(XML);
			const aEntries = this.getView().getModel()._getObject("/entry");
			if(!aEntries) {
				return;
			}
			for(let i=0;i<aEntries.length;i++) {
				const oEntry = aEntries[i];
				const oResource = oEntry.getElementsByTagName("resource")[0];
				const oChild = oResource.firstElementChild;
				const sTagName = oChild.tagName;
				const oSimpleForm = this.byId("simpleForm"+sTagName);
				if(oSimpleForm) {					
					oSimpleForm.bindElement("/entry/"+i+"/resource/"+sTagName);
				}
			}
		}
	});
}, true);