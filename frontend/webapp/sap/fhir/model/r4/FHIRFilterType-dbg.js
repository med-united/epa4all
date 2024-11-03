/*!
 * SAP SE
 * (c) Copyright 2009-2022 SAP SE or an SAP affiliate company.
 * Licensed under the Apache License, Version 2.0 - see LICENSE.txt.
 */

// Provides class sap.fhir.model.r4.FHIRFilterType
sap.ui.define(function() {

	"use strict";

	/**
	 * FHIRFilter value type
	 *
	 * @enum {string}
	 * @public
	 * @alias sap.fhir.model.r4.FHIRFilterType
	 */
	var FHIRFilterType = {
		/**
		 * @public
		 */
		string : "string",
		/**
		 * @public
		 */
		date: "date",
		/**
		 * @public
		 */
		number: "number"
	};

	return FHIRFilterType;
});