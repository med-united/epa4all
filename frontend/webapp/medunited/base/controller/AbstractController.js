sap.ui.define([
	"sap/ui/core/mvc/Controller"
], function (Controller) {
	"use strict";

	return Controller.extend("medunited.base.controller.AbstractController", {
		translate: function (sKey, aArgs, bIgnoreKeyFallback) {
			return this.getOwnerComponent().getModel("i18n").getResourceBundle().getText(sKey, aArgs, bIgnoreKeyFallback);
		}
	});
}, true);